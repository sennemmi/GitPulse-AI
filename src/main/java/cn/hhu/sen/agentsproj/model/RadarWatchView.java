package cn.hhu.sen.agentsproj.model;

import java.time.LocalDateTime;
import java.util.Map;

public record RadarWatchView(
        Long id,
        String repoName,
        String displayName,
        Map<String, Integer> criteria,
        boolean enabled,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        RadarSnapshotView latestSnapshot) {
}
