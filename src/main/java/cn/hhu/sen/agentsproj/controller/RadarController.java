package cn.hhu.sen.agentsproj.controller;

import cn.hhu.sen.agentsproj.model.RadarCompareView;
import cn.hhu.sen.agentsproj.model.RadarSnapshotView;
import cn.hhu.sen.agentsproj.model.RadarWatchView;
import cn.hhu.sen.agentsproj.service.RadarService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/radar")
public class RadarController {

    private final RadarService radarService;

    public RadarController(RadarService radarService) {
        this.radarService = radarService;
    }

    @PostMapping("/watchlist")
    public ResponseEntity<RadarWatchView> createWatch(@RequestBody CreateWatchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return ResponseEntity.ok(radarService.createWatch(
                request.repoName(), request.displayName(), request.criteria(), request.enabled()));
    }

    @GetMapping("/watchlist")
    public ResponseEntity<List<RadarWatchView>> listWatches() {
        return ResponseEntity.ok(radarService.listWatches());
    }

    @PutMapping("/watchlist/{watchId}")
    public ResponseEntity<RadarWatchView> updateWatch(@PathVariable Long watchId,
                                                       @RequestBody CreateWatchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return ResponseEntity.ok(radarService.updateWatch(
                watchId, request.displayName(), request.criteria(), request.enabled()));
    }

    @DeleteMapping("/watchlist/{watchId}")
    public ResponseEntity<Void> deleteWatch(@PathVariable Long watchId) {
        radarService.deleteWatch(watchId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/watchlist/{watchId}/scan")
    public ResponseEntity<RadarSnapshotView> scan(@PathVariable Long watchId) {
        return ResponseEntity.ok(radarService.scan(watchId));
    }

    @GetMapping("/watchlist/{watchId}/snapshots")
    public ResponseEntity<List<RadarSnapshotView>> snapshots(@PathVariable Long watchId) {
        return ResponseEntity.ok(radarService.listSnapshots(watchId));
    }

    @PostMapping("/compare")
    public ResponseEntity<RadarCompareView> compare(@RequestBody CompareRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return ResponseEntity.ok(radarService.compare(request.watchIds()));
    }

    @PostMapping("/scan-all")
    public ResponseEntity<List<RadarSnapshotView>> scanAll() {
        return ResponseEntity.ok(radarService.scanEnabledWatches());
    }

    public record CreateWatchRequest(
            String repoName,
            String displayName,
            Map<String, Integer> criteria,
            Boolean enabled) {
    }

    public record CompareRequest(List<Long> watchIds) {
    }
}
