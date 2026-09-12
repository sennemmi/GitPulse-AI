package cn.hhu.sen.agentsproj.model;

import java.time.Instant;
import java.util.List;

public record RadarCompareView(List<RadarSnapshotView> snapshots, Instant generatedAt) {
}
