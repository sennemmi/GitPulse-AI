package cn.hhu.sen.agentsproj.agent;

import java.util.Map;
import java.util.List;
import java.util.concurrent.Executors;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hhu.sen.agentsproj.client.GitHubApiClient;
import cn.hhu.sen.agentsproj.model.ProjectAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ResearchAgent {

    private final ChatClient chatClient;
    private final GitHubApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final boolean demoMode;

    public ResearchAgent(ChatClient.Builder builder,
                         GitHubApiClient apiClient,
                         ObjectMapper objectMapper,
                         @Value("${app.demo-mode:false}") boolean demoMode) {
        this.chatClient = builder.build();
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
        this.demoMode = demoMode;
    }

    public ProjectAnalysis run(String fullName) {
        log.info("[ResearchAgent] 开始研究: {}", fullName);
        long start = System.currentTimeMillis();

        if (demoMode) {
            ProjectAnalysis analysis = buildDemoAnalysis(fullName);
            log.info("[ResearchAgent] 演示模式完成: {} | 耗时: {}ms", fullName,
                    System.currentTimeMillis() - start);
            return analysis;
        }

        String[] parts = fullName.split("/");
        String owner = parts[0];
        String repo = parts[1];

        String readme;
        String fileTree;
        Map<String, Object> repoMeta;
        int contributorCount;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var readmeFuture = executor.submit(() -> apiClient.getReadme(owner, repo));
            var fileTreeFuture = executor.submit(() -> apiClient.getFileTree(owner, repo));
            var metaFuture = executor.submit(() -> apiClient.getRepoMeta(owner, repo));
            var contributorFuture = executor.submit(() -> apiClient.getContributorCount(owner, repo));

            readme = readmeFuture.get();
            fileTree = fileTreeFuture.get();
            repoMeta = metaFuture.get();

            int cnt = 0;
            try {
                cnt = contributorFuture.get();
            } catch (Exception e) {
                log.warn("[ResearchAgent] 获取贡献者数量失败，降级为 -1: {}", e.getMessage());
                cnt = -1;
            }
            contributorCount = cnt;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("虚拟线程并发获取数据被中断", e);
        } catch (Exception e) {
            log.error("[ResearchAgent] 并发获取 GitHub 数据失败", e);
            throw new RuntimeException("获取项目基础数据失败", e);
        }

        String safeReadme = readme.length() > 100_000
                ? readme.substring(0, 100_000) + "\n...(超长README已截断)" : readme;

        long stars = repoMeta != null && repoMeta.get("stargazers_count") != null
                ? ((Number) repoMeta.get("stargazers_count")).longValue() : 0;
        long forks = repoMeta != null && repoMeta.get("forks_count") != null
                ? ((Number) repoMeta.get("forks_count")).longValue() : 0;
        long openIssues = repoMeta != null && repoMeta.get("open_issues_count") != null
                ? ((Number) repoMeta.get("open_issues_count")).longValue() : 0;
        String language = repoMeta != null ? (String) repoMeta.get("language") : "";

        log.info("[ResearchAgent] 并发获取数据完成 | README: {}字 | 文件树: {}字 | Stars: {} | Forks: {} | Issues: {} | Contributors: {} | 耗时: {}ms",
                readme.length(), fileTree.length(), stars, forks, openIssues, contributorCount,
                System.currentTimeMillis() - start);

        String rawData = """
                项目：%s
                语言：%s
                Stars: %d
                Forks: %d
                Open Issues: %d
                Contributors: %d
                文件结构：
                %s
                README：
                %s
                """.formatted(fullName, language, stars, forks, openIssues, contributorCount, fileTree, safeReadme);

        long llmStart = System.currentTimeMillis();
        Entry entry = null;
        String json;
        try {
            entry = SphU.entry("llmCall");
            json = chatClient.prompt()
                    .system(new ClassPathResource("prompts/research-agent.st"))
                    .user(rawData)
                    .call()
                    .content();
            log.info("[ResearchAgent] LLM 调用完成 | 响应长度: {}字 | 耗时: {}ms",
                    json.length(), System.currentTimeMillis() - llmStart);
        } catch (BlockException e) {
            log.warn("[ResearchAgent] LLM 调用被熔断，降级处理");
            throw new RuntimeException("AI 服务暂时不可用，请稍后重试");
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }

        try {
            String cleanJson = json.replaceAll("```json|```", "").trim();
            ProjectAnalysis analysis = objectMapper.readValue(cleanJson, ProjectAnalysis.class);
            analysis.setFullName(fullName);
            log.info("[ResearchAgent] 完成: {} | 总耗时: {}ms", fullName,
                    System.currentTimeMillis() - start);
            return analysis;
        } catch (Exception e) {
            log.error("[ResearchAgent] JSON解析失败，降级为基础分析", e);
            ProjectAnalysis fallback = new ProjectAnalysis();
            fallback.setFullName(fullName);
            fallback.setOneLiner(fullName + " - GitHub 热门项目");
            fallback.setWhyPopular(json);
            return fallback;
        }
    }

    private ProjectAnalysis buildDemoAnalysis(String fullName) {
        ProjectAnalysis analysis = new ProjectAnalysis();
        analysis.setFullName(fullName);
        analysis.setOneLiner("基于 Spring Boot 与多智能体协作的 GitHub 技术情报分析系统");
        analysis.setWhyPopular("把仓库研究、结构化报告和异步任务处理组合成一条可复用的分析流水线。");
        analysis.setHighlights(List.of(
                "IntentAgent、ResearchAgent、ReportAgent 分工协作",
                "RocketMQ 异步编排与 Redis 缓存/分布式锁",
                "Java 21 虚拟线程承载并发 I/O 任务"
        ));
        analysis.setTargetAudience("需要批量评估开源项目的研发、架构和技术选型团队");
        analysis.setQuickStart("./mvnw spring-boot:run -Dspring-boot.run.profiles=wsl");
        analysis.setTags(List.of("Java 21", "Spring Boot", "Spring AI", "RocketMQ", "Redis"));
        return analysis;
    }
}
