package cn.hhu.sen.agentsproj.controller;

import cn.hhu.sen.agentsproj.entity.TaskRecord;
import cn.hhu.sen.agentsproj.exception.SentinelFallback;
import cn.hhu.sen.agentsproj.model.WorkflowContext;
import cn.hhu.sen.agentsproj.repository.TaskRecordRepository;
import cn.hhu.sen.agentsproj.service.GitHubWorkflowService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final GitHubWorkflowService workflowService;
    private final TaskRecordRepository taskRepository;
    private final RedissonClient redissonClient;

    public AgentController(GitHubWorkflowService workflowService,
                           TaskRecordRepository taskRepository,
                           RedissonClient redissonClient) {
        this.workflowService = workflowService;
        this.taskRepository = taskRepository;
        this.redissonClient = redissonClient;
    }

    @GetMapping("/token")
    public ResponseEntity<Map<String, String>> getIdempotentToken() {
        String token = UUID.randomUUID().toString().replace("-", "");
        redissonClient.getBucket("idempotent:token:" + token)
                .set("unused", 5, TimeUnit.MINUTES);
        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/chat")
    @SentinelResource(
        value = "analyzeRepo",
        blockHandler = "handleBlock",
        blockHandlerClass = SentinelFallback.class,
        fallback = "handleFallback",
        fallbackClass = SentinelFallback.class
    )
    public ResponseEntity<Map<String, String>> chat(
            @RequestBody ChatRequest request,
            @RequestHeader("Idempotency-Token") String token) {

        RBucket<String> bucket = redissonClient.getBucket("idempotent:token:" + token);
        boolean consumed = bucket.compareAndSet("unused", "used");
        if (!consumed) {
            log.warn("[Agent] 重复请求 | token: {}", token);
            return ResponseEntity.status(409)
                    .body(Map.of(
                        "code", "DUPLICATE_REQUEST",
                        "message", "重复提交，请勿重复操作"
                    ));
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("sessionId", sessionId);
        try {
            log.info("[Agent] 请求开始 | 输入长度: {}字", request.message().length());
            long start = System.currentTimeMillis();

            WorkflowContext ctx = WorkflowContext.builder()
                    .userMessage(request.message())
                    .sessionId(sessionId)
                    .build();

            String taskId = workflowService.submitTask(ctx);

            log.info("[Agent] 任务已提交 | taskId: {} | 耗时: {}ms", taskId, System.currentTimeMillis() - start);
            return ResponseEntity.ok(Map.of("taskId", taskId, "status", "PENDING"));
        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<TaskRecord> getTaskStatus(@PathVariable String taskId) {
        return taskRepository.findById(taskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record ChatRequest(String message) {}
}
