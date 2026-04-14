package cn.hhu.sen.agentsproj.service;

import java.util.List;

import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import cn.hhu.sen.agentsproj.agent.IntentAgent;
import cn.hhu.sen.agentsproj.agent.ReportAgent;
import cn.hhu.sen.agentsproj.entity.TaskRecord;
import cn.hhu.sen.agentsproj.exception.NonRetryableException;
import cn.hhu.sen.agentsproj.model.AgentTaskMessage;
import cn.hhu.sen.agentsproj.model.ProjectAnalysis;
import cn.hhu.sen.agentsproj.model.RepoItem;
import cn.hhu.sen.agentsproj.model.TaskStatus;
import cn.hhu.sen.agentsproj.model.TechReport;
import cn.hhu.sen.agentsproj.model.WorkflowContext;
import cn.hhu.sen.agentsproj.repository.TaskRecordRepository;
import cn.hhu.sen.agentsproj.tools.GitHubTools;
import cn.hhu.sen.agentsproj.tools.ImageTools;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

@Slf4j
@Service
public class GitHubWorkflowService {

    private final ChatClient chatClient;
    private final GitHubTools gitHubTools;
    private final ReportAgent reportAgent;
    private final ImageTools imageTools;
    private final SyncMcpToolCallbackProvider mcpToolCallbackProvider;
    private final ObjectMapper objectMapper;
    private final IntentAgent intentAgent;
    private final TaskRecordRepository taskRepository;
    private final RocketMQTemplate rocketMQTemplate;
    private final AnalysisCacheService analysisCacheService;

    public GitHubWorkflowService(ChatClient.Builder chatClientBuilder,
                                 GitHubTools gitHubTools,
                                 ReportAgent reportAgent,
                                 ImageTools imageTools,
                                 SyncMcpToolCallbackProvider mcpToolCallbackProvider,
                                 ObjectMapper objectMapper,
                                 IntentAgent intentAgent,
                                 TaskRecordRepository taskRepository,
                                 RocketMQTemplate rocketMQTemplate,
                                 AnalysisCacheService analysisCacheService) {
        this.chatClient = chatClientBuilder.build();
        this.gitHubTools = gitHubTools;
        this.reportAgent = reportAgent;
        this.imageTools = imageTools;
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
        this.objectMapper = objectMapper;
        this.intentAgent = intentAgent;
        this.taskRepository = taskRepository;
        this.rocketMQTemplate = rocketMQTemplate;
        this.analysisCacheService = analysisCacheService;
    }

