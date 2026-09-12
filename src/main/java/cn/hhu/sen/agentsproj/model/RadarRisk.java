package cn.hhu.sen.agentsproj.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A deterministic, human-readable risk signal derived from collected facts.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RadarRisk {

    private String code;
    private String severity;
    private String title;
    private String detail;
}
