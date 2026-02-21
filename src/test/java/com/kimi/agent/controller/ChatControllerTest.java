package com.kimi.agent.controller;

import com.kimi.agent.model.ChatRequest;
import com.kimi.agent.model.ChatResponse;
import com.kimi.agent.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 聊天控制器测试类
 * 
 * @author Kimi
 */
class ChatControllerTest {

    private ChatController chatController;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        // 使用模拟的 ChatService 进行测试
        chatService = new MockChatService();
        chatController = new ChatController(chatService);
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

    /**
     * 模拟的 ChatService 用于测试
     */
    private static class MockChatService extends ChatService {
        public MockChatService() {
            super(null, null, null);
        }

        @Override
        public void processMessage(String userMessage, String sessionId, 
                                    java.util.function.Consumer<ChatResponse> responseConsumer) {
            // 模拟返回最终答案
            responseConsumer.accept(ChatResponse.finalAnswer("模拟回复: " + userMessage, sessionId));
        }

        @Override
        public String createNewSession() {
            return "mock-session-id-" + System.currentTimeMillis();
        }

        @Override
        public void clearSession(String sessionId) {
            // 模拟清除会话
        }
    }
}
