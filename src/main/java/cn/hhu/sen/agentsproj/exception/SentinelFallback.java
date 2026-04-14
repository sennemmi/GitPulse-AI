package cn.hhu.sen.agentsproj.exception;

import cn.hhu.sen.agentsproj.controller.AgentController.ChatRequest;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@Slf4j
public class SentinelFallback {

    public static ResponseEntity<Map<String, String>> handleBlock(
            ChatRequest request, String token, BlockException e) {
        log.warn("[Sentinel] 请求被限流或熔断: {}", e.getClass().getSimpleName());
        return ResponseEntity.status(429)
                .body(Map.of(
                    "code", "RATE_LIMITED",
                    "message", "系统繁忙，请稍后重试"
                ));
    }

    public static ResponseEntity<Map<String, String>> handleFallback(
            ChatRequest request, String token, Throwable e) {
        log.error("[Sentinel] 业务降级: {}", e.getMessage());
        return ResponseEntity.status(503)
                .body(Map.of(
                    "code", "SERVICE_DEGRADED",
                    "message", "分析服务暂时不可用"
                ));
    }
}
