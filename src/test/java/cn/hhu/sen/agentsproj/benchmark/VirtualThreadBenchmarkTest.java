package cn.hhu.sen.agentsproj.benchmark;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualThreadBenchmarkTest {

    private static final int CONCURRENT_TASKS = 100;

    @Test
    void compareVirtualVsPlatformThreads() throws Exception {
        Runnable ioTask = () -> {
            try {
                try (var inner = Executors.newVirtualThreadPerTaskExecutor()) {
                    var f1 = inner.submit(() -> { Thread.sleep(500); return 1; });
                    var f2 = inner.submit(() -> { Thread.sleep(500); return 2; });
                    var f3 = inner.submit(() -> { Thread.sleep(500); return 3; });
                    var f4 = inner.submit(() -> { Thread.sleep(500); return 4; });
                    f1.get(); f2.get(); f3.get(); f4.get();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        System.out.println("=== 指标4：虚拟线程 vs 平台线程 A/B 对比 ===");
        System.out.printf("并发任务数: %d%n%n", CONCURRENT_TASKS);

        long platformTime = runWithExecutor(
                Executors.newFixedThreadPool(20), ioTask, CONCURRENT_TASKS, "平台线程（固定20线程池）");

        long virtualTime = runWithExecutor(
                Executors.newVirtualThreadPerTaskExecutor(), ioTask, CONCURRENT_TASKS, "虚拟线程");

        double speedup = (double) platformTime / virtualTime;
        System.out.printf("%n=== A/B 对比结论 ===%n");
        System.out.printf("平台线程总耗时: %6d ms%n", platformTime);
        System.out.printf("虚拟线程总耗时: %6d ms%n", virtualTime);
        System.out.printf("吞吐量提升:     %.2fx%n", speedup);
        System.out.printf("结论: 在 %d 并发I/O密集任务下，虚拟线程吞吐量提升 %.1f 倍%n",
                CONCURRENT_TASKS, speedup);

        assertThat(speedup).isGreaterThan(2.0);
    }

    private long runWithExecutor(ExecutorService executor, Runnable task,
                                  int count, String label) throws Exception {
        CountDownLatch latch = new CountDownLatch(count);
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            executor.submit(() -> {
                try { task.run(); }
                finally { latch.countDown(); }
            });
        }
        latch.await();
        long elapsed = System.currentTimeMillis() - start;
        executor.shutdown();
        System.out.printf("[%s] 完成 %d 任务，耗时 %d ms%n", label, count, elapsed);
        return elapsed;
    }
}
