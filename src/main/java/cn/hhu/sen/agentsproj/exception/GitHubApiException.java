package cn.hhu.sen.agentsproj.exception;

public class GitHubApiException extends BusinessException {

    public GitHubApiException(ErrorCode errorCode) {
        super(errorCode);
    }

    public GitHubApiException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public GitHubApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static GitHubApiException rateLimit() {
        return new GitHubApiException(ErrorCode.GITHUB_RATE_LIMIT);
    }

    public static GitHubApiException timeout() {
        return new GitHubApiException(ErrorCode.GITHUB_TIMEOUT);
    }

    public static GitHubApiException apiError(String message, Throwable cause) {
        return new GitHubApiException(ErrorCode.GITHUB_API_ERROR, message, cause);
    }
}
