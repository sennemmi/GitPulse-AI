package cn.hhu.sen.agentsproj.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkflowContext {

    private String userMessage;
    private String sessionId;

    private String repoHint;

    private List<RepoItem> trendingRepos;
    private RepoItem targetRepo;

    private ProjectAnalysis analysis;

    private TechReport techReport;

    private String imageUrl;

    private String publishResult;
}
