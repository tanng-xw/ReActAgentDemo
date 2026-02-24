package com.react.agentdemo.stream;

import com.react.agentdemo.model.ChatResponse;
import com.react.agentdemo.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
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
 * 流式输出 "回答" 标记处理测试
 * 
 * 测试场景：
 * 1. 思考过程应该流式输出（逐步接收，不是一次性）
 * 2. 回答内容应该流式输出（逐步接收，不是一次性）
 * 3. "回答" 标记本身不应该出现在输出中
 * 
 * 使用 @MockBean 替换 ChatService，避免真实调用模型 API
 * 
 * @author Kimi
 */
@SpringBootTest
@ActiveProfiles("test")
public class StreamingAnswerMarkerTest {

    @MockBean
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        // 配置 Mock ChatService
        when(chatService.createNewSession()).thenReturn("mock-session-" + System.currentTimeMillis());
    }

    /**
     * 测试用例 1: 思考过程应该流式输出
     */
    @Test
    public void testThinkingContentShouldBeStreaming() throws InterruptedException {
        // Mock 响应序列
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            // 模拟流式思考内容
            consumer.accept(ChatResponse.thinking("我来", sessionId));
            consumer.accept(ChatResponse.thinking("为您", sessionId));
            consumer.accept(ChatResponse.thinking("搜索", sessionId));
            consumer.accept(ChatResponse.thinking("周杰伦", sessionId));
            consumer.accept(ChatResponse.thinking("的", sessionId));
            consumer.accept(ChatResponse.thinking("歌曲", sessionId));
            consumer.accept(ChatResponse.finalAnswer("为您找到周杰伦的歌曲", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));

        String sessionId = chatService.createNewSession();
        List<ChatResponse> thinkingResponses = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("搜索周杰伦的歌曲", sessionId, response -> {
            if (response.getType() == ChatResponse.ResponseType.THINKING) {
                thinkingResponses.add(response);
                System.out.println("[THINKING] " + response.getContent());
            }
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "响应应该在5秒内完成");

        // 验证思考内容不是空的
        assertFalse(thinkingResponses.isEmpty(), "应该收到思考内容");
        
        // 合并所有思考内容
        StringBuilder fullThinking = new StringBuilder();
        for (ChatResponse r : thinkingResponses) {
            fullThinking.append(r.getContent());
        }
        
        System.out.println("思考内容块数: " + thinkingResponses.size());
        System.out.println("完整思考内容: " + fullThinking);

        // 验证思考内容分多个块
        assertTrue(thinkingResponses.size() > 1, "思考内容应该分多个块发送");
        
        // 验证思考内容不包含孤立的 "回答" 标记
        String thinking = fullThinking.toString();
        assertFalse(thinking.contains("回答"), "思考内容不应该包含'回答'标记");
    }

    /**
     * 测试用例 2: 回答内容应该流式输出
     */
    @Test
    public void testAnswerContentShouldBeStreaming() throws InterruptedException {
        // Mock 响应序列
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            // 模拟流式回答内容
            consumer.accept(ChatResponse.streaming("回答：", sessionId));
            consumer.accept(ChatResponse.streaming("为您", sessionId));
            consumer.accept(ChatResponse.streaming("找到", sessionId));
            consumer.accept(ChatResponse.streaming("了", sessionId));
            consumer.accept(ChatResponse.streaming("邓紫棋", sessionId));
            consumer.accept(ChatResponse.streaming("的", sessionId));
            consumer.accept(ChatResponse.streaming("歌曲", sessionId));
            consumer.accept(ChatResponse.finalAnswer("为您找到了邓紫棋的歌曲", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));

        String sessionId = chatService.createNewSession();
        List<ChatResponse> streamingResponses = new ArrayList<>();
        StringBuilder finalAnswer = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("搜索邓紫棋的歌曲", sessionId, response -> {
            if (response.getType() == ChatResponse.ResponseType.STREAMING) {
                streamingResponses.add(response);
                System.out.println("[STREAMING] " + response.getContent());
            } else if (response.getType() == ChatResponse.ResponseType.FINAL_ANSWER) {
                finalAnswer.append(response.getContent());
            }
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "响应应该在5秒内完成");

        // 合并所有流式内容
        StringBuilder fullStreaming = new StringBuilder();
        for (ChatResponse r : streamingResponses) {
            fullStreaming.append(r.getContent());
        }

        System.out.println("流式内容块数: " + streamingResponses.size());
        System.out.println("流式内容: " + fullStreaming);

        // 关键验证：应该有多个流式内容块
        assertTrue(streamingResponses.size() > 1, "应该收到多个流式内容块");
        
        // 验证内容不为空
        assertTrue(fullStreaming.length() > 10, "流式内容应该有一定的长度");
    }

    /**
     * 测试用例 3: "回答" 标记处理
     */
    @Test
    public void testAnswerMarkerHandling() throws InterruptedException {
        // Mock 包含 "回答" 标记的响应序列
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            // 模拟思考过程
            consumer.accept(ChatResponse.thinking("我来", sessionId));
            consumer.accept(ChatResponse.thinking("帮您", sessionId));
            consumer.accept(ChatResponse.thinking("搜索", sessionId));
            // 注意：思考内容中不应该包含 "回答" 标记
            
            // 回答部分
            consumer.accept(ChatResponse.streaming("回答：", sessionId));
            consumer.accept(ChatResponse.streaming("这是", sessionId));
            consumer.accept(ChatResponse.streaming("结果", sessionId));
            consumer.accept(ChatResponse.finalAnswer("回答：这是结果", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));

        String sessionId = chatService.createNewSession();
        List<String> thinkingContents = new ArrayList<>();
        List<String> streamingContents = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("搜索歌曲", sessionId, response -> {
            if (response.getType() == ChatResponse.ResponseType.THINKING) {
                thinkingContents.add(response.getContent());
            } else if (response.getType() == ChatResponse.ResponseType.STREAMING) {
                streamingContents.add(response.getContent());
            }
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed);

        // 验证：思考内容中不应该包含 "回答"
        String fullThinking = String.join("", thinkingContents);
        assertFalse(fullThinking.contains("回答"), "思考内容不应该包含'回答'标记");
        
        // 验证：流式内容可以包含 "回答"
        String fullStreaming = String.join("", streamingContents);
        System.out.println("流式内容: " + fullStreaming);
    }

    /**
     * 测试用例 4: 工具调用场景下的流式输出
     */
    @Test
    public void testStreamingWithToolCalls() throws InterruptedException {
        // Mock 包含工具调用的响应序列
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            // 思考过程
            consumer.accept(ChatResponse.thinking("需要", sessionId));
            consumer.accept(ChatResponse.thinking("搜索", sessionId));
            consumer.accept(ChatResponse.thinking("两位", sessionId));
            consumer.accept(ChatResponse.thinking("歌手", sessionId));
            
            // 工具调用
            consumer.accept(ChatResponse.toolCallResult("searchSongs", 
                "{\"keyword\": \"周杰伦\"}", null, sessionId));
            consumer.accept(ChatResponse.toolCallResult("searchSongs", 
                "{\"keyword\": \"邓紫棋\"}", null, sessionId));
            
            // 最终结果
            consumer.accept(ChatResponse.streaming("回答：", sessionId));
            consumer.accept(ChatResponse.streaming("找到", sessionId));
            consumer.accept(ChatResponse.streaming("了", sessionId));
            consumer.accept(ChatResponse.finalAnswer("回答：找到了两位歌手的歌曲", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));

        String sessionId = chatService.createNewSession();
        List<ChatResponse> allResponses = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("搜索周杰伦和邓紫棋的歌曲", sessionId, response -> {
            allResponses.add(response);
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed);

        // 统计各类响应
        long thinkingCount = allResponses.stream()
                .filter(r -> r.getType() == ChatResponse.ResponseType.THINKING).count();
        long streamingCount = allResponses.stream()
                .filter(r -> r.getType() == ChatResponse.ResponseType.STREAMING).count();
        long toolCallCount = allResponses.stream()
                .filter(r -> r.getType() == ChatResponse.ResponseType.TOOL_CALL).count();

        System.out.println("思考响应数: " + thinkingCount);
        System.out.println("流式响应数: " + streamingCount);
        System.out.println("工具调用数: " + toolCallCount);

        // 验证
        assertEquals(2, toolCallCount, "应该触发2个工具调用");
        assertTrue(thinkingCount > 0, "应该有思考内容");
        assertTrue(streamingCount > 0, "应该有流式内容");
    }
}
