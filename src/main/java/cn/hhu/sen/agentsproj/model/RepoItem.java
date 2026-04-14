package cn.hhu.sen.agentsproj.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoItem {
    private String fullName;
    private String description;
    private String language;
    private String stars;
    private String todayStars;
    private String url;
}
