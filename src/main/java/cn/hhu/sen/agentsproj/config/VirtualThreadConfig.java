package cn.hhu.sen.agentsproj.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class VirtualThreadConfig {

    @Bean("virtualThreadExecutor")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public TomcatProtocolHandlerCustomizer<?> tomcatVirtualThreads() {
        return handler -> handler.setExecutor(
                Executors.newVirtualThreadPerTaskExecutor());
    }
}
