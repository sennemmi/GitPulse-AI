package cn.hhu.sen.agentsproj.repository;

import cn.hhu.sen.agentsproj.entity.RadarSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RadarSnapshotRepository extends JpaRepository<RadarSnapshot, Long> {

    List<RadarSnapshot> findByWatchIdOrderByCreatedTimeDesc(Long watchId);

    Optional<RadarSnapshot> findTopByWatchIdOrderByCreatedTimeDesc(Long watchId);

    Optional<RadarSnapshot> findFirstByWatchIdAndIdNotOrderByCreatedTimeDesc(Long watchId, Long id);

    void deleteByWatchId(Long watchId);
}
