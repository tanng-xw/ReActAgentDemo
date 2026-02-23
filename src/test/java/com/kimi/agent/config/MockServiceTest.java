package com.kimi.agent.config;

import com.kimi.agent.model.ChatResponse;
import com.kimi.agent.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock 服务测试示例
 * 
 * 使用 @MockBean 替换 ChatService，避免真实调用模型 API
 * 
 * @author Kimi
 */
@SpringBootTest
@ActiveProfiles("test")
public class MockServiceTest {

    @MockBean
    private ChatService chatService;

    /**
     * 测试示例：验证流式响应顺序
     */
    @Test
    public void testStreamingResponseSequence() throws InterruptedException {
        // 模拟 ChatService 的行为
        doAnswer(invocation -> {
            String userMessage = invocation.getArgument(0);
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            // 模拟流式响应
            consumer.accept(ChatResponse.thinking("正在思考...", sessionId));
            consumer.accept(ChatResponse.thinking("我来帮您搜索", sessionId));
            consumer.accept(ChatResponse.streaming("回答：", sessionId));
            consumer.accept(ChatResponse.streaming("这是", sessionId));
            consumer.accept(ChatResponse.streaming("Mock", sessionId));
            consumer.accept(ChatResponse.streaming("回复", sessionId));
            consumer.accept(ChatResponse.finalAnswer("这是Mock回复", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));

        String sessionId = "test-session";
        List<ChatResponse> responses = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("搜索歌曲", sessionId, response -> {
            responses.add(response);
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "应该在5秒内完成");
        
        // 验证响应数量
        assertTrue(responses.size() >= 3, "应该收到至少3个响应");
        
        // 验证响应类型顺序
        assertEquals(ChatResponse.ResponseType.THINKING, responses.get(0).getType());
        assertEquals(ChatResponse.ResponseType.FINAL_ANSWER, responses.get(responses.size() - 1).getType());
        
        System.out.println("收到 " + responses.size() + " 个响应");
        for (int i = 0; i < responses.size(); i++) {
            System.out.println("[" + i + "] " + responses.get(i).getType() + ": " + 
                responses.get(i).getContent().substring(0, Math.min(30, responses.get(i).getContent().length())));
        }
    }

    /**
     * 测试示例：验证工具调用场景
     */
    @Test
    public void testToolCallScenario() throws InterruptedException {
        // 模拟需要工具调用的场景
        doAnswer(invocation -> {
            String userMessage = invocation.getArgument(0);
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            // 模拟思考
            consumer.accept(ChatResponse.thinking("需要搜索歌曲", sessionId));
            
            // 模拟工具调用
            consumer.accept(ChatResponse.toolCallResult("searchSongs", 
                "{\"keyword\": \"周杰伦\"}", 
                "[晴天, 稻香]", sessionId));
            
            // 模拟最终回答
            consumer.accept(ChatResponse.finalAnswer("为您找到周杰伦的歌曲：晴天、稻香", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));

        String sessionId = "test-session-tool";
        List<ChatResponse> responses = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("搜索周杰伦歌曲", sessionId, response -> {
            responses.add(response);
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed);
        
        // 验证包含工具调用
        boolean hasToolCall = responses.stream()
                .anyMatch(r -> r.getType() == ChatResponse.ResponseType.TOOL_CALL);
        assertTrue(hasToolCall, "应该包含工具调用");
        
        System.out.println("工具调用场景测试通过，收到 " + responses.size() + " 个响应");
    }
}
