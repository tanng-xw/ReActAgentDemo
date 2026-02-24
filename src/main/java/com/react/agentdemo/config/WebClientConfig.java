package com.react.agentdemo.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

/**
 * WebClient 配置类
 * 配置自定义 WebClient 以记录 HTTP 请求/响应日志
 * 支持企业内网禁用 SSL 证书验证
 * 
 * @author Kimi
 */
@Configuration
public class WebClientConfig {

    @Value("${app.ssl.insecure:false}")
    private boolean sslInsecure;

    /**
     * WebClient 自定义器，添加日志过滤器
     * 这个自定义器会被 Spring Boot 自动应用到所有 WebClient.Builder
     * Spring AI 流式模式会使用这个配置
     * 
     * @param loggingFilter 日志过滤器
     * @return WebClient 自定义器
     */
    @Bean
    public WebClientCustomizer webClientLoggingCustomizer(WebClientLoggingFilter loggingFilter) {
        return builder -> {
            builder.filter(loggingFilter);
            
            // 如果配置了禁用 SSL 验证，配置自定义 HttpClient
            if (sslInsecure) {
                try {
                    // 创建不验证证书的 SSL 上下文
                    SslContext sslContext = SslContextBuilder
                            .forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .build();

                    // 配置 HttpClient 使用自定义 SSL 上下文
                    HttpClient httpClient = HttpClient.create()
                            .secure(sslSpec -> sslSpec.sslContext(sslContext));

                    builder.clientConnector(new ReactorClientHttpConnector(httpClient));
                    
                    System.out.println("[WARNING] WebClient SSL certificate verification is disabled.");
                    System.out.println("[WARNING] This should only be used in internal enterprise networks.");
                } catch (Exception e) {
                    throw new RuntimeException("Failed to configure insecure WebClient", e);
                }
            }
        };
    }
}
