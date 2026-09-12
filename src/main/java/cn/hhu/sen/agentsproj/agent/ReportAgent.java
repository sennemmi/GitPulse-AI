package cn.hhu.sen.agentsproj.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hhu.sen.agentsproj.model.ProjectAnalysis;
import cn.hhu.sen.agentsproj.model.TechReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class ReportAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final boolean demoMode;

    public ReportAgent(ChatClient.Builder builder,
                       ObjectMapper objectMapper,
                       ObjectProvider<SyncMcpToolCallbackProvider> mcpToolCallbackProvider,
                       @Value("${app.demo-mode:false}") boolean demoMode) {
        ChatClient.Builder reportBuilder = builder;
        SyncMcpToolCallbackProvider provider = mcpToolCallbackProvider.getIfAvailable();
        if (provider != null) {
            reportBuilder = reportBuilder.defaultToolCallbacks(provider.getToolCallbacks());
        }
        this.chatClient = reportBuilder.build();
        this.objectMapper = objectMapper;
        this.demoMode = demoMode;
    }

    public TechReport generateTechReport(ProjectAnalysis analysis) {
        log.info("[ReportAgent] 开始生成技术报告: {}", analysis.getFullName());
        long start = System.currentTimeMillis();

        if (demoMode) {
            TechReport report = buildDemoReport(analysis);
            log.info("[ReportAgent] 演示模式完成 | 项目: {} | 评分: {} | 耗时: {}ms",
                    report.getRepoName(), report.getScore(), System.currentTimeMillis() - start);
            return report;
        }

        String userInput = """
                项目名称：%s
                一句话定位：%s
                爆火原因：%s
                技术亮点：%s
                目标人群：%s
                快速上手：%s
                推荐标签：%s

                你有 search_repositories 工具可以调用。
                请在生成 competitorComparison 字段前，自行搜索同类项目做对比分析。
                如果第一次搜索结果不理想，可以换关键词再搜一次。
                """.formatted(
                analysis.getFullName(),
                analysis.getOneLiner(),
                analysis.getWhyPopular(),
                analysis.getHighlights() != null
                        ? String.join("、", analysis.getHighlights()) : "",
                analysis.getTargetAudience(),
                analysis.getQuickStart(),
                analysis.getTags() != null
                        ? String.join("、", analysis.getTags()) : ""
        );

        String json = chatClient.prompt()
                .system(new ClassPathResource("prompts/report-agent.st"))
                .user(userInput)
                .call()
                .content();

        try {
            String cleanJson = json.replaceAll("```json|```", "").trim();
            TechReport report = objectMapper.readValue(cleanJson, TechReport.class);
            report.setRepoName(analysis.getFullName());
            report.setGeneratedAt(LocalDateTime.now());
            log.info("[ReportAgent] 技术报告生成完成 | 项目: {} | 评分: {} | 耗时: {}ms",
                    report.getRepoName(), report.getScore(),
                    System.currentTimeMillis() - start);
            return report;
        } catch (Exception e) {
            log.error("[ReportAgent] JSON解析失败", e);
            TechReport fallback = new TechReport();
            fallback.setRepoName(analysis.getFullName());
            fallback.setSummary(analysis.getOneLiner());
            fallback.setMaturity("Unknown");
            fallback.setCoreValue(analysis.getWhyPopular());
            fallback.setScore(50);
            fallback.setGeneratedAt(LocalDateTime.now());
            return fallback;
        }
    }

    private TechReport buildDemoReport(ProjectAnalysis analysis) {
        TechReport report = new TechReport();
        report.setRepoName(analysis.getFullName());
        report.setSummary(analysis.getOneLiner());
        report.setMaturity("Beta");
        report.setTechStack(analysis.getTags() == null || analysis.getTags().isEmpty()
                ? List.of("Java 21", "Spring Boot") : analysis.getTags());
        report.setCoreValue("项目已经形成从请求接入、意图识别、数据研究到报告生成的主流程，"
                + "并通过 RocketMQ、Redis 和 MySQL 具备异步化、缓存和持久化基础。");
        report.setRiskPoints(List.of(
                "真实模式仍依赖 GitHub、模型和 RocketMQ 等外部服务",
                "生产环境需要补充认证、限流策略和可观测性配置"
        ));
        report.setAdoptionAdvice("适合作为技术情报原型或内部工具继续迭代；正式上线前应切换真实凭证并补齐安全与运维能力。");
        report.setCompetitorComparison("GitHub Trending：偏实时热榜抓取；GitPulse AI：增加结构化分析、报告生成与异步任务编排。");
        report.setScore(78);
        report.setGeneratedAt(LocalDateTime.now());
        return report;
    }
}
