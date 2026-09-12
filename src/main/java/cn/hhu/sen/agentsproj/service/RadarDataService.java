package cn.hhu.sen.agentsproj.service;

import cn.hhu.sen.agentsproj.client.GitHubApiClient;
import cn.hhu.sen.agentsproj.model.RadarEvidence;
import cn.hhu.sen.agentsproj.model.RepositorySnapshotData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Collects repository facts. It is intentionally separate from scoring so the
 * same facts can later feed different team rubrics without another GitHub call.
 */
@Slf4j
@Service
public class RadarDataService {

    private final GitHubApiClient apiClient;
    private final boolean demoMode;

    public RadarDataService(GitHubApiClient apiClient,
                            @Value("${app.demo-mode:false}") boolean demoMode) {
        this.apiClient = apiClient;
        this.demoMode = demoMode;
    }

    public RepositorySnapshotData collect(String repoName) {
        String normalized = normalizeRepoName(repoName);
        if (demoMode) {
            return collectDemo(normalized);
        }

        String[] parts = normalized.split("/");
        String owner = parts[0];
        String repo = parts[1];
        String collectedAt = Instant.now().toString();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var metaFuture = executor.submit(() -> apiClient.getRepoMeta(owner, repo));
            var readmeFuture = executor.submit(() -> apiClient.getReadme(owner, repo));
            var treeFuture = executor.submit(() -> apiClient.getFileTree(owner, repo));
            var contributorsFuture = executor.submit(() -> apiClient.getContributorCount(owner, repo));
            var revisionFuture = executor.submit(() -> apiClient.getLatestCommitSha(owner, repo));

            Map<String, Object> meta = metaFuture.get();
            String readme = readmeFuture.get();
            String rootTree = treeFuture.get();
            int contributors = contributorsFuture.get();
            String revision = revisionFuture.get();

            Map<String, Object> metrics = buildMetrics(meta, readme, rootTree, contributors, revision);
            List<RadarEvidence> evidence = buildEvidence(normalized, metrics, collectedAt);
            return new RepositorySnapshotData(
                    normalized,
                    revision == null || revision.isBlank() ? stringValue(metrics.get("lastPushedAt")) : revision,
                    metrics,
                    evidence);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("仓库数据采集被中断", e);
        } catch (Exception e) {
            log.error("[Radar] 采集仓库数据失败: {}", normalized, e);
            throw new IllegalStateException("仓库数据采集失败: " + e.getMessage(), e);
        }
    }

    public String normalizeRepoName(String repoName) {
        if (repoName == null || !repoName.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("repoName 必须是 owner/repository 格式");
        }
        return repoName.trim();
    }

    private Map<String, Object> buildMetrics(Map<String, Object> meta,
                                             String readme,
                                             String rootTree,
                                             int contributors,
                                             String revision) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("description", stringValue(meta.get("description")));
        metrics.put("language", stringValue(meta.get("language")));
        metrics.put("stars", numberValue(meta.get("stargazers_count")));
        metrics.put("forks", numberValue(meta.get("forks_count")));
        metrics.put("openIssues", numberValue(meta.get("open_issues_count")));
        metrics.put("contributors", contributors);
        metrics.put("license", licenseValue(meta.get("license")));
        metrics.put("archived", Boolean.TRUE.equals(meta.get("archived")));
        metrics.put("lastPushedAt", stringValue(meta.get("pushed_at")));
        metrics.put("lastUpdatedAt", stringValue(meta.get("updated_at")));
        metrics.put("readmeChars", readme == null ? 0 : readme.length());
        metrics.put("hasBuildFile", containsBuildFile(rootTree));
        metrics.put("hasContainerFile", containsIgnoreCase(rootTree, "dockerfile"));
        metrics.put("hasLicenseFile", containsIgnoreCase(rootTree, "license"));
        metrics.put("sourceType", "github-api");
        metrics.put("latestCommitSha", revision == null ? "" : revision);
        return metrics;
    }

    private List<RadarEvidence> buildEvidence(String repoName,
                                               Map<String, Object> metrics,
                                               String collectedAt) {
        String repoUrl = "https://github.com/" + repoName;
        List<RadarEvidence> evidence = new ArrayList<>();
        evidence.add(new RadarEvidence("stars", stringValue(metrics.get("stars")),
                repoUrl, collectedAt, "GitHub repository metadata"));
        evidence.add(new RadarEvidence("lastPushedAt", stringValue(metrics.get("lastPushedAt")),
                repoUrl + "/commits", collectedAt, "最近一次推送时间"));
        evidence.add(new RadarEvidence("contributors", stringValue(metrics.get("contributors")),
                repoUrl + "/graphs/contributors", collectedAt, "贡献者数量"));
        evidence.add(new RadarEvidence("license", stringValue(metrics.get("license")),
                repoUrl, collectedAt, "仓库许可证"));
        evidence.add(new RadarEvidence("documentation", stringValue(metrics.get("readmeChars")),
                repoUrl + "#readme", collectedAt, "README 字符数"));
        evidence.add(new RadarEvidence("latestCommitSha", stringValue(metrics.get("latestCommitSha")),
                repoUrl + "/commits", collectedAt, "用于历史快照去重"));
        return evidence;
    }

    private RepositorySnapshotData collectDemo(String repoName) {
        String collectedAt = Instant.now().toString();
        String lowerName = repoName.toLowerCase();
        long stars = 2400L;
        long forks = 280L;
        long openIssues = 18L;
        int contributors = 12;
        String language = "Java";
        String license = "Apache-2.0";
        int readmeChars = 4200;
        long daysSincePush = 7;
        if (lowerName.contains("typescript")) {
            stars = 105000L;
            forks = 14500L;
            openIssues = 620L;
            contributors = 180;
            language = "TypeScript";
            license = "Apache-2.0";
            readmeChars = 12000;
            daysSincePush = 2;
        } else if (lowerName.contains("langchain")) {
            stars = 95000L;
            forks = 16000L;
            openIssues = 310L;
            contributors = 420;
            language = "Python";
            license = "MIT";
            readmeChars = 9000;
            daysSincePush = 4;
        }
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("description", "Demo repository facts for radar development");
        metrics.put("language", language);
        metrics.put("stars", stars);
        metrics.put("forks", forks);
        metrics.put("openIssues", openIssues);
        metrics.put("contributors", contributors);
        metrics.put("license", license);
        metrics.put("archived", false);
        metrics.put("lastPushedAt", LocalDateTime.now().minusDays(daysSincePush).toString());
        metrics.put("lastUpdatedAt", LocalDateTime.now().minusDays(2).toString());
        metrics.put("readmeChars", readmeChars);
        metrics.put("hasBuildFile", true);
        metrics.put("hasContainerFile", true);
        metrics.put("hasLicenseFile", true);
        metrics.put("sourceType", "demo");
        metrics.put("latestCommitSha", "demo-" + repoName.replace('/', '-'));

        List<RadarEvidence> evidence = List.of(
                new RadarEvidence("stars", stringValue(metrics.get("stars")), "demo://" + repoName, collectedAt, "演示数据"),
                new RadarEvidence("lastPushedAt", stringValue(metrics.get("lastPushedAt")),
                        "demo://" + repoName, collectedAt, "演示数据"),
                new RadarEvidence("contributors", stringValue(metrics.get("contributors")), "demo://" + repoName, collectedAt, "演示数据"),
                new RadarEvidence("license", stringValue(metrics.get("license")), "demo://" + repoName, collectedAt, "演示数据"),
                new RadarEvidence("documentation", stringValue(metrics.get("readmeChars")), "demo://" + repoName, collectedAt, "演示数据")
        );
        return new RepositorySnapshotData(repoName, stringValue(metrics.get("latestCommitSha")), metrics, evidence);
    }

    private boolean containsBuildFile(String value) {
        return containsIgnoreCase(value, "pom.xml")
                || containsIgnoreCase(value, "build.gradle")
                || containsIgnoreCase(value, "package.json")
                || containsIgnoreCase(value, "pyproject.toml")
                || containsIgnoreCase(value, "go.mod");
    }

    private boolean containsIgnoreCase(String value, String target) {
        return value != null && value.toLowerCase().contains(target.toLowerCase());
    }

    private String licenseValue(Object value) {
        if (value instanceof Map<?, ?> license) {
            Object spdx = license.get("spdx_id");
            if (spdx != null && !String.valueOf(spdx).isBlank()) {
                return String.valueOf(spdx);
            }
            Object name = license.get("name");
            return name == null ? "" : String.valueOf(name);
        }
        return value == null ? "" : String.valueOf(value);
    }

    private long numberValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
