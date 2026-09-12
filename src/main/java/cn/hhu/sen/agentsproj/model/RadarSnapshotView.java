package cn.hhu.sen.agentsproj.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record RadarSnapshotView(
        Long id,
        Long watchId,
        String repoName,
        Integer score,
        String decision,
        String summary,
        String sourceRevision,
        Map<String, Object> metrics,
        Map<String, Integer> criteriaScores,
        List<RadarEvidence> evidence,
        List<RadarRisk> risks,
        Map<String, String> changes,
        String freshness,
        LocalDateTime createdTime) {
}
