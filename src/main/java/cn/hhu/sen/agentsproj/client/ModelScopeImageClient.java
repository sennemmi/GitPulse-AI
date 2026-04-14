package cn.hhu.sen.agentsproj.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import cn.hhu.sen.agentsproj.exception.ImageGenerationException;

/**
 * ModelScope 魔搭社区文生图 API 客户端
 * 封装异步调用流程，对外暴露同步接口
 */
@Slf4j
@Component
public class ModelScopeImageClient {

    private static final String AGENT_NAME = "ModelScopeImageClient";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String imageModel;
    private final long pollIntervalMs;
    private final int pollMaxAttempts;

    public ModelScopeImageClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${modelscope.api-key}") String apiKey,
            @Value("${modelscope.base-url}") String baseUrl,
            @Value("${modelscope.image-model}") String imageModel,
            @Value("${modelscope.poll-interval-ms:3000}") long pollIntervalMs,
            @Value("${modelscope.poll-max-attempts:20}") int pollMaxAttempts) {

        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.objectMapper = objectMapper;
        this.imageModel = imageModel;
        this.pollIntervalMs = pollIntervalMs;
        this.pollMaxAttempts = pollMaxAttempts;

        log.info("[{}] 初始化完成 | model: {} | pollInterval: {}ms | maxAttempts: {} | 总超时: {}秒",
                AGENT_NAME, imageModel, pollIntervalMs, pollMaxAttempts, (pollIntervalMs * pollMaxAttempts) / 1000);
    }

    /**
     * 同步生成图片
     * 内部封装异步流程：提交任务 → 轮询状态 → 返回图片 URL
     *
     * @param prompt 图片生成提示词
     * @return 图片访问 URL
     */
    public String generate(String prompt) {
        log.info("[{}] 开始生成图片 | prompt: {}", AGENT_NAME, prompt);
        long startTime = System.currentTimeMillis();

        try {
            // 1. 提交任务
            String taskId = submitTask(prompt);
            log.info("[{}] 任务已提交 | taskId: {} | 即将开始轮询（间隔 {}ms，最多 {} 次）",
                    AGENT_NAME, taskId, pollIntervalMs, pollMaxAttempts);

            // 2. 轮询任务状态
            String imageUrl = pollTaskStatus(taskId, startTime);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[{}] 图片生成完成 | 总耗时: {}ms | URL: {}", AGENT_NAME, duration, imageUrl);

            return imageUrl;

        } catch (ImageGenerationException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[{}] 图片生成失败 | 总耗时: {}ms | 错误: {}", AGENT_NAME, duration, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[{}] 图片生成失败 | 总耗时: {}ms | 错误: {}", AGENT_NAME, duration, e.getMessage(), e);
            throw ImageGenerationException.generationFailed("图片生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提交生成任务
     */
    private String submitTask(String prompt) {
        log.debug("[{}] 提交生成任务", AGENT_NAME);

        try {
            // 构建请求体
            String requestBody = objectMapper.createObjectNode()
                    .put("model", imageModel)
                    .put("prompt", prompt)
                    .toString();

            String response = webClient.post()
                    .uri("/images/generations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-ModelScope-Async-Mode", "true")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode responseNode = objectMapper.readTree(response);
            String taskId = responseNode.get("task_id").asText();

            if (taskId == null || taskId.isEmpty()) {
                throw ImageGenerationException.generationFailed("提交任务失败：未返回 task_id");
            }

            return taskId;

        } catch (Exception e) {
            log.error("[{}] 提交任务失败: {}", AGENT_NAME, e.getMessage(), e);
            throw ImageGenerationException.generationFailed("提交图片生成任务失败", e);
        }
    }

    /**
     * 轮询任务状态
     */
    private String pollTaskStatus(String taskId, long startTime) {
        log.info("[{}] 开始轮询任务状态 | taskId: {} | 轮询间隔: {}ms | 最大次数: {}",
                AGENT_NAME, taskId, pollIntervalMs, pollMaxAttempts);

        for (int attempt = 1; attempt <= pollMaxAttempts; attempt++) {
            try {
                // 每次轮询都等待（包括第一次），符合文生图任务的实际耗时
                long waitStart = System.currentTimeMillis();
                Thread.sleep(pollIntervalMs);
                long actualWait = System.currentTimeMillis() - waitStart;

                long elapsed = System.currentTimeMillis() - startTime;
                log.debug("[{}] 第 {}/{} 次轮询前等待 {}ms | 已耗时 {}ms | taskId: {}",
                        AGENT_NAME, attempt, pollMaxAttempts, actualWait, elapsed, taskId);

                String response = webClient.get()
                        .uri("/tasks/{taskId}", taskId)
                        .header("X-ModelScope-Task-Type", "image_generation")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode responseNode = objectMapper.readTree(response);
                String taskStatus = responseNode.get("task_status").asText();

                long pollElapsed = System.currentTimeMillis() - startTime;
                log.info("[{}] 第 {}/{} 次轮询 | taskId: {} | status: {} | 已耗时 {}ms",
                        AGENT_NAME, attempt, pollMaxAttempts, taskId, taskStatus, pollElapsed);

                switch (taskStatus) {
                    case "SUCCEED":
                        JsonNode outputImages = responseNode.get("output_images");
                        if (outputImages != null && outputImages.isArray() && outputImages.size() > 0) {
                            String imageUrl = outputImages.get(0).asText();
                            log.info("[{}] 任务完成 | 共轮询 {} 次 | 图片URL: {}",
                                    AGENT_NAME, attempt, imageUrl);
                            return imageUrl;
                        }
                        throw ImageGenerationException.generationFailed("任务完成但未返回图片 URL");

                    case "FAILED":
                        String errorMessage = "图片生成任务失败";
                        if (responseNode.has("errors") && responseNode.get("errors").has("message")) {
                            errorMessage = responseNode.get("errors").get("message").asText();
                        }
                        log.error("[{}] 任务失败 | taskId: {} | 错误: {}", AGENT_NAME, taskId, errorMessage);
                        throw ImageGenerationException.generationFailed(errorMessage);

                    case "PROCESSING":
                    case "PENDING":
                        // 继续轮询
                        log.debug("[{}] 任务处理中 | taskId: {} | status: {} | 将继续轮询",
                                AGENT_NAME, taskId, taskStatus);
                        break;

                    default:
                        log.warn("[{}] 未知任务状态 | taskId: {} | status: {}", AGENT_NAME, taskId, taskStatus);
                        break;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[{}] 轮询被中断 | taskId: {}", AGENT_NAME, taskId);
                throw ImageGenerationException.generationFailed("轮询被中断", e);
            } catch (ImageGenerationException e) {
                throw e;
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;
                log.error("[{}] 第 {} 次轮询异常 | taskId: {} | 已耗时 {}ms | 错误: {}",
                        AGENT_NAME, attempt, taskId, elapsed, e.getMessage(), e);
                // 继续轮询，不立即失败
            }
        }

        // 超过最大轮询次数
        long totalElapsed = System.currentTimeMillis() - startTime;
        log.error("[{}] 轮询超时 | taskId: {} | 共轮询 {} 次 | 总耗时 {}ms | 超过最大等待时间",
                AGENT_NAME, taskId, pollMaxAttempts, totalElapsed);
        throw ImageGenerationException.timeout();
    }
}
