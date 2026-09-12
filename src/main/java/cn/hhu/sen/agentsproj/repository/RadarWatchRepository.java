package cn.hhu.sen.agentsproj.repository;

import cn.hhu.sen.agentsproj.entity.RadarWatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RadarWatchRepository extends JpaRepository<RadarWatch, Long> {

    Optional<RadarWatch> findByRepoName(String repoName);

    List<RadarWatch> findByEnabledTrueOrderByUpdatedTimeDesc();
}
