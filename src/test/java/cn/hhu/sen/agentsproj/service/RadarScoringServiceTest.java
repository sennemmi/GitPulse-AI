package cn.hhu.sen.agentsproj.service;

import cn.hhu.sen.agentsproj.model.RadarEvaluation;
import cn.hhu.sen.agentsproj.model.RepositorySnapshotData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RadarScoringServiceTest {

    private final RadarScoringService scoringService = new RadarScoringService(new ObjectMapper());

    @Test
    void emitsActionableRiskFlagsForWeakRepositorySignals() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("language", "");
        metrics.put("stars", 12);
        metrics.put("forks", 1);
        metrics.put("openIssues", 900);
        metrics.put("contributors", 1);
        metrics.put("license", "");
        metrics.put("archived", true);
        metrics.put("lastPushedAt", OffsetDateTime.now(ZoneOffset.UTC).minusDays(400).toString());
        metrics.put("readmeChars", 120);
        metrics.put("hasBuildFile", false);
        metrics.put("hasContainerFile", false);

        RepositorySnapshotData data = new RepositorySnapshotData(
                "owner/repository", "abc123", metrics, List.of());

        RadarEvaluation evaluation = scoringService.evaluate(data, scoringService.criteriaToJson(null));

        assertThat(evaluation.getRisks())
                .extracting(risk -> risk.getCode())
                .contains("ARCHIVED", "NO_LICENSE", "LOW_DOCUMENTATION", "NO_BUILD_SIGNAL",
                        "LOW_BUS_FACTOR", "ISSUE_BACKLOG");
        assertThat(evaluation.getSummary()).contains("风险");
    }

    @Test
    void doesNotFlagHealthyRecentRepositoryAsUnlicensedOrStale() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("language", "Java");
        metrics.put("stars", 10000);
        metrics.put("forks", 1000);
        metrics.put("openIssues", 20);
        metrics.put("contributors", 25);
        metrics.put("license", "Apache-2.0");
        metrics.put("archived", false);
        metrics.put("lastPushedAt", OffsetDateTime.now(ZoneOffset.UTC).minusDays(5).toString());
        metrics.put("readmeChars", 4000);
        metrics.put("hasBuildFile", true);
        metrics.put("hasContainerFile", true);

        RepositorySnapshotData data = new RepositorySnapshotData(
                "owner/repository", "abc123", metrics, List.of());

        RadarEvaluation evaluation = scoringService.evaluate(data, scoringService.criteriaToJson(null));

        assertThat(evaluation.getRisks())
                .extracting(risk -> risk.getCode())
                .doesNotContain("NO_LICENSE", "STALE_ACTIVITY");
    }
}
