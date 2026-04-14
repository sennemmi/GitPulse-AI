package cn.hhu.sen.agentsproj.client;

import java.time.Duration;
import java.util.Map;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import cn.hhu.sen.agentsproj.exception.GitHubApiException;
import cn.hhu.sen.agentsproj.exception.NonRetryableException;
import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.LoopResources;
import reactor.netty.transport.ProxyProvider;

@Slf4j
@Component
public class GitHubApiClient {

    private final WebClient webClient;

    public GitHubApiClient(WebClient.Builder builder,
                           @Value("${github.token:}") String token,
                           @Value("${proxy.enabled:false}") boolean proxyEnabled,
                           @Value("${proxy.host:}") String proxyHost,
                           @Value("${proxy.port:0}") int proxyPort) {

        WebClient.Builder webClientBuilder = builder
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", token.isEmpty() ? "" : "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github.v3+json");

        HttpClient httpClient;
        if (proxyEnabled && !proxyHost.isEmpty() && proxyPort > 0) {
            log.info("[GitHubApiClient] 启用代理 | {}:{}", proxyHost, proxyPort);
            httpClient = HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .responseTimeout(Duration.ofSeconds(30))
                    .runOn(LoopResources.create("github-http", 4, true))
                    .proxy(proxy -> proxy
                            .type(ProxyProvider.Proxy.HTTP)
                            .host(proxyHost)
                            .port(proxyPort));
        } else {
            log.info("[GitHubApiClient] 未启用代理");
            httpClient = HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .responseTimeout(Duration.ofSeconds(30))
                    .runOn(LoopResources.create("github-http", 4, true));
        }
        webClientBuilder.clientConnector(new ReactorClientHttpConnector(httpClient));

        this.webClient = webClientBuilder.build();
    }

    public String getReadme(String owner, String repo) {
        log.debug("[GitHubApiClient] 获取 README: {}/{}", owner, repo);
        try {
            String readme = webClient.get()
                    .uri("/repos/{owner}/{repo}/readme", owner, repo)
                    .header("Accept", "application/vnd.github.v3.raw")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.debug("[GitHubApiClient] 成功获取 README: {}/{} | 长度: {} 字符",
                    owner, repo, readme != null ? readme.length() : 0);
            return readme;
        } catch (WebClientResponseException.NotFound e) {
            throw NonRetryableException.githubNotFound(owner + "/" + repo);
        } catch (WebClientResponseException.TooManyRequests e) {
            throw GitHubApiException.rateLimit();
        } catch (WebClientResponseException e) {
            throw GitHubApiException.apiError("获取 README 失败: " + e.getStatusCode(), e);
        }
    }

    public String getFileTree(String owner, String repo) {
        log.debug("[GitHubApiClient] 获取文件树: {}/{}", owner, repo);
        try {
            String fileTree = webClient.get()
                    .uri("/repos/{owner}/{repo}/contents", owner, repo)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.debug("[GitHubApiClient] 成功获取文件树: {}/{} | 长度: {} 字符",
                    owner, repo, fileTree != null ? fileTree.length() : 0);
            return fileTree;
        } catch (WebClientResponseException.NotFound e) {
            throw NonRetryableException.githubNotFound(owner + "/" + repo);
        } catch (WebClientResponseException.TooManyRequests e) {
            throw GitHubApiException.rateLimit();
        } catch (WebClientResponseException e) {
            throw GitHubApiException.apiError("获取文件树失败: " + e.getStatusCode(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getRepoMeta(String owner, String repo) {
        log.debug("[GitHubApiClient] 获取仓库元信息: {}/{}", owner, repo);
        try {
            Map<String, Object> meta = webClient.get()
                    .uri("/repos/{owner}/{repo}", owner, repo)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            log.debug("[GitHubApiClient] 成功获取仓库元信息: {}/{}", owner, repo);
            return meta;
        } catch (WebClientResponseException.NotFound e) {
            throw NonRetryableException.githubNotFound(owner + "/" + repo);
        } catch (WebClientResponseException.TooManyRequests e) {
            throw GitHubApiException.rateLimit();
        } catch (WebClientResponseException e) {
            throw GitHubApiException.apiError("获取仓库元信息失败: " + e.getStatusCode(), e);
        }
    }

    public int getContributorCount(String owner, String repo) {
        log.debug("[GitHubApiClient] 获取贡献者数量: {}/{}", owner, repo);
        try {
            Object[] contributors = webClient.get()
                    .uri("/repos/{owner}/{repo}/contributors?per_page=1&anon=true", owner, repo)
                    .retrieve()
                    .bodyToMono(Object[].class)
                    .block();
            int count = contributors != null ? contributors.length : 0;
            log.debug("[GitHubApiClient] 成功获取贡献者数量: {}/{} | count: {}", owner, repo, count);
            return count;
        } catch (WebClientResponseException.NotFound e) {
            throw NonRetryableException.githubNotFound(owner + "/" + repo);
        } catch (WebClientResponseException.TooManyRequests e) {
            throw GitHubApiException.rateLimit();
        } catch (WebClientResponseException.Forbidden e) {
            log.warn("[GitHubApiClient] 贡献者接口被限制(仓库过大): {}/{}, 降级返回 -1", owner, repo);
            return -1;
        } catch (WebClientResponseException e) {
            throw GitHubApiException.apiError("获取贡献者数量失败: " + e.getStatusCode(), e);
        }
    }
}