    @Transactional
    public String submitTask(WorkflowContext ctx) {
        IntentAgent.IntentResult result = intentAgent.recognizeIntent(ctx.getUserMessage());
        ctx.setRepoHint(result.repoHint());

        TaskRecord task = new TaskRecord();
        task.setTaskId(ctx.getSessionId());
        task.setIntent(result.intent());
        task.setRepoHint(result.repoHint());
        task.setUserMessage(ctx.getUserMessage());
        task.setStatus(TaskStatus.PENDING.name());
        taskRepository.save(task);

        final String taskId = task.getTaskId();
        final AgentTaskMessage message = new AgentTaskMessage(taskId, task.getIntent(), task.getRepoHint());
        
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rocketMQTemplate.convertAndSend("AGENT_PIPELINE_TOPIC", message);
                log.info("[Producer] 任务已投递到 MQ, taskId: {}", taskId);
            }
        });

        return taskId;
    }

    public void processTask(String taskId, WorkflowContext ctx) {
        TaskRecord task = taskRepository.findById(taskId).orElseThrow();

        if (TaskStatus.SUCCESS.name().equals(task.getStatus())) {
            log.info("[Workflow] 任务已成功，忽略重复投递, taskId: {}", taskId);
            return;
        }

        try {
            updateStatus(task, TaskStatus.INTENT_RECOGNIZED);
            log.info("[Workflow] 意图识别完成, taskId: {}, intent: {}", taskId, task.getIntent());

            updateStatus(task, TaskStatus.DATA_FETCHING);
            log.info("[Workflow] 开始数据抓取, taskId: {}", taskId);

            String resultText = switch (task.getIntent()) {
                case "FETCH_TRENDING" -> {
                    updateStatus(task, TaskStatus.AI_ANALYZING);
                    updateStatus(task, TaskStatus.REPORT_GENERATING);
                    yield executeFetchTrending(ctx);
                }
                case "ANALYZE_ONLY" -> {
                    updateStatus(task, TaskStatus.AI_ANALYZING);
                    updateStatus(task, TaskStatus.REPORT_GENERATING);
                    yield executeAnalyzeOnly(ctx);
                }
                case "ANALYZE_AND_GENERATE"  -> executeAnalyzeAndGenerate(ctx, task);
                case "ANALYZE_AND_PUBLISH"   -> executeFullPipeline(ctx, task);
                case "DIRECT_PUBLISH" -> {
                    updateStatus(task, TaskStatus.AI_ANALYZING);
                    updateStatus(task, TaskStatus.REPORT_GENERATING);
                    yield executeDirectPublish(ctx);
                }
                default -> executeAnalyzeAndGenerate(ctx, task);
            };

            updateStatus(task, TaskStatus.SUCCESS);
            task.setResultData(resultText);
            taskRepository.save(task);
            log.info("[Workflow] 任务执行成功, taskId: {}", taskId);

        } catch (NonRetryableException e) {
            log.error("[Workflow] 任务执行失败(不可重试), taskId: {}, errorCode: {}", taskId, e.getErrorCode(), e);
            updateStatus(task, TaskStatus.FAILED);
            task.setErrorMsg("[" + e.getErrorCode() + "] " + e.getMessage());
            taskRepository.save(task);
        } catch (Exception e) {
            log.error("[Workflow] 任务执行失败(可重试), taskId: {}", taskId, e);
            updateStatus(task, TaskStatus.FAILED);
            task.setErrorMsg(e.getMessage());
            taskRepository.save(task);
            if (isRetryableException(e)) {
                throw new RuntimeException("任务执行失败，触发MQ重试", e);
            }
        }
    }

    private void updateStatus(TaskRecord task, TaskStatus next) {
        TaskStatus current = task.getTaskStatus();
        task.transitionTo(next);
        taskRepository.save(task);
        log.info("[Workflow] 状态转换: {} -> {}", current, next);
    }

    private boolean isRetryableException(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return true;
        }
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("timeout") 
            || lowerMessage.contains("503")
            || lowerMessage.contains("502")
            || lowerMessage.contains("rate limit")
            || lowerMessage.contains("429")
            || lowerMessage.contains("connection refused")
            || lowerMessage.contains("socket timeout");
    }

    private String executeFetchTrending(WorkflowContext ctx) {
        try {
            List<RepoItem> repos = gitHubTools.fetchTrendingRepos("", "daily");
            ctx.setTrendingRepos(repos);
            return formatTrendingList(repos);
        } catch (Exception e) {
            throw new RuntimeException("获取热榜失败", e);
        }
    }

    private String executeAnalyzeOnly(WorkflowContext ctx) {
        String fullName = resolveTargetRepo(ctx);
        ProjectAnalysis analysis = analysisCacheService.getOrAnalyze(fullName);
        ctx.setAnalysis(analysis);
        return formatAnalysis(analysis);
    }

    private String executeAnalyzeAndGenerate(WorkflowContext ctx, TaskRecord task) {
        TechReport report = executeResearchAndGenerate(ctx, task);
        return formatTechReport(report);
    }

    private String executeFullPipeline(WorkflowContext ctx, TaskRecord task) {
        TechReport report = executeResearchAndGenerate(ctx, task);

        String imagePrompt = buildImagePrompt(ctx.getAnalysis());
        String imageUrl = imageTools.generateImage(imagePrompt);
        ctx.setImageUrl(imageUrl);

        return "技术报告生成成功！\n\n" + formatTechReport(report) + "\n\n图片：" + imageUrl;
    }

    private TechReport executeResearchAndGenerate(WorkflowContext ctx, TaskRecord task) {
        String fullName = resolveTargetRepo(ctx);
        
        updateStatus(task, TaskStatus.AI_ANALYZING);
        ProjectAnalysis analysis = analysisCacheService.getOrAnalyze(fullName);
        ctx.setAnalysis(analysis);

        updateStatus(task, TaskStatus.REPORT_GENERATING);
        TechReport report = reportAgent.generateTechReport(analysis);
        ctx.setTechReport(report);
        return report;
    }

    private String executeDirectPublish(WorkflowContext ctx) {
        String extracted = chatClient.prompt()
                .system(new ClassPathResource("prompts/direct-publish-agent.st"))
                .user(ctx.getUserMessage())
                .call()
                .content();

        try {
            String cleanJson = extracted.replaceAll("```json|```", "").trim();
            DirectPublishRequest request = objectMapper.readValue(cleanJson, DirectPublishRequest.class);

            TechReport report = new TechReport();
            report.setRepoName("direct-publish");
            report.setSummary(request.title());
            report.setCoreValue(request.body());
            report.setTechStack(request.tags());

            return "直接发布成功！\n\n标题：" + request.title() + "\n图片：" + request.imageUrl();
        } catch (Exception e) {
            log.error("[Workflow] 直接发布解析失败", e);
            throw new NonRetryableException("PARSE_ERROR", "解析发布内容失败: " + e.getMessage(), e);
        }
    }

    public record DirectPublishRequest(String title, String body, List<String> tags, String imageUrl) {}

    private String resolveTargetRepo(WorkflowContext ctx) {
        String hint = ctx.getRepoHint();

        if (hint != null && !hint.startsWith("TRENDING")) {
            return hint;
        }

        List<RepoItem> repos = ctx.getTrendingRepos();
        if (repos == null || repos.isEmpty()) {
            repos = gitHubTools.fetchTrendingRepos("", "daily");
            ctx.setTrendingRepos(repos);
        }

        int index = 0;
        if (hint != null && hint.startsWith("TRENDING_N:")) {
            try {
                index = Integer.parseInt(hint.split(":")[1]) - 1;
                index = Math.max(0, Math.min(index, repos.size() - 1));
            } catch (Exception e) {
                index = 0;
            }
        }

        String fullName = repos.get(index).getFullName();
        log.info("[Workflow] 使用热榜第{}名: {}", index + 1, fullName);
        return fullName;
    }

    private String buildImagePrompt(ProjectAnalysis analysis) {
        return "科技感海报，主题为%s，%s，蓝色调，简洁现代风格"
                .formatted(analysis.getOneLiner(),
                        analysis.getHighlights() != null && !analysis.getHighlights().isEmpty()
                                ? analysis.getHighlights().get(0) : "");
    }

    private String formatTrendingList(List<RepoItem> repos) {
        StringBuilder sb = new StringBuilder("📈 今日 GitHub 热榜\n\n");
        for (int i = 0; i < repos.size(); i++) {
            RepoItem r = repos.get(i);
            sb.append("%d. **%s**\n   %s\n\n"
                    .formatted(i + 1, r.getFullName(), r.getDescription()));
        }
        return sb.toString();
    }

    private String formatAnalysis(ProjectAnalysis a) {
        return """
                📊 项目分析：%s
                
                **定位**：%s
                **爆火原因**：%s
                **技术亮点**：%s
                **目标人群**：%s
                **快速上手**：`%s`
                """.formatted(a.getFullName(), a.getOneLiner(), a.getWhyPopular(),
                a.getHighlights() != null ? String.join(" / ", a.getHighlights()) : "",
                a.getTargetAudience(), a.getQuickStart());
    }

    private String formatTechReport(TechReport r) {
        return """
                📋 技术分析报告
                
                **项目**：%s
                **定位**：%s
                **成熟度**：%s
                **评分**：%d/100
                
                **核心技术价值**：
                %s
                
                **技术栈**：%s
                **风险点**：%s
                **引入建议**：%s
                **竞品对比**：%s
                """.formatted(
                r.getRepoName(),
                r.getSummary(),
                r.getMaturity(),
                r.getScore() != null ? r.getScore() : 0,
                r.getCoreValue(),
                r.getTechStack() != null ? String.join(" / ", r.getTechStack()) : "",
                r.getRiskPoints() != null ? String.join(" / ", r.getRiskPoints()) : "",
                r.getAdoptionAdvice(),
                r.getCompetitorComparison());
    }
}
