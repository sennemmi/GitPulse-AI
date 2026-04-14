package cn.hhu.sen.agentsproj.benchmark;

import cn.hhu.sen.agentsproj.entity.TaskRecord;
import cn.hhu.sen.agentsproj.model.WorkflowContext;
import cn.hhu.sen.agentsproj.repository.TaskRecordRepository;
import cn.hhu.sen.agentsproj.service.GitHubWorkflowService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnalysisLatencyTest {

    @Autowired
    private GitHubWorkflowService workflowService;
    @Autowired
    private TaskRecordRepository taskRepository;

    private static final List<String> TEST_REPOS = List.of(
            "microsoft/vscode",
            "facebook/react",
            "golang/go",
            "spring-projects/spring-boot",
            "apache/kafka"
    );

    @Test
    @Order(1)
    void measureColdAnalysisLatency() throws Exception {
        List<Long> latencies = new ArrayList<>();

        for (String repo : TEST_REPOS) {
            String sessionId = UUID.randomUUID().toString().replace("-", "");
            WorkflowContext ctx = WorkflowContext.builder()
                    .userMessage("分析 " + repo)
                    .sessionId(sessionId)
                    .repoHint(repo)
                    .build();

            long start = System.currentTimeMillis();
            String taskId = workflowService.submitTask(ctx);

            TaskRecord task = pollUntilDone(taskId, Duration.ofMinutes(5));
            long elapsed = System.currentTimeMillis() - start;

            assertThat(task.getStatus()).isEqualTo("SUCCESS");
            latencies.add(elapsed);

            System.out.printf("[耗时] %s → %d ms (%.1f 秒)%n",
                    repo, elapsed, elapsed / 1000.0);
        }

        long avgMs = (long) latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long maxMs = latencies.stream().mapToLong(Long::longValue).max().orElse(0);

        System.out.printf("%n=== 耗时统计 ===%n");
        System.out.printf("平均耗时: %.1f 秒%n", avgMs / 1000.0);
        System.out.printf("最大耗时: %.1f 秒%n", maxMs / 1000.0);
        System.out.printf("人工对比基准: ~1500 秒（25分钟）%n");
        System.out.printf("提升倍数: %.1fx%n", 1500_000.0 / avgMs);

        assertThat(avgMs).isLessThan(180_000L);
    }

    private TaskRecord pollUntilDone(String taskId, Duration timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            TaskRecord task = taskRepository.findById(taskId).orElseThrow();
            if ("SUCCESS".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
                return task;
            }
            Thread.sleep(2000);
        }
        throw new TimeoutException("任务超时: " + taskId);
    }
}
