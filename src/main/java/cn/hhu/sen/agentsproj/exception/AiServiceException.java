package cn.hhu.sen.agentsproj.exception;

public class AiServiceException extends BusinessException {

    public AiServiceException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AiServiceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AiServiceException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static AiServiceException parseError(String message, Throwable cause) {
        return new AiServiceException(ErrorCode.AI_PARSE_ERROR,
                "AI 响应解析失败: " + message, cause);
    }

    public static AiServiceException serviceError(String message, Throwable cause) {
        return new AiServiceException(ErrorCode.AI_SERVICE_ERROR,
                "AI 服务调用失败: " + message, cause);
    }

    public static AiServiceException timeout() {
        return new AiServiceException(ErrorCode.AI_TIMEOUT);
    }
}
