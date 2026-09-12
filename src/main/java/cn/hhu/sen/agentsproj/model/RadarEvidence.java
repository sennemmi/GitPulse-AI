package cn.hhu.sen.agentsproj.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single evidence item used to explain a radar score.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RadarEvidence {

    private String metric;
    private String value;
    private String source;
    private String collectedAt;
    private String note;
}
