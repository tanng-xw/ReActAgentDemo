package com.react.agentdemo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.net.ssl.*;
import java.net.HttpURLConnection;
import java.security.cert.X509Certificate;

/**
 * OpenAI 配置类
 * 配置自定义 RestClient 以记录请求/响应日志
 * 支持企业内网环境禁用 SSL 证书验证
 * 
 * @author Kimi
 */
@Configuration
public class OpenAiConfig {

    @Value("${app.ssl.insecure:false}")
    private boolean sslInsecure;

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
            
            // 如果配置了禁用 SSL 验证，设置自定义 HostnameVerifier
            if (sslInsecure) {
                disableSslVerification();
            }
            
            BufferingClientHttpRequestFactory bufferingFactory = new BufferingClientHttpRequestFactory(factory);
            
            restClientBuilder
                    .requestFactory(bufferingFactory)
                    .requestInterceptor(new LoggingInterceptor());
        };
    }

    /**
     * 禁用 SSL 证书验证
     * 用于企业内网环境（自签名证书或 SSL 拦截代理）
     * 警告：仅在受信任的内网环境使用
     */
    private void disableSslVerification() {
        try {
            // 创建信任所有证书的 TrustManager
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };

            // 安装全信任 TrustManager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // 创建全信任 HostnameVerifier
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            
            System.out.println("[WARNING] SSL certificate verification is disabled.");
            System.out.println("[WARNING] This should only be used in internal enterprise networks.");
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to disable SSL verification", e);
        }
    }
}
