package cn.hhu.sen.agentsproj.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Normalized repository facts collected from GitHub or the demo provider.
 * This object deliberately contains facts only; scoring is handled separately.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepositorySnapshotData {

    private String repoName;
    private String sourceRevision;
    private Map<String, Object> metrics;
    private List<RadarEvidence> evidence;
}
