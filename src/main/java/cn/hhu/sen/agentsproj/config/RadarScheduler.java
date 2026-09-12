package cn.hhu.sen.agentsproj.config;

import cn.hhu.sen.agentsproj.service.RadarService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.radar", name = "schedule-enabled", havingValue = "true")
public class RadarScheduler {

    private final RadarService radarService;

    public RadarScheduler(RadarService radarService) {
        this.radarService = radarService;
    }

    @Scheduled(cron = "${app.radar.scan-cron:0 0 3 * * *}")
    public void scanEnabledWatches() {
        log.info("[Radar] 开始定时扫描 Watchlist");
        radarService.scanEnabledWatches();
    }
}
