package cn.hhu.sen.agentsproj.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.hhu.sen.agentsproj.exception.NonRetryableException;

@Slf4j
@Component
public class IntentAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final boolean demoMode;

    private static final Pattern GITHUB_URL = Pattern.compile(
            "github\\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPO_NAME = Pattern.compile(
            "(?<![A-Za-z0-9_.-])([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)(?![A-Za-z0-9_.-])");
    private static final Pattern TRENDING_INDEX = Pattern.compile(
            "(?:第|top\\s*|trending\\s*)(\\d+)", Pattern.CASE_INSENSITIVE);

    public IntentAgent(ChatClient.Builder builder,
                       ObjectMapper objectMapper,
                       @Value("${app.demo-mode:false}") boolean demoMode) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
        this.demoMode = demoMode;
    }

    public record IntentResult(String intent, String repoHint) {}

    public IntentResult recognizeIntent(String message) {
        if (message == null || message.isBlank()) {
            throw NonRetryableException.invalidRequest("message 不能为空");
        }

        if (demoMode) {
            return recognizeDemoIntent(message.trim());
        }

        // 1. 规则快速匹配（保留你原来的快速匹配逻辑）
        if (message.contains("热榜") && !message.contains("分析") && !message.contains("文案")) {
            return new IntentResult("FETCH_TRENDING", null);
        }

        // 2. LLM 兜底识别
        String json = chatClient.prompt()
                .system(new ClassPathResource("prompts/intent-agent.st"))
                .user(message)
                .call()
                .content()
                .trim();

        try {
            String cleanJson = json.replaceAll("```json|```", "").trim();
            return objectMapper.readValue(cleanJson, IntentResult.class);
        } catch (Exception e) {
            log.warn("[IntentAgent] 意图识别失败，降级为 ANALYZE_AND_GENERATE. 原文: {}", json);
            return new IntentResult("ANALYZE_AND_GENERATE", null);
        }
    }

    private IntentResult recognizeDemoIntent(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        String repoHint = extractRepoHint(message);
        boolean trending = normalized.contains("热榜") || normalized.contains("trending");
        if (repoHint == null && trending) {
            Matcher matcher = TRENDING_INDEX.matcher(message);
            repoHint = matcher.find()
                    ? "TRENDING_N:" + matcher.group(1)
                    : "TRENDING_N:1";
        }

        boolean publish = normalized.contains("发布")
                || normalized.contains("导出")
                || normalized.contains("publish")
                || normalized.contains("export");
        if (publish) {
            return new IntentResult(repoHint == null ? "DIRECT_PUBLISH" : "ANALYZE_AND_PUBLISH", repoHint);
        }
        if (trending
                && !normalized.contains("分析")
                && !normalized.contains("analy")
                && !normalized.contains("报告")
                && !normalized.contains("report")) {
            return new IntentResult("FETCH_TRENDING", repoHint);
        }
        if (normalized.contains("只分析")
                || normalized.contains("仅分析")
                || normalized.contains("不要报告")
                || normalized.contains("不生成报告")
                || normalized.contains("analyze only")
                || normalized.contains("analysis only")) {
            return new IntentResult("ANALYZE_ONLY", repoHint);
        }
        return new IntentResult("ANALYZE_AND_GENERATE", repoHint);
    }

    private String extractRepoHint(String message) {
        Matcher urlMatcher = GITHUB_URL.matcher(message);
        String candidate = urlMatcher.find() ? urlMatcher.group(1) : null;
        if (candidate == null) {
            Matcher repoMatcher = REPO_NAME.matcher(message);
            candidate = repoMatcher.find() ? repoMatcher.group(1) : null;
        }
        if (candidate == null) {
            return null;
        }
        candidate = candidate.replaceAll("[),.;!?，。；！？]+$", "");
        return candidate.endsWith(".git")
                ? candidate.substring(0, candidate.length() - 4)
                : candidate;
    }
}
