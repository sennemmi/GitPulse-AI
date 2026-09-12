package cn.hhu.sen.agentsproj.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "radar_snapshot")
public class RadarSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "watch_id", nullable = false)
    private Long watchId;

    @Column(name = "repo_name", nullable = false, length = 255)
    private String repoName;

    private Integer score;

    @Column(length = 32)
    private String decision;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "source_revision", length = 128)
    private String sourceRevision;

    @Column(name = "metrics_json", columnDefinition = "TEXT", nullable = false)
    private String metricsJson;

    @Column(name = "criteria_scores_json", columnDefinition = "TEXT", nullable = false)
    private String criteriaScoresJson;

    @Column(name = "evidence_json", columnDefinition = "TEXT", nullable = false)
    private String evidenceJson;

    @Column(name = "created_time")
    private LocalDateTime createdTime;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        createdTime = LocalDateTime.now();
    }
}
