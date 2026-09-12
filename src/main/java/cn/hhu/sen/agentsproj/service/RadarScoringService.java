package cn.hhu.sen.agentsproj.service;

import cn.hhu.sen.agentsproj.model.RadarEvaluation;
import cn.hhu.sen.agentsproj.model.RepositorySnapshotData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class RadarScoringService {

    private static final Map<String, Integer> DEFAULT_CRITERIA = Map.of(
            "maintenance", 30,
            "community", 25,
            "productionFit", 20,
            "documentation", 15,
            "license", 10
    );

    private final ObjectMapper objectMapper;

    public RadarScoringService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Integer> defaultCriteria() {
        return new LinkedHashMap<>(DEFAULT_CRITERIA);
    }

    public Map<String, Integer> normalizeCriteria(Map<String, Integer> criteria) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        Map<String, Integer> source = criteria == null || criteria.isEmpty()
                ? DEFAULT_CRITERIA : criteria;
        source.forEach((name, weight) -> {
            if (name != null && !name.isBlank() && weight != null && weight > 0) {
                normalized.put(name.trim(), weight);
            }
        });
        return normalized.isEmpty() ? defaultCriteria() : normalized;
    }

    public String criteriaToJson(Map<String, Integer> criteria) {
        try {
            return objectMapper.writeValueAsString(normalizeCriteria(criteria));
        } catch (Exception e) {
            throw new IllegalStateException("评估模板序列化失败", e);
        }
    }

    public Map<String, Integer> criteriaFromJson(String json) {
        try {
            Map<String, Integer> criteria = objectMapper.readValue(json,
                    new TypeReference<Map<String, Integer>>() {});
            return normalizeCriteria(criteria);
        } catch (Exception e) {
            throw new IllegalStateException("评估模板解析失败", e);
        }
    }

    public RadarEvaluation evaluate(RepositorySnapshotData data, String criteriaJson) {
        Map<String, Integer> criteria = criteriaFromJson(criteriaJson);
        Map<String, Integer> criterionScores = new LinkedHashMap<>();
        int totalWeight = criteria.values().stream().mapToInt(Integer::intValue).sum();
        int weightedScore = 0;

        for (Map.Entry<String, Integer> criterion : criteria.entrySet()) {
            int score = scoreFor(criterion.getKey(), data.getMetrics());
            criterionScores.put(criterion.getKey(), score);
            weightedScore += score * criterion.getValue();
        }

        int score = totalWeight == 0 ? 0 : Math.round((float) weightedScore / totalWeight);
        String decision = score >= 80 ? "RECOMMEND" : score >= 60 ? "PILOT" : "WATCH";
        Map<String, Object> metrics = data.getMetrics();
        String summary = "总分 " + score + "/100，建议 " + decision
                + "；最近推送 " + value(metrics, "lastPushedAt")
                + "，Stars " + value(metrics, "stars")
                + "，License " + (value(metrics, "license").isBlank() ? "未知" : value(metrics, "license"));
        return new RadarEvaluation(score, decision, summary, criterionScores);
    }

    private int scoreFor(String criterion, Map<String, Object> metrics) {
        String key = criterion.toLowerCase(Locale.ROOT).replace("_", "");
        return switch (key) {
            case "maintenance", "activity", "活跃度", "维护度" -> maintenanceScore(value(metrics, "lastPushedAt"),
                    Boolean.parseBoolean(value(metrics, "archived")));
            case "community", "社区" -> communityScore(number(metrics, "stars"), number(metrics, "forks"),
                    number(metrics, "contributors"));
            case "productionfit", "maturity", "production", "生产适配" -> productionScore(metrics);
            case "documentation", "docs", "文档" -> documentationScore(metrics);
            case "license", "许可证", "合规" -> value(metrics, "license").isBlank() ? 20 : 100;
            default -> 50;
        };
    }

    private int maintenanceScore(String pushedAt, boolean archived) {
        if (archived) {
            return 10;
        }
        if (pushedAt == null || pushedAt.isBlank()) {
            return 20;
        }
        try {
            LocalDateTime time = parseTime(pushedAt);
            long days = ChronoUnit.DAYS.between(time, LocalDateTime.now(ZoneOffset.UTC));
            if (days <= 30) return 100;
            if (days <= 90) return 85;
            if (days <= 180) return 65;
            if (days <= 365) return 40;
            return 20;
        } catch (Exception ignored) {
            return 40;
        }
    }

    private int communityScore(long stars, long forks, long contributors) {
        double starScore = Math.min(100, Math.log10(stars + 1) / 5.0 * 100);
        double forkScore = Math.min(100, Math.log10(forks + 1) / 4.0 * 100);
        double contributorScore = Math.min(100, Math.log10(contributors + 1) / 3.0 * 100);
        return (int) Math.round(starScore * 0.5 + forkScore * 0.2 + contributorScore * 0.3);
    }

    private int productionScore(Map<String, Object> metrics) {
        int score = 0;
        if (!value(metrics, "language").isBlank()) score += 20;
        if (Boolean.parseBoolean(value(metrics, "hasBuildFile"))) score += 30;
        if (Boolean.parseBoolean(value(metrics, "hasContainerFile"))) score += 20;
        if (!value(metrics, "license").isBlank()) score += 20;
        if (!Boolean.parseBoolean(value(metrics, "archived"))) score += 10;
        return score;
    }

    private int documentationScore(Map<String, Object> metrics) {
        long chars = number(metrics, "readmeChars");
        if (chars >= 5000) return 100;
        if (chars >= 2000) return 85;
        if (chars >= 500) return 65;
        if (chars > 0) return 40;
        return 10;
    }

    private LocalDateTime parseTime(String value) {
        if (value.endsWith("Z") || value.contains("+")) {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        }
        return LocalDateTime.parse(value);
    }

    private long number(Map<String, Object> metrics, String key) {
        Object value = metrics.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String value(Map<String, Object> metrics, String key) {
        Object value = metrics.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
