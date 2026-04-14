package cn.hhu.sen.agentsproj.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TechReport {
    private String repoName;
    private String summary;
    private String maturity;
    private List<String> techStack;
    private String coreValue;
    private List<String> riskPoints;
    private String adoptionAdvice;
    private String competitorComparison;
    private Integer score;
    private LocalDateTime generatedAt;
}
