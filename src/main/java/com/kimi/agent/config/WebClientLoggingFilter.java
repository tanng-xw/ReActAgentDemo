package com.kimi.agent.config;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
    private static final int MAX_LOG_LENGTH = 5000; // 日志最大长度
    private static final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String timestamp = LocalDateTime.now().format(formatter);
        
        // 创建请求体捕获器
        RequestBodyCapture bodyCapture = new RequestBodyCapture();
        
        // 包装请求以捕获请求体
        ClientRequest wrappedRequest = wrapRequest(request, bodyCapture);
        
        // 执行请求
        return next.exchange(wrappedRequest)
                .doOnSuccess(response -> {
                    // 请求成功，记录请求体
                    String body = bodyCapture.getBody();
                    logRequest(timestamp, requestId, request, body);
                })
                .doOnError(error -> {
                    // 请求失败，仍然记录请求
                    String body = bodyCapture.getBody();
                    logRequest(timestamp, requestId, request, body);
                })
                .flatMap(response -> {
                    MediaType contentType = response.headers().contentType().orElse(null);
                    boolean isStreaming = contentType != null && 
                            (contentType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM) ||
                             contentType.toString().contains("stream"));
                    
                    if (isStreaming) {
                        // 流式响应：保持流式特性，使用 doOnNext 记录
                        return handleStreamingResponse(response, timestamp, requestId);
                    } else {
                        // 普通响应：可以完整记录
                        return handleNormalResponse(response, timestamp, requestId);
                    }
                });
    }

    /**
     * 包装请求以捕获请求体
     */
    private ClientRequest wrapRequest(ClientRequest request, RequestBodyCapture capture) {
        BodyInserter<?, ? super ClientHttpRequest> originalBodyInserter = request.body();
        
        BodyInserter<?, ? super ClientHttpRequest> wrappedBodyInserter = (outputMessage, context) -> {
            ClientHttpRequest decoratedRequest = new ClientHttpRequestDecorator(outputMessage) {
                @Override
                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                    return super.writeWith(Flux.from(body)
                            .map(buffer -> {
                                byte[] bytes = new byte[buffer.readableByteCount()];
                                buffer.read(bytes);
                                capture.addChunk(bytes);
                                return bufferFactory.wrap(bytes);
                            }));
                }
                
                @Override
                public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                    return super.writeAndFlushWith(Flux.from(body)
                            .map(publisher -> Flux.from(publisher)
                                    .map(buffer -> {
                                        byte[] bytes = new byte[buffer.readableByteCount()];
                                        buffer.read(bytes);
                                        capture.addChunk(bytes);
                                        return bufferFactory.wrap(bytes);
                                    })));
                }
            };
            
            return originalBodyInserter.insert(decoratedRequest, context);
        };
        
        return ClientRequest.from(request)
                .body(wrappedBodyInserter)
                .build();
    }

    /**
     * 处理流式响应 - 保持流式特性
     */
    private Mono<ClientResponse> handleStreamingResponse(ClientResponse response, String timestamp, String requestId) {
        // 用于收集响应体的缓冲区
        StringBuilder responseBuffer = new StringBuilder();
        
        // 转换响应体，同时记录日志
        Flux<DataBuffer> transformedBody = response.bodyToFlux(DataBuffer.class)
                .map(buffer -> {
                    // 复制 buffer 内容用于日志
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    
                    // 添加到日志缓冲区（限制大小）
                    if (responseBuffer.length() < MAX_LOG_LENGTH) {
                        String chunk = new String(bytes, StandardCharsets.UTF_8);
                        responseBuffer.append(chunk);
                    }
                    
                    // 返回新的 buffer（原 buffer 已被读取）
                    return (DataBuffer) bufferFactory.wrap(bytes);
                })
                .doOnComplete(() -> {
                    // 流完成后记录响应
                    String body = responseBuffer.length() > 0 ? responseBuffer.toString() : null;
                    boolean truncated = responseBuffer.length() >= MAX_LOG_LENGTH;
                    logResponse(timestamp, requestId, response, body, truncated);
                })
                .doOnError(error -> {
                    log.warn("流式响应处理失败: {}", error.getMessage());
                    logResponse(timestamp, requestId, response, null, false);
                });
        
        // 重建响应，保持流式特性
        return Mono.just(ClientResponse.create(response.statusCode())
                .headers(headers -> headers.addAll(response.headers().asHttpHeaders()))
                .body(transformedBody)
                .build());
    }

    /**
     * 处理普通响应 - 可以完整记录
     */
    private Mono<ClientResponse> handleNormalResponse(ClientResponse response, String timestamp, String requestId) {
        return response.bodyToMono(DataBuffer.class)
                .defaultIfEmpty(bufferFactory.wrap(new byte[0]))
                .flatMap(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    
                    String body = new String(bytes, StandardCharsets.UTF_8);
                    logResponse(timestamp, requestId, response, body.isEmpty() ? null : body, false);
                    
                    DataBuffer newBuffer = bufferFactory.wrap(bytes);
                    return Mono.just(ClientResponse.create(response.statusCode())
                            .headers(headers -> headers.addAll(response.headers().asHttpHeaders()))
                            .body(Flux.just(newBuffer))
                            .build());
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
    private void logRequest(String timestamp, String requestId, ClientRequest request, String body) {
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
        
        if (body != null && !body.isEmpty()) {
            sb.append("╠═══════════════════════════════════════════════════════════════════════════════\n");
            sb.append("║ Body (JSON):\n");
            sb.append(formatJsonBody(body)).append("\n");
        }
        
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
     * 格式化 JSON 体
     */
    private String formatJsonBody(String json) {
        if (json == null || json.isEmpty()) {
            return "║   (empty)";
        }
        
        String content = json;
        if (content.length() > MAX_LOG_LENGTH) {
            content = content.substring(0, MAX_LOG_LENGTH);
        }
        
        String[] lines = content.split("\n");
        StringBuilder formatted = new StringBuilder();
        for (String line : lines) {
            formatted.append("║   ").append(line).append("\n");
        }
        return formatted.toString().trim();
    }

    /**
     * 请求体捕获器
     */
    private static class RequestBodyCapture {
        private final List<byte[]> chunks = new ArrayList<>();
        
        synchronized void addChunk(byte[] bytes) {
            chunks.add(bytes);
        }
        
        synchronized String getBody() {
            if (chunks.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (byte[] chunk : chunks) {
                sb.append(new String(chunk, StandardCharsets.UTF_8));
            }
            String body = sb.toString();
            return body.isEmpty() ? null : body;
        }
    }
}
