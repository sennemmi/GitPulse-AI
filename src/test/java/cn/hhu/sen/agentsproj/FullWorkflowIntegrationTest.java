package cn.hhu.sen.agentsproj;

import cn.hhu.sen.agentsproj.entity.TaskRecord;
import cn.hhu.sen.agentsproj.model.WorkflowContext;
import cn.hhu.sen.agentsproj.repository.TaskRecordRepository;
import cn.hhu.sen.agentsproj.service.GitHubWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class FullWorkflowIntegrationTest {

    @Autowired
    private GitHubWorkflowService workflowService;

    @Autowired
    private TaskRecordRepository taskRepository;

    @Test
    public void testFullWorkflowAndOutputReport() throws Exception {
        String targetRepo = "microsoft/TypeScript";
        String userMessage = "帮我分析一下 " + targetRepo + " 这个项目，并生成一份详细的技术分析报告。";
        String sessionId = UUID.randomUUID().toString().replace("-", "");

        System.out.println("==================================================");
        System.out.println("🚀 开始完整工作流测试...");
        System.out.println("Session ID: " + sessionId);
        System.out.println("User Message: " + userMessage);
        System.out.println("==================================================");

        WorkflowContext ctx = WorkflowContext.builder()
                .userMessage(userMessage)
                .sessionId(sessionId)
                .build();

        long startTime = System.currentTimeMillis();
        String taskId = workflowService.submitTask(ctx);
        System.out.println("\n✅ 任务已提交，Task ID: " + taskId);

        TaskRecord finalTask = pollUntilDone(taskId, Duration.ofMinutes(3));
        long elapsed = System.currentTimeMillis() - startTime;

        System.out.println("\n==================================================");
        System.out.println("🏁 测试执行完毕 (总耗时: " + elapsed + " ms)");
        System.out.println("最终状态: " + finalTask.getStatus());
        System.out.println("识别意图: " + finalTask.getIntent());
        
        if ("FAILED".equals(finalTask.getStatus())) {
            System.err.println("❌ 工作流执行失败: " + finalTask.getErrorMsg());
        }

        assertThat(finalTask.getStatus()).isEqualTo("SUCCESS");

        System.out.println("\n📄 Report Agent 最终生成的报告内容:");
        System.out.println("--------------------------------------------------");
        System.out.println(finalTask.getResultData());
        System.out.println("--------------------------------------------------");
    }

    private TaskRecord pollUntilDone(String taskId, Duration timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        String lastStatus = "";

        while (System.currentTimeMillis() < deadline) {
            TaskRecord task = taskRepository.findById(taskId).orElseThrow();
            String currentStatus = task.getStatus();

            if (!currentStatus.equals(lastStatus)) {
                System.out.println("⏳ [状态流转] " + (lastStatus.isEmpty() ? "初始" : lastStatus) + " -> " + currentStatus);
                lastStatus = currentStatus;
            }

            if ("SUCCESS".equals(currentStatus) || "FAILED".equals(currentStatus)) {
                return task;
            }
            Thread.sleep(3000);
        }
        throw new TimeoutException("⌛ 任务执行超时，未能在规定时间内完成，Task ID: " + taskId);
    }
}
