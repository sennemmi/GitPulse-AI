package cn.hhu.sen.agentsproj.exception;

/**
 * 图片生成异常
 */
public class ImageGenerationException extends BusinessException {

    public ImageGenerationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ImageGenerationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ImageGenerationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static ImageGenerationException timeout() {
        return new ImageGenerationException(ErrorCode.IMAGE_GENERATION_TIMEOUT);
    }

    public static ImageGenerationException generationFailed(String message) {
        return new ImageGenerationException(ErrorCode.IMAGE_GENERATION_FAILED, message);
    }

    public static ImageGenerationException generationFailed(String message, Throwable cause) {
        return new ImageGenerationException(ErrorCode.IMAGE_GENERATION_FAILED, message, cause);
    }
}
