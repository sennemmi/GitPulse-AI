package cn.hhu.sen.agentsproj.exception;

import lombok.Getter;

@Getter
public class NonRetryableException extends RuntimeException {

    private final String errorCode;

    public NonRetryableException(String message) {
        super(message);
        this.errorCode = "NON_RETRYABLE";
    }

    public NonRetryableException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public NonRetryableException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public static NonRetryableException githubNotFound(String repoName) {
        return new NonRetryableException("GITHUB_NOT_FOUND", "GitHub 仓库不存在: " + repoName);
    }

    public static NonRetryableException promptViolation(String message) {
        return new NonRetryableException("PROMPT_VIOLATION", "Prompt 违规: " + message);
    }

    public static NonRetryableException invalidRequest(String message) {
        return new NonRetryableException("INVALID_REQUEST", "请求无效: " + message);
    }
}
