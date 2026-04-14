package cn.hhu.sen.agentsproj.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import cn.hhu.sen.agentsproj.client.ModelScopeImageClient;
import cn.hhu.sen.agentsproj.exception.ErrorCode;
import cn.hhu.sen.agentsproj.exception.ImageGenerationException;

@Slf4j
@Component
public class ImageTools {

    private static final String AGENT_NAME = "ImageTools";

    private final ModelScopeImageClient imageClient;

    public ImageTools(ModelScopeImageClient imageClient) {
        this.imageClient = imageClient;
    }

    @Tool(description = """
            为技术分析报告生成配套可视化图片。
            prompt 应描述图片的视觉内容，建议包含：风格（科技感/简约）、主体内容、配色方案。
            返回图片的访问 URL。
            """)
    public String generateImage(
            @ToolParam(description = "图片生成提示词，描述图片内容和风格") String prompt) {
        log.info("[{}] 执行工具函数: generateImage | prompt: {}", AGENT_NAME, prompt);
        long startTime = System.currentTimeMillis();

        try {
            String imageUrl = imageClient.generate(prompt);
            log.info("[{}] 工具函数 generateImage 执行成功 | 耗时: {}ms", AGENT_NAME, System.currentTimeMillis() - startTime);
            log.info("[{}] 返回内容: {}", AGENT_NAME, imageUrl);
            return imageUrl;
        } catch (ImageGenerationException e) {
            if (e.getErrorCode() == ErrorCode.IMAGE_GENERATION_TIMEOUT) {
                log.warn("[{}] 图片生成超时，返回提示信息", AGENT_NAME);
                String timeoutMsg = "图片生成超时，任务仍在处理中，请稍后通过任务ID查询结果";
                log.info("[{}] 返回内容: {}", AGENT_NAME, timeoutMsg);
                return timeoutMsg;
            }
            log.error("[{}] 工具函数 generateImage 执行失败 | 耗时: {}ms | 错误: {}",
                    AGENT_NAME, System.currentTimeMillis() - startTime, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("[{}] 工具函数 generateImage 执行失败 | 耗时: {}ms | 错误: {}",
                    AGENT_NAME, System.currentTimeMillis() - startTime, e.getMessage(), e);
            throw e;
        }
    }
}
