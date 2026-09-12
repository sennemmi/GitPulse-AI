package cn.hhu.sen.agentsproj.service;

import cn.hhu.sen.agentsproj.entity.RadarSnapshot;
import cn.hhu.sen.agentsproj.entity.RadarWatch;
import cn.hhu.sen.agentsproj.model.RadarCompareView;
import cn.hhu.sen.agentsproj.model.RadarEvidence;
import cn.hhu.sen.agentsproj.model.RadarEvaluation;
import cn.hhu.sen.agentsproj.model.RadarSnapshotView;
import cn.hhu.sen.agentsproj.model.RadarWatchView;
import cn.hhu.sen.agentsproj.model.RepositorySnapshotData;
import cn.hhu.sen.agentsproj.repository.RadarSnapshotRepository;
import cn.hhu.sen.agentsproj.repository.RadarWatchRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class RadarService {

    private final RadarWatchRepository watchRepository;
    private final RadarSnapshotRepository snapshotRepository;
    private final RadarDataService dataService;
    private final RadarScoringService scoringService;
    private final ObjectMapper objectMapper;

    public RadarService(RadarWatchRepository watchRepository,
                        RadarSnapshotRepository snapshotRepository,
                        RadarDataService dataService,
                        RadarScoringService scoringService,
                        ObjectMapper objectMapper) {
        this.watchRepository = watchRepository;
        this.snapshotRepository = snapshotRepository;
        this.dataService = dataService;
        this.scoringService = scoringService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RadarWatchView createWatch(String repoName,
                                      String displayName,
                                      Map<String, Integer> criteria,
                                      Boolean enabled) {
        String normalized = dataService.normalizeRepoName(repoName);
        if (watchRepository.findByRepoName(normalized).isPresent()) {
            throw new IllegalArgumentException("该仓库已经在 Watchlist 中: " + normalized);
        }

        RadarWatch watch = new RadarWatch();
        watch.setRepoName(normalized);
        watch.setDisplayName(displayName == null || displayName.isBlank() ? normalized : displayName.trim());
        watch.setTemplateJson(scoringService.criteriaToJson(criteria));
        watch.setEnabled(enabled == null || enabled);
        return toWatchView(watchRepository.save(watch));
    }

    @Transactional(readOnly = true)
    public List<RadarWatchView> listWatches() {
        return watchRepository.findAll().stream().map(this::toWatchView).toList();
    }

    @Transactional
    public RadarWatchView updateWatch(Long watchId,
                                      String displayName,
                                      Map<String, Integer> criteria,
                                      Boolean enabled) {
        RadarWatch watch = requireWatch(watchId);
        if (displayName != null && !displayName.isBlank()) {
            watch.setDisplayName(displayName.trim());
        }
        if (criteria != null) {
            watch.setTemplateJson(scoringService.criteriaToJson(criteria));
        }
        if (enabled != null) {
            watch.setEnabled(enabled);
        }
        return toWatchView(watchRepository.save(watch));
    }

    @Transactional
    public void deleteWatch(Long watchId) {
        RadarWatch watch = requireWatch(watchId);
        snapshotRepository.deleteByWatchId(watch.getId());
        watchRepository.delete(watch);
    }

    @Transactional
    public RadarSnapshotView scan(Long watchId) {
        RadarWatch watch = requireWatch(watchId);
        RadarSnapshot previous = snapshotRepository.findTopByWatchIdOrderByCreatedTimeDesc(watchId).orElse(null);
        RepositorySnapshotData data = dataService.collect(watch.getRepoName());
        RadarEvaluation evaluation = scoringService.evaluate(data, watch.getTemplateJson());

        RadarSnapshot snapshot = new RadarSnapshot();
        snapshot.setWatchId(watch.getId());
        snapshot.setRepoName(watch.getRepoName());
        snapshot.setScore(evaluation.getScore());
        snapshot.setDecision(evaluation.getDecision());
        snapshot.setSummary(evaluation.getSummary());
        snapshot.setSourceRevision(data.getSourceRevision());
        snapshot.setMetricsJson(writeJson(data.getMetrics()));
        snapshot.setCriteriaScoresJson(writeJson(evaluation.getCriteriaScores()));
        snapshot.setEvidenceJson(writeJson(data.getEvidence()));
        RadarSnapshot saved = snapshotRepository.save(snapshot);
        log.info("[Radar] 快照完成 | watchId: {} | repo: {} | score: {} | decision: {}",
                watchId, watch.getRepoName(), evaluation.getScore(), evaluation.getDecision());
        return toSnapshotView(saved, previous);
    }

    @Transactional(readOnly = true)
    public List<RadarSnapshotView> listSnapshots(Long watchId) {
        requireWatch(watchId);
        List<RadarSnapshot> snapshots = snapshotRepository.findByWatchIdOrderByCreatedTimeDesc(watchId);
        List<RadarSnapshotView> views = new ArrayList<>();
        for (int i = 0; i < snapshots.size(); i++) {
            RadarSnapshot previous = i + 1 < snapshots.size() ? snapshots.get(i + 1) : null;
            views.add(toSnapshotView(snapshots.get(i), previous));
        }
        return views;
    }

    @Transactional(readOnly = true)
    public RadarCompareView compare(List<Long> watchIds) {
        if (watchIds == null || watchIds.size() < 2) {
            throw new IllegalArgumentException("至少选择两个 Watchlist 项目进行比较");
        }

        List<RadarSnapshotView> snapshots = new ArrayList<>();
        for (Long watchId : watchIds) {
            requireWatch(watchId);
            RadarSnapshot snapshot = snapshotRepository.findTopByWatchIdOrderByCreatedTimeDesc(watchId)
                    .orElseThrow(() -> new IllegalArgumentException("项目尚未扫描，无法比较: " + watchId));
            snapshots.add(toSnapshotView(snapshot, findPrevious(snapshot).orElse(null)));
        }
        snapshots.sort(Comparator.comparing(RadarSnapshotView::score,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return new RadarCompareView(snapshots, Instant.now());
    }

    @Transactional
    public List<RadarSnapshotView> scanEnabledWatches() {
        List<RadarSnapshotView> results = new ArrayList<>();
        for (RadarWatch watch : watchRepository.findByEnabledTrueOrderByUpdatedTimeDesc()) {
            try {
                results.add(scan(watch.getId()));
            } catch (Exception e) {
                log.error("[Radar] 定时扫描失败 | watchId: {} | repo: {}", watch.getId(), watch.getRepoName(), e);
            }
        }
        return results;
    }

    private RadarWatch requireWatch(Long watchId) {
        if (watchId == null) {
            throw new IllegalArgumentException("watchId 不能为空");
        }
        return watchRepository.findById(watchId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Watchlist 项目不存在: " + watchId));
    }

    private RadarWatchView toWatchView(RadarWatch watch) {
        RadarSnapshotView latest = snapshotRepository.findTopByWatchIdOrderByCreatedTimeDesc(watch.getId())
                .map(snapshot -> toSnapshotView(snapshot, findPrevious(snapshot).orElse(null))).orElse(null);
        return new RadarWatchView(
                watch.getId(),
                watch.getRepoName(),
                watch.getDisplayName(),
                scoringService.criteriaFromJson(watch.getTemplateJson()),
                watch.isEnabled(),
                watch.getCreatedTime(),
                watch.getUpdatedTime(),
                latest);
    }

    private RadarSnapshotView toSnapshotView(RadarSnapshot snapshot, RadarSnapshot previous) {
        return new RadarSnapshotView(
                snapshot.getId(),
                snapshot.getWatchId(),
                snapshot.getRepoName(),
                snapshot.getScore(),
                snapshot.getDecision(),
                snapshot.getSummary(),
                snapshot.getSourceRevision(),
                readMap(snapshot.getMetricsJson()),
                readIntMap(snapshot.getCriteriaScoresJson()),
                readEvidence(snapshot.getEvidenceJson()),
                calculateChanges(snapshot, previous),
                snapshot.getCreatedTime());
    }

    private java.util.Optional<RadarSnapshot> findPrevious(RadarSnapshot snapshot) {
        return snapshotRepository.findFirstByWatchIdAndIdNotOrderByCreatedTimeDesc(
                snapshot.getWatchId(), snapshot.getId());
    }

    private Map<String, String> calculateChanges(RadarSnapshot current, RadarSnapshot previous) {
        if (previous == null) {
            return Map.of();
        }
        Map<String, Object> currentMetrics = readMap(current.getMetricsJson());
        Map<String, Object> previousMetrics = readMap(previous.getMetricsJson());
        Map<String, String> changes = new LinkedHashMap<>();
        List<String> trackedMetrics = List.of(
                "stars", "forks", "openIssues", "contributors", "lastPushedAt", "license", "archived");
        for (String metric : trackedMetrics) {
            String currentValue = String.valueOf(currentMetrics.getOrDefault(metric, ""));
            String previousValue = String.valueOf(previousMetrics.getOrDefault(metric, ""));
            if (!Objects.equals(currentValue, previousValue)) {
                changes.put(metric, previousValue + " -> " + currentValue);
            }
        }
        return changes;
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("快照指标解析失败", e);
        }
    }

    private Map<String, Integer> readIntMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("快照评分解析失败", e);
        }
    }

    private List<RadarEvidence> readEvidence(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<RadarEvidence>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("快照证据解析失败", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("快照序列化失败", e);
        }
    }
}
