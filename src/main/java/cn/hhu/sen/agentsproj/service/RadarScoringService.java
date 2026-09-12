package cn.hhu.sen.agentsproj.service;

import cn.hhu.sen.agentsproj.model.RadarEvaluation;
import cn.hhu.sen.agentsproj.model.RadarRisk;
import cn.hhu.sen.agentsproj.model.RepositorySnapshotData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
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
        List<RadarRisk> risks = identifyRisks(metrics);
        String summary = "总分 " + score + "/100，建议 " + decision
                + "；最近推送 " + value(metrics, "lastPushedAt")
                + "，Stars " + value(metrics, "stars")
                + "，License " + (value(metrics, "license").isBlank() ? "未知" : value(metrics, "license"))
                + "；风险 " + risks.size() + " 项";
        return new RadarEvaluation(score, decision, summary, criterionScores, risks);
    }

    private List<RadarRisk> identifyRisks(Map<String, Object> metrics) {
        List<RadarRisk> risks = new ArrayList<>();
        boolean archived = Boolean.parseBoolean(value(metrics, "archived"));
        String license = value(metrics, "license");
        String pushedAt = value(metrics, "lastPushedAt");
        long contributors = number(metrics, "contributors");
        long openIssues = number(metrics, "openIssues");
        long readmeChars = number(metrics, "readmeChars");

        if (archived) {
            risks.add(new RadarRisk("ARCHIVED", "HIGH", "仓库已归档", "仓库已被 GitHub 标记为 archived，不适合作为新的长期依赖。"));
        }
        if (license.isBlank()) {
            risks.add(new RadarRisk("NO_LICENSE", "HIGH", "缺少许可证", "无法确认再分发、商用和二次修改边界，需要人工确认。"));
        }
        long daysSincePush = daysSince(pushedAt);
        if (!archived && daysSincePush > 365) {
            risks.add(new RadarRisk("STALE_ACTIVITY", "HIGH", "长期未维护", "最近一次推送已经超过一年，需确认项目是否仍然活跃。"));
        } else if (!archived && daysSincePush > 180) {
            risks.add(new RadarRisk("STALE_ACTIVITY", "MEDIUM", "维护活跃度下降", "最近一次推送已经超过六个月，建议检查维护者响应情况。"));
        }
        if (readmeChars < 500) {
            risks.add(new RadarRisk("LOW_DOCUMENTATION", "MEDIUM", "文档信号较弱", "README 内容较少，接入成本和使用边界需要额外核实。"));
        }
        if (!Boolean.parseBoolean(value(metrics, "hasBuildFile"))) {
            risks.add(new RadarRisk("NO_BUILD_SIGNAL", "MEDIUM", "缺少构建入口", "根目录未发现常见构建文件，自动化构建和复现成本可能较高。"));
        }
        if (contributors >= 0 && contributors <= 2) {
            risks.add(new RadarRisk("LOW_BUS_FACTOR", "MEDIUM", "维护者集中", "贡献者数量很少，项目可能依赖单一维护者。"));
        }
        if (openIssues >= 500) {
            risks.add(new RadarRisk("ISSUE_BACKLOG", "LOW", "开放问题较多", "当前开放 issue 数量较高，采用前需要评估未解决问题对业务的影响。"));
        }
        if (contributors < 0) {
            risks.add(new RadarRisk("INCOMPLETE_DATA", "LOW", "贡献者数据不完整", "GitHub 未返回完整贡献者数量，本次评分使用了保守处理。"));
        }
        return risks;
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

    private long daysSince(String value) {
        if (value == null || value.isBlank()) {
            return Long.MAX_VALUE;
        }
        try {
            return Math.max(0, ChronoUnit.DAYS.between(parseTime(value), LocalDateTime.now(ZoneOffset.UTC)));
        } catch (Exception ignored) {
            return -1;
        }
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
