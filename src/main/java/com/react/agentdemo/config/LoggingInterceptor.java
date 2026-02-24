package com.react.agentdemo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * HTTP 请求/响应日志拦截器
 * 记录完整的请求体和响应体 JSON
 * 
 * @author Kimi
 */
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);
    private static final Logger apiLog = LoggerFactory.getLogger("API_LOG");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String timestamp = LocalDateTime.now().format(formatter);
        String requestId = generateRequestId();
        
        // 记录请求信息
        logRequest(timestamp, requestId, request, body);
        
        // 执行请求
        ClientHttpResponse response = execution.execute(request, body);
        
        // 记录响应信息
        ClientHttpResponse responseCopy = logResponse(timestamp, requestId, response);
        
        return responseCopy;
    }

    /**
     * 记录请求信息
     * 
     * @param timestamp 时间戳
     * @param requestId 请求ID
     * @param request HTTP请求
     * @param body 请求体
     */
    private void logRequest(String timestamp, String requestId, HttpRequest request, byte[] body) {
        String requestBody = new String(body, StandardCharsets.UTF_8);
        
        // 构建日志字符串
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("║ ").append(timestamp).append(" | Request ID: ").append(requestId).append("\n");
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("║ [REQUEST] ").append(request.getMethod()).append(" ").append(request.getURI()).append("\n");
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("║ Headers:\n");
        request.getHeaders().forEach((name, values) -> {
            sb.append("║   ").append(name).append(": ").append(String.join(", ", values)).append("\n");
        });
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("║ Body (JSON):\n");
        sb.append(formatJson(requestBody)).append("\n");
        sb.append("╚═══════════════════════════════════════════════════════════════════════════════");
        
        String logMessage = sb.toString();
        log.info(logMessage);
        apiLog.info(logMessage);
    }

    /**
     * 记录响应信息
     * 
     * @param timestamp 时间戳
     * @param requestId 请求ID
     * @param response HTTP响应
     * @return 响应的副本（因为原始响应流只能读取一次）
     * @throws IOException IO异常
     */
    private ClientHttpResponse logResponse(String timestamp, String requestId, ClientHttpResponse response) throws IOException {
        // 读取响应体
        String responseBody = "";
        if (response.getBody() != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8));
            responseBody = reader.lines().collect(Collectors.joining("\n"));
        }
        
        final String finalResponseBody = responseBody;
        
        // 构建日志字符串
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("║ ").append(timestamp).append(" | Request ID: ").append(requestId).append("\n");
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("║ [RESPONSE] Status: ").append(response.getStatusCode()).append("\n");
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("║ Headers:\n");
        response.getHeaders().forEach((name, values) -> {
            sb.append("║   ").append(name).append(": ").append(String.join(", ", values)).append("\n");
        });
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("║ Body (JSON):\n");
        sb.append(formatJson(finalResponseBody)).append("\n");
        sb.append("╚═══════════════════════════════════════════════════════════════════════════════");
        
        String logMessage = sb.toString();
        log.info(logMessage);
        apiLog.info(logMessage);
        
        // 返回包装后的响应，使响应体可以被再次读取
        return new BufferedClientHttpResponse(response, finalResponseBody.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 格式化 JSON 字符串，添加缩进
     * 
     * @param json 原始 JSON 字符串
     * @return 格式化后的 JSON 字符串
     */
    private String formatJson(String json) {
        if (json == null || json.isEmpty()) {
            return "║   (empty)";
        }
        
        try {
            // 简单的 JSON 格式化：每行添加 "║   " 前缀
            String[] lines = json.split("\n");
            StringBuilder formatted = new StringBuilder();
            for (String line : lines) {
                // 截断过长的行
                if (line.length() > 200) {
                    line = line.substring(0, 2000000) + "... (truncated)";
                }
                formatted.append("║   ").append(line).append("\n");
            }
            return formatted.toString().trim();
        } catch (Exception e) {
            return "║   " + json;
        }
    }

    /**
     * 生成请求ID
     * 
     * @return 唯一请求ID
     */
    private String generateRequestId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
