package cn.hhu.sen.agentsproj.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cn.hhu.sen.agentsproj.client.GitHubApiClient;
import cn.hhu.sen.agentsproj.exception.GitHubApiException;
import cn.hhu.sen.agentsproj.model.RepoItem;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GitHubFetcherService {

    private static final String TRENDING_URL = "https://github.com/trending";
    private static final int MAX_RETRIES = 3;
    private static final int BASE_TIMEOUT = 10000; // 10秒基础超时
    private static final Random RANDOM = new Random();

    private final GitHubApiClient apiClient;
    private final boolean proxyEnabled;
    private final String proxyHost;
    private final int proxyPort;

    public GitHubFetcherService(GitHubApiClient apiClient,
                                @Value("${proxy.enabled:false}") boolean proxyEnabled,
                                @Value("${proxy.host:}") String proxyHost,
                                @Value("${proxy.port:0}") int proxyPort) {
        this.apiClient = apiClient;
        this.proxyEnabled = proxyEnabled;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;

        if (proxyEnabled && !proxyHost.isEmpty() && proxyPort > 0) {
            log.info("[GitHubFetcherService] 启用代理 | {}:{}", proxyHost, proxyPort);
        } else {
            log.info("[GitHubFetcherService] 未启用代理");
        }
    }

    public List<RepoItem> fetchTrending(String language, String since) throws IOException {
        String url = TRENDING_URL + (language != null && !language.isEmpty() ? "/" + language : "") + "?since=" + since;

        log.info("[GitHubTrendAgent] 开始获取 GitHub Trending | URL: {}", url);

        // 带重试的请求
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.debug("[GitHubTrendAgent] 第 {} 次尝试请求 GitHub...", attempt);

                // 随机延迟，避免请求过快
                if (attempt > 1) {
                    int delay = 1000 + RANDOM.nextInt(2000); // 1-3秒随机延迟
                    log.debug("[GitHubTrendAgent] 等待 {}ms 后重试...", delay);
                    Thread.sleep(delay);
                }

                // 构建 Jsoup 连接
                Connection connection = Jsoup.connect(url)
                        .userAgent(getRandomUserAgent())
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .header("Accept-Encoding", "gzip, deflate, br")
                        .header("Connection", "keep-alive")
                        .header("Upgrade-Insecure-Requests", "1")
                        .header("Sec-Fetch-Dest", "document")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Site", "none")
                        .header("Cache-Control", "max-age=0")
                        .timeout(BASE_TIMEOUT + (attempt - 1) * 5000) // 递增超时时间
                        .followRedirects(true);

                // 如果启用了代理，配置代理
                if (proxyEnabled && !proxyHost.isEmpty() && proxyPort > 0) {
                    connection.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
                }

                Document doc = connection.get();

                Elements rows = doc.select("article.Box-row");
                log.info("[GitHubTrendAgent] 成功获取 GitHub Trending | 找到 {} 个项目", rows.size());

                return parseRepoItems(rows);

            } catch (HttpStatusException e) {
                log.warn("[GitHubTrendAgent] 第 {} 次请求失败 | HTTP状态: {} | URL: {}",
                        attempt, e.getStatusCode(), url);

                if (e.getStatusCode() == 502 && attempt < MAX_RETRIES) {
                    continue; // 502错误时重试
                }
                if (e.getStatusCode() == 429) {
                    throw GitHubApiException.rateLimit();
                }
                throw new IOException("GitHub 请求失败: " + e.getStatusCode(), e);

            } catch (SocketTimeoutException e) {
                log.warn("[GitHubTrendAgent] 第 {} 次请求超时", attempt);
                if (attempt < MAX_RETRIES) {
                    continue;
                }
                throw GitHubApiException.timeout();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("请求被中断", e);
            }
        }

        throw new IOException("GitHub 请求失败，已重试 " + MAX_RETRIES + " 次");
    }

    private List<RepoItem> parseRepoItems(Elements rows) {
        List<RepoItem> items = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (Element row : rows) {
            try {
                String fullName = row.select("h2 a").text().replace(" ", "").trim();
                if (fullName.isEmpty()) {
                    continue;
                }

                String description = row.select("p.col-9").text();
                String lang = row.select("[itemprop=programmingLanguage]").text();

                // 获取 stars - 尝试多种选择器
                String stars = "";
                Elements starElements = row.select("a.Link--muted");
                if (starElements.isEmpty()) {
                    starElements = row.select("a[href$=stargazers]");
                }
                if (!starElements.isEmpty()) {
                    stars = starElements.first().text().trim();
                }

                // 获取今日新增 Stars
                String addedStars = "";
                Elements addedElements = row.select("span.d-inline-block.float-sm-right");
                if (addedElements.isEmpty()) {
                    addedElements = row.select("span[data-view-component=true]");
                }
                if (!addedElements.isEmpty()) {
                    addedStars = addedElements.first().text().trim();
                }

                items.add(RepoItem.builder()
                        .fullName(fullName)
                        .description(description)
                        .language(lang)
                        .stars(stars)
                        .todayStars(addedStars)
                        .url("https://github.com/" + fullName)
                        .build());
                successCount++;

            } catch (Exception e) {
                failCount++;
                log.debug("[GitHubTrendAgent] 解析行失败: {}", e.getMessage());
            }
        }

        log.info("[GitHubTrendAgent] 解析完成 | 成功: {} | 失败: {}", successCount, failCount);
        return items;
    }

    private String getRandomUserAgent() {
        String[] userAgents = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        };
        return userAgents[RANDOM.nextInt(userAgents.length)];
    }

    /**
     * 直接通过 GitHub API 构建 RepoItem
     * 不再查询热榜，避免不必要的网络请求
     */
    public RepoItem buildRepoItem(String fullName) {
        log.info("[GitHubTrendAgent] 构建 RepoItem: {}", fullName);

        String[] parts = fullName.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid fullName format. Expected: owner/repo");
        }
        String owner = parts[0];
        String repo = parts[1];

        // 直接使用 apiClient 获取 README
        String readme = apiClient.getReadme(owner, repo);
        String description = "";

        if (readme != null && !readme.isEmpty()) {
            String[] lines = readme.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("!")) {
                    description = line;
                    break;
                }
            }
        }

        RepoItem item = RepoItem.builder()
                .fullName(fullName)
                .description(description)
                .language("")
                .stars("")
                .todayStars("")
                .url("https://github.com/" + fullName)
                .build();
        log.info("[GitHubTrendAgent] 已构建 RepoItem: {}", fullName);

        return item;
    }

}
