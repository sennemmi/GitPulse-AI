package cn.hhu.sen.agentsproj.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.List;

/**
 * Explainable evaluation produced from normalized repository facts.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RadarEvaluation {

    private Integer score;
    private String decision;
    private String summary;
    private Map<String, Integer> criteriaScores;
    private List<RadarRisk> risks;
}
