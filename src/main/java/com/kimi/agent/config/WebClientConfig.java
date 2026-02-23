package com.kimi.agent.config;

import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WebClient 配置类
 * 配置自定义 WebClient 以记录 HTTP 请求/响应日志
 * 
 * @author Kimi
 */
@Configuration
public class WebClientConfig {

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
        return builder -> builder.filter(loggingFilter);
    }
}
