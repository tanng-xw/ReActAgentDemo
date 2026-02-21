package com.kimi.agent.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * OpenAI 配置类
 * 配置自定义 RestClient 以记录请求/响应日志
 * 
 * @author Kimi
 */
@Configuration
public class OpenAiConfig {

    /**
     * 配置 RestClient 自定义器，添加日志拦截器
     * 这个自定义器会被 Spring Boot 使用来创建 RestClient.Builder
     * Spring AI 会使用这个 RestClient.Builder 来创建 OpenAiApi
     * 
     * @return RestClient 自定义器
     */
    @Bean
    public RestClientCustomizer restClientCustomizer() {
        return restClientBuilder -> {
            // 创建支持缓冲的请求工厂（允许响应体被多次读取）
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(30000);
            factory.setReadTimeout(60000);
            BufferingClientHttpRequestFactory bufferingFactory = new BufferingClientHttpRequestFactory(factory);
            
            restClientBuilder
                    .requestFactory(bufferingFactory)
                    .requestInterceptor(new LoggingInterceptor());
        };
    }
}
