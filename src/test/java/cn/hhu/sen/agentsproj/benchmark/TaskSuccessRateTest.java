package cn.hhu.sen.agentsproj.benchmark;

import cn.hhu.sen.agentsproj.agent.ResearchAgent;
import cn.hhu.sen.agentsproj.entity.TaskRecord;
import cn.hhu.sen.agentsproj.model.WorkflowContext;
import cn.hhu.sen.agentsproj.repository.RepoAnalysisRepository;
import cn.hhu.sen.agentsproj.repository.TaskRecordRepository;
import cn.hhu.sen.agentsproj.service.AnalysisCacheService;
import cn.hhu.sen.agentsproj.service.GitHubWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class TaskSuccessRateTest {

    @Autowired
    private GitHubWorkflowService workflowService;
    @Autowired
    private TaskRecordRepository taskRepository;
    @Autowired
    private AnalysisCacheService cacheService;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RepoAnalysisRepository analysisRepository;

    @SpyBean
    private ResearchAgent researchAgent;

    private static final List<String> REPOS = List.of(
            "microsoft/vscode", "facebook/react", "golang/go",
            "torvalds/linux", "kubernetes/kubernetes",
            "apache/kafka", "elastic/elasticsearch", "redis/redis",
            "nginx/nginx", "grafana/grafana"
    );

    @BeforeEach
    void warmUpCache() {
        redissonClient.getMapCache("repo:analysis:cache").clear();
        redissonClient.getBucket("repo:bloom:filter").delete();
        analysisRepository.deleteAll();

        System.out.println("=== 预热缓存 ===");
        for (String repo : REPOS) {
            try {
                cacheService.getOrAnalyze(repo);
            } catch (Exception e) {
                System.err.println("预热失败: " + repo);
            }
        }
        System.out.println("=== 缓存预热完成 ===");
    }

    @Test
    void measureSuccessRateUnderFault() throws Exception {
        int totalTasks = 20;
        AtomicInteger callCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            int count = callCount.incrementAndGet();
            if (count % 3 == 0) {
                throw new RuntimeException("模拟LLM 503 超时");
            }
            return invocation.callRealMethod();
        }).when(researchAgent).run(anyString());

        List<String> taskIds = new ArrayList<>();

        for (int i = 0; i < totalTasks; i++) {
            String repo = REPOS.get(i % REPOS.size());
            String sessionId = UUID.randomUUID().toString().replace("-", "");
            WorkflowContext ctx = WorkflowContext.builder()
                    .userMessage("分析 " + repo)
                    .sessionId(sessionId)
                    .repoHint(repo)
                    .build();
            taskIds.add(workflowService.submitTask(ctx));
        }

        System.out.println("等待任务完成（含重试）...");
        Thread.sleep(Duration.ofMinutes(3));

        long successCount = 0, failedCount = 0;
        for (String taskId : taskIds) {
            TaskRecord task = taskRepository.findById(taskId).orElseThrow();
            if ("SUCCESS".equals(task.getStatus())) successCount++;
            else failedCount++;
        }

        double successRate = successCount * 100.0 / totalTasks;
        System.out.printf("%n=== 成功率统计 ===%n");
        System.out.printf("总任务数:   %d%n", totalTasks);
        System.out.printf("成功:       %d%n", successCount);
        System.out.printf("失败:       %d%n", failedCount);
        System.out.printf("注入故障率: 33%%%n");
        System.out.printf("最终成功率: %.1f%%%n", successRate);

        assertThat(successRate).isGreaterThanOrEqualTo(95.0);
    }
}
