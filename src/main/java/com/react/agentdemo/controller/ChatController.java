package com.react.agentdemo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.react.agentdemo.model.ChatRequest;
import com.react.agentdemo.model.ChatResponse;
import com.react.agentdemo.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 聊天控制器
 * 处理聊天相关的HTTP请求
 * 
 * @author Kimi
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    /** SSE 连接超时时间（毫秒） */
    private static final long SSE_TIMEOUT = 300000L; // 5分钟

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public ChatController(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送消息并获取流式响应
     * 使用 SSE（Server-Sent Events）实现流式返回
     * 
     * @param request 聊天请求
     * @return SSE 发射器
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        // 如果没有会话ID，创建新的
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = chatService.createNewSession();
        }

        final String finalSessionId = sessionId;
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // 处理完成或发生错误时关闭 emitter
        emitter.onCompletion(() -> logger.info("SSE 连接完成，会话ID: {}", finalSessionId));
        emitter.onTimeout(() -> logger.warn("SSE 连接超时，会话ID: {}", finalSessionId));
        emitter.onError(e -> logger.error("SSE 连接错误，会话ID: {}", finalSessionId, e));

        // 异步处理消息
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("开始处理流式消息，会话ID: {}", finalSessionId);
                
                chatService.processMessage(request.getMessage(), finalSessionId, response -> {
                    try {
                        logger.debug("发送SSE消息 - 会话: {}, 类型: {}", finalSessionId, response.getType());
                        
                        // 使用 SseEmitter 发送事件
                        SseEmitter.SseEventBuilder event = SseEmitter.event()
                                .name("message")
                                .data(response);
                        
                        emitter.send(event);
                        
                        // 如果是最终答案或错误，完成连接
                        if (response.isDone()) {
                            logger.info("SSE连接完成，会话ID: {}", finalSessionId);
                            emitter.complete();
                        }
                    } catch (IOException e) {
                        logger.error("发送 SSE 消息失败", e);
                        emitter.completeWithError(e);
                    }
                });
                
            } catch (Exception e) {
                logger.error("处理消息失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(ChatResponse.error(e.getMessage(), finalSessionId)));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    /**
     * 发送消息并获取完整响应（非流式）
     * 
     * @param request 聊天请求
     * @return 聊天响应
     */
    @PostMapping("/send")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request) {
        // 如果没有会话ID，创建新的
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = chatService.createNewSession();
        }

        final String finalSessionId = sessionId;
        final ChatResponse[] finalResponse = new ChatResponse[1];

        chatService.processMessage(request.getMessage(), finalSessionId, response -> {
            if (response.getType() == ChatResponse.ResponseType.FINAL_ANSWER ||
                response.getType() == ChatResponse.ResponseType.ERROR) {
                finalResponse[0] = response;
            }
        });

        if (finalResponse[0] == null) {
            finalResponse[0] = ChatResponse.error("处理超时", finalSessionId);
        }

        return ResponseEntity.ok(finalResponse[0]);
    }

    /**
     * 创建新会话
     * 
     * @return 新会话ID
     */
    @PostMapping("/session/new")
    public ResponseEntity<Map<String, String>> createNewSession() {
        String sessionId = chatService.createNewSession();
        Map<String, String> result = new HashMap<>();
        result.put("sessionId", sessionId);
        return ResponseEntity.ok(result);
    }

    /**
     * 清除会话
     * 
     * @param sessionId 会话ID
     * @return 操作结果
     */
    @PostMapping("/session/{sessionId}/clear")
    public ResponseEntity<Map<String, Object>> clearSession(@PathVariable String sessionId) {
        chatService.clearSession(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "会话已清除");
        return ResponseEntity.ok(result);
    }

    /**
     * 健康检查
     * 
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "ok");
        return ResponseEntity.ok(result);
    }
}
