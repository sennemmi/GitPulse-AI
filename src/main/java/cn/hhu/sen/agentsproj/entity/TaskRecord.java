package cn.hhu.sen.agentsproj.entity;

import cn.hhu.sen.agentsproj.model.TaskStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "task_record")
public class TaskRecord {
    @Id
    private String taskId;
    private String intent;
    private String repoHint;
    private String status;

    @Column(columnDefinition = "TEXT")
    private String resultData;

    @Column(columnDefinition = "TEXT")
    private String errorMsg;

    @Column(columnDefinition = "TEXT")
    private String userMessage;

    @Column(updatable = false)
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    public TaskStatus getTaskStatus() {
        return status != null ? TaskStatus.valueOf(status) : null;
    }

    public void transitionTo(TaskStatus next) {
        TaskStatus current = getTaskStatus();
        if (current == null) {
            throw new IllegalStateException("当前状态为空，无法转换");
        }
        TaskStatus validated = current.transition(next);
        this.status = validated.name();
    }
}
