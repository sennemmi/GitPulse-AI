package cn.hhu.sen.agentsproj.repository;

import cn.hhu.sen.agentsproj.entity.RepoAnalysisRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepoAnalysisRepository extends JpaRepository<RepoAnalysisRecord, String> {
}
