package cn.hhu.sen.agentsproj.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    GITHUB_API_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "GITHUB_API_ERROR", "GitHub API 调用失败"),
    GITHUB_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "GITHUB_RATE_LIMIT", "GitHub API 限流，请稍后重试"),
    GITHUB_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "GITHUB_TIMEOUT", "GitHub API 请求超时"),

    AI_PARSE_ERROR(HttpStatus.UNPROCESSABLE_ENTITY, "AI_PARSE_ERROR", "AI 响应解析失败"),
    AI_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_ERROR", "AI 服务调用失败"),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "AI 服务请求超时"),

    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "请求参数无效"),

    IMAGE_GENERATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_GENERATION_FAILED", "图片生成失败"),
    IMAGE_GENERATION_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "IMAGE_GENERATION_TIMEOUT", "图片生成超时，请稍后重试"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器内部错误");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
