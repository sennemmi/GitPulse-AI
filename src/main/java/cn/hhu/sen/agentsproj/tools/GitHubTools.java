package cn.hhu.sen.agentsproj.tools;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import cn.hhu.sen.agentsproj.model.RepoItem;
import cn.hhu.sen.agentsproj.service.GitHubFetcherService;
import lombok.extern.slf4j.Slf4j;

/**
 * GitHubTools - GitHub 相关工具
 * 职责单一：只提供获取热榜的功能
 * 项目深度研究已转移至 ResearchAgent
 */
@Slf4j
@Component
public class GitHubTools {

    private static final String AGENT_NAME = "GitHubTrendAgent";

    private final GitHubFetcherService fetcherService;
    private final boolean demoMode;

    public GitHubTools(GitHubFetcherService fetcherService,
                       @Value("${app.demo-mode:false}") boolean demoMode) {
        this.fetcherService = fetcherService;
        this.demoMode = demoMode;
    }

    public List<RepoItem> fetchTrendingRepos(String language, String since) {
        log.info("[{}] 执行工具函数: fetchTrendingRepos | 参数: language={}, since={}", AGENT_NAME, language, since);
        long startTime = System.currentTimeMillis();

        try {
            if (demoMode) {
                List<RepoItem> result = demoTrendingRepos();
                log.info("[{}] 演示模式返回 {} 条数据 | 耗时 {}ms", AGENT_NAME, result.size(),
                        System.currentTimeMillis() - startTime);
                return result;
            }
            List<RepoItem> result = fetcherService.fetchTrending(language, since);
            log.info("[{}] 工具函数 fetchTrendingRepos 执行成功 | 返回 {} 条数据 | 耗时 {}ms",
                    AGENT_NAME, result.size(), System.currentTimeMillis() - startTime);
            if (!result.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(3, result.size()); i++) {
                    RepoItem item = result.get(i);
                    sb.append("[").append(i + 1).append("]").append(item.getFullName()).append(" ");
                }
                if (result.size() > 3) {
                    sb.append("... 等共 ").append(result.size()).append(" 个项目");
                }
                log.debug("[{}] 返回内容摘要: {}", AGENT_NAME, sb.toString());
            }
            return result;
        } catch (Exception e) {
            log.error("[{}] 工具函数 fetchTrendingRepos 执行失败 | 耗时 {}ms | 错误: {}",
                    AGENT_NAME, System.currentTimeMillis() - startTime, e.getMessage(), e);
            throw new RuntimeException("获取热榜失败", e);
        }
    }

    private List<RepoItem> demoTrendingRepos() {
        return List.of(
                RepoItem.builder()
                        .fullName("sennemmi/GitPulse-AI")
                        .description("Java 21 多智能体 GitHub 技术情报分析系统")
                        .language("Java")
                        .stars("demo")
                        .todayStars("demo")
                        .url("https://github.com/sennemmi/GitPulse-AI")
                        .build(),
                RepoItem.builder()
                        .fullName("microsoft/TypeScript")
                        .description("JavaScript with syntax for types")
                        .language("TypeScript")
                        .stars("demo")
                        .todayStars("demo")
                        .url("https://github.com/microsoft/TypeScript")
                        .build(),
                RepoItem.builder()
                        .fullName("langchain-ai/langchain")
                        .description("Build context-aware reasoning applications")
                        .language("Python")
                        .stars("demo")
                        .todayStars("demo")
                        .url("https://github.com/langchain-ai/langchain")
                        .build()
        );
    }
}
