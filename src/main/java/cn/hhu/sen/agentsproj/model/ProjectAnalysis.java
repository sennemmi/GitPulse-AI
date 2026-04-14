package cn.hhu.sen.agentsproj.model;

import lombok.Data;
import java.util.List;

/**
 * 项目分析结果 - ResearchAgent的输出契约
 * 替代自然语言字符串，消除Agent间的信息损失
 */
@Data
public class ProjectAnalysis {
    private String fullName;          // microsoft/BitNet
    private String oneLiner;          // 一句话定位
    private String whyPopular;        // 爆火原因
    private List<String> highlights;  // 技术亮点（3-5条）
    private String targetAudience;    // 目标人群
    private String quickStart;        // 快速上手命令
    private List<String> tags;        // 推荐标签
}
