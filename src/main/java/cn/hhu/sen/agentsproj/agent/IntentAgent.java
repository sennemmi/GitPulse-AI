package cn.hhu.sen.agentsproj.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IntentAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public IntentAgent(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public record IntentResult(String intent, String repoHint) {}

    public IntentResult recognizeIntent(String message) {
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
}
