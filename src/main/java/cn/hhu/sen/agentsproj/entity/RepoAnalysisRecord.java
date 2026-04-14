package cn.hhu.sen.agentsproj.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "repo_analysis_record")
public class RepoAnalysisRecord {
    @Id
    private String repoName;

    @Column(columnDefinition = "TEXT")
    private String analysisJson;
}
