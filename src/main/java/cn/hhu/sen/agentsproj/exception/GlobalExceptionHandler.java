package cn.hhu.sen.agentsproj.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("业务异常: [{}] {}", errorCode.getCode(), e.getMessage());

        ErrorResponse response = new ErrorResponse(
                errorCode.getCode(),
                e.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    @ExceptionHandler(WebClientResponseException.TooManyRequests.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(WebClientResponseException.TooManyRequests e) {
        log.warn("GitHub API 限流: {}", e.getMessage());
        ErrorResponse response = new ErrorResponse(
                ErrorCode.GITHUB_RATE_LIMIT.getCode(),
                ErrorCode.GITHUB_RATE_LIMIT.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(ErrorCode.GITHUB_RATE_LIMIT.getHttpStatus()).body(response);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(TimeoutException e) {
        log.warn("请求超时: {}", e.getMessage());
        ErrorResponse response = new ErrorResponse(
                ErrorCode.GITHUB_TIMEOUT.getCode(),
                "请求超时，请稍后重试",
                LocalDateTime.now()
        );
        return ResponseEntity.status(ErrorCode.GITHUB_TIMEOUT.getHttpStatus()).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        ErrorResponse response = new ErrorResponse(
                ErrorCode.INVALID_PARAMETER.getCode(),
                e.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.getHttpStatus()).body(response);
    }

    @ExceptionHandler(ImageGenerationException.class)
    public ResponseEntity<ErrorResponse> handleImageGenerationException(ImageGenerationException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("图片生成异常: [{}] {}", errorCode.getCode(), e.getMessage());

        ErrorResponse response = new ErrorResponse(
                errorCode.getCode(),
                e.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        ErrorResponse response = new ErrorResponse(
                ErrorCode.INTERNAL_ERROR.getCode(),
                "服务器内部错误，请稍后重试",
                LocalDateTime.now()
        );
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus()).body(response);
    }

    public record ErrorResponse(String code, String message, LocalDateTime timestamp) {}
}
