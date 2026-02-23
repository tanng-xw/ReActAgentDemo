package com.kimi.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

/**
 * WebClient 日志过滤器
 * 用于记录流式模式下的 HTTP 请求/响应
 * 
 * @author Kimi
 */
@Component
public class WebClientLoggingFilter implements ExchangeFilterFunction {

    private static final Logger log = LoggerFactory.getLogger(WebClientLoggingFilter.class);
    private static final Logger apiLog = LoggerFactory.getLogger("API_LOG");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String timestamp = LocalDateTime.now().format(formatter);
        
        // 记录请求
        logRequest(timestamp, requestId, request);
        
        // 执行请求并记录响应
        return next.exchange(request)
                .doOnNext(response -> {
                    logResponse(timestamp, requestId, response);
                });
    }

    private void logRequest(String timestamp, String requestId, ClientRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("║ ").append(timestamp).append(" | Request ID: ").append(requestId).append("\n");
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("║ [WEBCLIENT REQUEST] ").append(request.method()).append(" ").append(request.url()).append("\n");
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("║ Headers:\n");
        request.headers().forEach((name, values) -> {
            // 隐藏敏感信息（如 Authorization）
            String value = name.equalsIgnoreCase("Authorization") 
                    ? "Bearer ***" 
                    : String.join(", ", values);
            sb.append("║   ").append(name).append(": ").append(value).append("\n");
        });
        sb.append("╚═══════════════════════════════════════════════════════════════════════════════");
        
        String logMessage = sb.toString();
        log.info(logMessage);
        apiLog.info(logMessage);
    }

    private void logResponse(String timestamp, String requestId, ClientResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("║ ").append(timestamp).append(" | Request ID: ").append(requestId).append("\n");
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("║ [WEBCLIENT RESPONSE] Status: ").append(response.statusCode()).append("\n");
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("║ Headers:\n");
        response.headers().asHttpHeaders().forEach((name, values) -> {
            sb.append("║   ").append(name).append(": ").append(String.join(", ", values)).append("\n");
        });
        sb.append("╚═══════════════════════════════════════════════════════════════════════════════");
        
        String logMessage = sb.toString();
        log.info(logMessage);
        apiLog.info(logMessage);
    }
}
