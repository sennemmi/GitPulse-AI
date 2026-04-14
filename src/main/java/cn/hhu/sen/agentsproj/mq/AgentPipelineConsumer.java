package cn.hhu.sen.agentsproj.mq;

import java.util.concurrent.ExecutorService;

import cn.hhu.sen.agentsproj.entity.TaskRecord;
import cn.hhu.sen.agentsproj.model.AgentTaskMessage;
import cn.hhu.sen.agentsproj.model.WorkflowContext;
import cn.hhu.sen.agentsproj.repository.TaskRecordRepository;
import cn.hhu.sen.agentsproj.service.GitHubWorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(topic = "AGENT_PIPELINE_TOPIC", consumerGroup = "agent-pipeline-consumer-group")
public class AgentPipelineConsumer implements RocketMQListener<AgentTaskMessage> {

    private final GitHubWorkflowService workflowService;
    private final TaskRecordRepository taskRepository;
    private final ExecutorService virtualThreadExecutor;

    public AgentPipelineConsumer(GitHubWorkflowService workflowService,
                                 TaskRecordRepository taskRepository,
                                 @Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        this.workflowService = workflowService;
        this.taskRepository = taskRepository;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @Override
    public void onMessage(AgentTaskMessage message) {
        MDC.put("sessionId", message.getTaskId());
        try {
            log.info("[Consumer] 收到任务, 开始异步链路追踪...");
            TaskRecord task = taskRepository.findById(message.getTaskId()).orElse(null);
            if (task == null) {
                log.warn("[Consumer] 任务不存在: {}", message.getTaskId());
                return;
            }

            WorkflowContext ctx = WorkflowContext.builder()
                    .sessionId(message.getTaskId())
                    .repoHint(message.getRepoHint())
                    .userMessage(task.getUserMessage())
                    .build();

            var future = virtualThreadExecutor.submit(() -> {
                workflowService.processTask(message.getTaskId(), ctx);
            });
            future.get();
        } catch (Exception e) {
            log.error("[Consumer] 任务处理异常", e);
            throw new RuntimeException("任务处理失败", e);
        } finally {
            MDC.clear();
        }
    }
}
