package cn.hhu.sen.agentsproj.repository;

import cn.hhu.sen.agentsproj.entity.TaskRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRecordRepository extends JpaRepository<TaskRecord, String> {
}
