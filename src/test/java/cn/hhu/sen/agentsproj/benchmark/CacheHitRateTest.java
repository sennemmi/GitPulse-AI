package cn.hhu.sen.agentsproj.benchmark;

import cn.hhu.sen.agentsproj.repository.RepoAnalysisRepository;
import cn.hhu.sen.agentsproj.service.AnalysisCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CacheHitRateTest {

    @Autowired
    private AnalysisCacheService cacheService;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RepoAnalysisRepository analysisRepository;

    private static final List<String> HOT_REPOS = List.of(
            "microsoft/vscode", "facebook/react", "golang/go",
            "torvalds/linux", "kubernetes/kubernetes"
    );
    private static final List<String> LONG_TAIL_REPOS = List.of(
            "apache/kafka", "elastic/elasticsearch", "redis/redis",
            "nginx/nginx", "grafana/grafana",
            "hashicorp/terraform", "prometheus/prometheus", "istio/istio",
            "grpc/grpc", "etcd-io/etcd",
            "helm/helm", "argoproj/argo-cd", "cilium/cilium",
            "open-telemetry/opentelemetry-java", "jaegertracing/jaeger"
    );

    @BeforeEach
    void clearCache() {
        redissonClient.getMapCache("repo:analysis:cache").clear();
        redissonClient.getBucket("repo:bloom:filter").delete();
        analysisRepository.deleteAll();
        System.out.println("=== 缓存已清空，准备冷启动 ===");
    }

    @Test
    void measureCacheHitRate() {
        System.out.println("=== 第一轮：冷启动 ===");
        Stream.concat(HOT_REPOS.stream(), LONG_TAIL_REPOS.stream())
              .forEach(repo -> {
                  try { cacheService.getOrAnalyze(repo); }
                  catch (Exception e) { System.err.println("跳过: " + repo); }
              });

        System.out.printf("第一轮命中率: %.1f%% (预期≈0%%)%n", cacheService.getHitRate());

        System.out.println("\n=== 第二轮：模拟真实流量（100次请求）===");
        Random random = new Random(42);
        int total = 100;

        for (int i = 0; i < total; i++) {
            String repo = random.nextDouble() < 0.7
                    ? HOT_REPOS.get(random.nextInt(HOT_REPOS.size()))
                    : LONG_TAIL_REPOS.get(random.nextInt(LONG_TAIL_REPOS.size()));
            try { cacheService.getOrAnalyze(repo); }
            catch (Exception ignored) {}
        }

        double hitRate = cacheService.getHitRate();
        System.out.printf("%n=== 缓存效果统计 ===%n");
        System.out.printf("总请求数: %d%n", cacheService.getHitCount() + cacheService.getMissCount());
        System.out.printf("缓存命中: %d 次%n", cacheService.getHitCount());
        System.out.printf("LLM调用:  %d 次 (每次约0.05元)%n", cacheService.getMissCount());
        System.out.printf("缓存命中率: %.1f%%%n", hitRate);
        System.out.printf("节省LLM调用: %.1f%%%n", hitRate);

        assertThat(hitRate).isGreaterThanOrEqualTo(40.0);
    }
}
