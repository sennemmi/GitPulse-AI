package cn.hhu.sen.agentsproj.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hhu.sen.agentsproj.model.ProjectAnalysis;
import cn.hhu.sen.agentsproj.model.TechReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class ReportAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ReportAgent(ChatClient.Builder builder,
                       ObjectMapper objectMapper,
                       SyncMcpToolCallbackProvider mcpToolCallbackProvider) {
        this.chatClient = builder
                .defaultToolCallbacks(mcpToolCallbackProvider.getToolCallbacks())
                .build();
        this.objectMapper = objectMapper;
    }

    public TechReport generateTechReport(ProjectAnalysis analysis) {
        log.info("[ReportAgent] 开始生成技术报告: {}", analysis.getFullName());
        long start = System.currentTimeMillis();

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
}
