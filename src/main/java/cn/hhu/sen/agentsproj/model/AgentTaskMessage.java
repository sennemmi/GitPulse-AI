package cn.hhu.sen.agentsproj.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentTaskMessage {
    private String taskId;
    private String intent;
    private String repoHint;
}
