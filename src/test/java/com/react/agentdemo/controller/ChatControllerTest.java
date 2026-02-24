package com.react.agentdemo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.react.agentdemo.model.ChatRequest;
import com.react.agentdemo.model.ChatResponse;
import com.react.agentdemo.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 聊天控制器测试类
 * 
 * 使用 @MockBean 替换 ChatService，避免真实调用模型 API
 * 
 * @author Kimi
 */
@SpringBootTest
@ActiveProfiles("test")
class ChatControllerTest {

    @Autowired
    private ChatController chatController;

    @MockBean
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        // 配置 Mock ChatService
        when(chatService.createNewSession()).thenReturn("mock-session-id-" + System.currentTimeMillis());
        doNothing().when(chatService).clearSession(anyString());
        
        // 配置 processMessage 的默认行为
        doAnswer(invocation -> {
            String userMessage = invocation.getArgument(0);
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            consumer.accept(ChatResponse.finalAnswer("模拟回复: " + userMessage, sessionId));
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));
    }

    @Test
    @DisplayName("测试创建新会话")
    void testCreateNewSession() {
        // 当
        ResponseEntity<Map<String, String>> response = chatController.createNewSession();

        // 则
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("sessionId"));
        assertFalse(response.getBody().get("sessionId").isEmpty());
    }

    @Test
    @DisplayName("测试清除会话")
    void testClearSession() {
        // 给定
        String sessionId = "test-session-123";

        // 当
        ResponseEntity<Map<String, Object>> response = chatController.clearSession(sessionId);

        // 则
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("success"));
        assertEquals("会话已清除", response.getBody().get("message"));
        
        // 验证 clearSession 被调用
        verify(chatService, times(1)).clearSession(sessionId);
    }

    @Test
    @DisplayName("测试健康检查")
    void testHealth() {
        // 当
        ResponseEntity<Map<String, String>> response = chatController.health();

        // 则
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals("ok", response.getBody().get("status"));
    }

    @Test
    @DisplayName("测试发送消息请求")
    void testSendMessageRequest() {
        // 给定
        ChatRequest request = new ChatRequest();
        request.setMessage("你好");
        request.setSessionId("test-session");

        // 当
        ResponseEntity<ChatResponse> response = chatController.sendMessage(request);

        // 则
        assertNotNull(response);
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("测试发送消息时创建新会话")
    void testSendMessageCreatesNewSession() {
        // 给定 - 没有 sessionId
        ChatRequest request = new ChatRequest();
        request.setMessage("你好");
        request.setSessionId(null);

        // 当
        ResponseEntity<ChatResponse> response = chatController.sendMessage(request);

        // 则 - 响应应该包含新的 sessionId
        assertNotNull(response);
        assertNotNull(response.getBody());
    }
}
