package cn.hhu.sen.agentsproj.service;

import cn.hhu.sen.agentsproj.agent.ResearchAgent;
import cn.hhu.sen.agentsproj.entity.RepoAnalysisRecord;
import cn.hhu.sen.agentsproj.model.ProjectAnalysis;
import cn.hhu.sen.agentsproj.repository.RepoAnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class AnalysisCacheService {

    private final RedissonClient redissonClient;
    private final RepoAnalysisRepository analysisRepository;
    private final ResearchAgent researchAgent;
    private final ObjectMapper objectMapper;

    private RBloomFilter<String> bloomFilter;
    private RMapCache<String, String> redisCache;

    private final AtomicLong cacheHitCount = new AtomicLong(0);
    private final AtomicLong cacheMissCount = new AtomicLong(0);

    public AnalysisCacheService(RedissonClient redissonClient,
                                RepoAnalysisRepository analysisRepository,
                                ResearchAgent researchAgent,
                                ObjectMapper objectMapper) {
        this.redissonClient = redissonClient;
        this.analysisRepository = analysisRepository;
        this.researchAgent = researchAgent;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        bloomFilter = redissonClient.getBloomFilter("repo:bloom:filter");
        bloomFilter.tryInit(100000L, 0.01);

        redisCache = redissonClient.getMapCache("repo:analysis:cache");
    }

    public ProjectAnalysis getOrAnalyze(String repoName) {
        if (bloomFilter.contains(repoName)) {
            String cachedJson = redisCache.get(repoName);
            if (cachedJson != null) {
                log.info("[Cache] 命中 Redis 缓存: {}", repoName);
                cacheHitCount.incrementAndGet();
                return parseJson(cachedJson);
            }
        }

        RLock lock = redissonClient.getLock("lock:analyze:" + repoName);
        try {
            boolean isLocked = lock.tryLock(30, -1, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new RuntimeException("系统繁忙，其他任务正在分析该项目，请稍后再试");
            }

            String dclJson = redisCache.get(repoName);
            if (dclJson != null) {
                cacheHitCount.incrementAndGet();
                return parseJson(dclJson);
            }
            var dbRecord = analysisRepository.findById(repoName);
            if (dbRecord.isPresent()) {
                cacheHitCount.incrementAndGet();
                redisCache.put(repoName, dbRecord.get().getAnalysisJson(), 7, TimeUnit.DAYS);
                return parseJson(dbRecord.get().getAnalysisJson());
            }

            log.info("[Analyze] 缓存未命中，开始调用 LLM 分析: {}", repoName);
            cacheMissCount.incrementAndGet();
            ProjectAnalysis analysis = researchAgent.run(repoName);
            String resultJson = objectMapper.writeValueAsString(analysis);

            RepoAnalysisRecord record = new RepoAnalysisRecord();
            record.setRepoName(repoName);
            record.setAnalysisJson(resultJson);
            analysisRepository.save(record);

            redisCache.put(repoName, resultJson, 7, TimeUnit.DAYS);

            bloomFilter.add(repoName);

            return analysis;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断", e);
        } catch (Exception e) {
            log.error("分析执行异常", e);
            throw new RuntimeException("分析执行失败: " + e.getMessage(), e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public double getHitRate() {
        long total = cacheHitCount.get() + cacheMissCount.get();
        return total == 0 ? 0 : (cacheHitCount.get() * 100.0 / total);
    }

    public long getHitCount() { return cacheHitCount.get(); }
    public long getMissCount() { return cacheMissCount.get(); }

    private ProjectAnalysis parseJson(String json) {
        try {
            return objectMapper.readValue(json, ProjectAnalysis.class);
        } catch (Exception e) {
            throw new RuntimeException("JSON解析失败", e);
        }
    }
}
