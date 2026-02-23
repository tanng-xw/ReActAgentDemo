package com.kimi.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * WebClient 日志过滤器
 * 用于记录流式模式下的完整 HTTP 请求/响应 JSON 体
 * 
 * @author Kimi
 */
@Component
public class WebClientLoggingFilter implements ExchangeFilterFunction {

    private static final Logger log = LoggerFactory.getLogger(WebClientLoggingFilter.class);
    private static final Logger apiLog = LoggerFactory.getLogger("API_LOG");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final int MAX_BODY_LENGTH = 10000; // 最大记录长度
    private static final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String timestamp = LocalDateTime.now().format(formatter);
        
        // 记录请求
        logRequest(timestamp, requestId, request);
        
        // 执行请求并捕获响应体
        return next.exchange(request)
                .flatMap(response -> {
                    // 检查是否是 SSE 流式响应
                    MediaType contentType = response.headers().contentType().orElse(null);
                    boolean isStreaming = contentType != null && 
                            (contentType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM) ||
                             contentType.toString().contains("stream"));
                    
                    if (isStreaming) {
                        // 流式响应：记录部分样本然后继续
                        return logStreamingResponse(timestamp, requestId, response);
                    } else {
                        // 普通响应：完整记录
                        return logNormalResponse(timestamp, requestId, response);
                    }
                });
    }

    /**
     * 记录流式响应（记录样本然后继续流）
     */
    private Mono<ClientResponse> logStreamingResponse(String timestamp, String requestId, ClientResponse response) {
        // 缓存所有 DataBuffer
        return response.bodyToFlux(DataBuffer.class)
                .map(buffer -> {
                    // 复制 buffer 以便后续使用
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBuffer copy = bufferFactory.wrap(bytes);
                    DataBufferUtils.release(buffer);
                    return new BufferHolder(bytes, copy);
                })
                .collectList()
                .map(holders -> {
                    // 构建日志内容
                    StringBuilder bodyBuilder = new StringBuilder();
                    int totalSize = 0;
                    for (BufferHolder holder : holders) {
                        totalSize += holder.bytes.length;
                        if (bodyBuilder.length() < MAX_BODY_LENGTH) {
                            bodyBuilder.append(new String(holder.bytes, StandardCharsets.UTF_8));
                        }
                    }
                    
                    boolean truncated = totalSize > MAX_BODY_LENGTH;
                    logResponse(timestamp, requestId, response, bodyBuilder.toString(), truncated);
                    
                    // 重建 Flux<DataBuffer> 用于后续处理
                    Flux<DataBuffer> bodyFlux = Flux.fromIterable(holders)
                            .map(h -> h.buffer);
                    
                    // 重建响应
                    return ClientResponse.create(response.statusCode())
                            .headers(headers -> headers.addAll(response.headers().asHttpHeaders()))
                            .body(bodyFlux)
                            .build();
                })
                .onErrorResume(e -> {
                    log.warn("Failed to capture streaming response: {}", e.getMessage());
                    logResponse(timestamp, requestId, response, null, false);
                    return Mono.just(response);
                });
    }

    /**
     * 记录普通响应
     */
    private Mono<ClientResponse> logNormalResponse(String timestamp, String requestId, ClientResponse response) {
        return response.bodyToMono(DataBuffer.class)
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .defaultIfEmpty(new byte[0])
                .map(bytes -> {
                    String body = new String(bytes, StandardCharsets.UTF_8);
                    logResponse(timestamp, requestId, response, body, false);
                    
                    // 重建响应
                    DataBuffer newBuffer = bufferFactory.wrap(bytes);
                    return ClientResponse.create(response.statusCode())
                            .headers(headers -> headers.addAll(response.headers().asHttpHeaders()))
                            .body(Flux.just(newBuffer))
                            .build();
                })
                .onErrorResume(e -> {
                    log.warn("Failed to capture response body: {}", e.getMessage());
                    logResponse(timestamp, requestId, response, null, false);
                    return Mono.just(response);
                });
    }

    /**
     * 记录请求信息
     */
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

    /**
     * 记录响应信息
     */
    private void logResponse(String timestamp, String requestId, ClientResponse response, 
                             String body, boolean truncated) {
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
        
        if (body != null && !body.isEmpty()) {
            sb.append("╠═══════════════════════════════════════════════════════════════════════════════\n");
            sb.append("║ Body (JSON/SSE):\n");
            sb.append(formatJsonBody(body)).append("\n");
            if (truncated) {
                sb.append("║   ... (truncated for logging)\n");
            }
        }
        
        sb.append("╚═══════════════════════════════════════════════════════════════════════════════");
        
        String logMessage = sb.toString();
        log.info(logMessage);
        apiLog.info(logMessage);
    }

    /**
     * 格式化 JSON 体，添加行前缀
     */
    private String formatJsonBody(String json) {
        if (json == null || json.isEmpty()) {
            return "║   (empty)";
        }
        
        String content = json;
        if (content.length() > MAX_BODY_LENGTH) {
            content = content.substring(0, MAX_BODY_LENGTH);
        }
        
        String[] lines = content.split("\n");
        StringBuilder formatted = new StringBuilder();
        for (String line : lines) {
            formatted.append("║   ").append(line).append("\n");
        }
        return formatted.toString().trim();
    }

    /**
     * 内部类：持有原始字节和可重用的 DataBuffer
     */
    private static class BufferHolder {
        final byte[] bytes;
        final DataBuffer buffer;
        
        BufferHolder(byte[] bytes, DataBuffer buffer) {
            this.bytes = bytes;
            this.buffer = buffer;
        }
    }
}
