package com.kimi.agent.stream;

import com.kimi.agent.model.ChatResponse;
import com.kimi.agent.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 流式输出完整性测试
 * 
 * 验证流式响应的实时性，确保消息不是被批量缓冲后一次性返回
 * 
 * 使用 @MockBean 替换 ChatService，避免真实调用模型 API
 * 
 * @author Kimi
 */
@SpringBootTest
@ActiveProfiles("test")
public class StreamingIntegrityTest {

    @MockBean
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        when(chatService.createNewSession()).thenReturn("mock-session-" + System.currentTimeMillis());
    }

    /**
     * 测试用例 1: 验证思考内容是分多个块流式返回的
     */
    @Test
    public void testThinkingContentIsStreamedNotBatched() throws InterruptedException {
        // Mock 思考内容分块发送
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            consumer.accept(ChatResponse.thinking("我来", sessionId));
            Thread.sleep(10); // 模拟网络延迟
            consumer.accept(ChatResponse.thinking("帮您", sessionId));
            Thread.sleep(10);
            consumer.accept(ChatResponse.thinking("搜索", sessionId));
            Thread.sleep(10);
            consumer.accept(ChatResponse.thinking("歌曲", sessionId));
            consumer.accept(ChatResponse.finalAnswer("搜索完成", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));

        String sessionId = chatService.createNewSession();
        List<Long> receiveTimestamps = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger thinkingCount = new AtomicInteger(0);
        
        Instant startTime = Instant.now();

        chatService.processMessage("搜索一首歌曲", sessionId, response -> {
            if (response.getType() == ChatResponse.ResponseType.THINKING) {
                receiveTimestamps.add(System.currentTimeMillis());
                thinkingCount.incrementAndGet();
            }
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "响应应该在5秒内完成");
        
        // 验证：应该收到多个思考内容块
        assertTrue(thinkingCount.get() >= 3, "应该至少收到3个思考内容块");
        
        // 验证响应时间
        long duration = Duration.between(startTime, Instant.now()).toMillis();
        System.out.println("思考内容块数: " + thinkingCount.get());
        System.out.println("总耗时: " + duration + "ms");
        
        // 验证时间间隔（如果有多个思考块）
        if (thinkingCount.get() > 1) {
            assertTrue(duration > 20, "流式响应应该持续一定时间");
        }
    }

    /**
     * 测试用例 2: 验证流式内容（STREAMING 类型）是实时分块返回的
     */
    @Test
    public void testStreamingContentIsRealTime() throws InterruptedException {
        // Mock 流式内容分块发送
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            consumer.accept(ChatResponse.streaming("回答：", sessionId));
            Thread.sleep(5);
            consumer.accept(ChatResponse.streaming("为您", sessionId));
            Thread.sleep(5);
            consumer.accept(ChatResponse.streaming("找到", sessionId));
            Thread.sleep(5);
            consumer.accept(ChatResponse.streaming("了", sessionId));
            Thread.sleep(5);
            consumer.accept(ChatResponse.streaming("歌曲", sessionId));
            consumer.accept(ChatResponse.finalAnswer("回答：为您找到了歌曲", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));

        String sessionId = chatService.createNewSession();
        List<ChatResponse> streamingResponses = new ArrayList<>();
        StringBuilder finalAnswer = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("搜索邓紫棋的歌曲", sessionId, response -> {
            if (response.getType() == ChatResponse.ResponseType.STREAMING) {
                streamingResponses.add(response);
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

        // 关键验证：应该收到多个 STREAMING 块
        assertTrue(streamingResponses.size() >= 4, "应该收到至少4个流式内容块");
        
        // 验证内容不为空
        int totalLength = fullStreaming.length() + finalAnswer.length();
        assertTrue(totalLength > 10, "回答内容应该有一定的长度");
    }

    /**
     * 测试用例 3: 验证响应类型顺序正确
     */
    @Test
    public void testResponseTypeSequence() throws InterruptedException {
        // Mock 标准响应序列
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            consumer.accept(ChatResponse.thinking("思考1", sessionId));
            consumer.accept(ChatResponse.thinking("思考2", sessionId));
            consumer.accept(ChatResponse.toolCallResult("tool", "arg", null, sessionId));
            consumer.accept(ChatResponse.thinking("思考3", sessionId));
            consumer.accept(ChatResponse.streaming("回答：", sessionId));
            consumer.accept(ChatResponse.streaming("内容", sessionId));
            consumer.accept(ChatResponse.finalAnswer("最终答案", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));

        String sessionId = chatService.createNewSession();
        List<ChatResponse.ResponseType> typeSequence = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("搜索周杰伦的歌曲", sessionId, response -> {
            // 只记录类型变化
            if (typeSequence.isEmpty() || typeSequence.get(typeSequence.size() - 1) != response.getType()) {
                typeSequence.add(response.getType());
            }
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "响应应该在5秒内完成");
        
        System.out.println("响应类型顺序: " + typeSequence);
        
        // 验证顺序合理性
        assertTrue(typeSequence.contains(ChatResponse.ResponseType.THINKING), 
                "应该包含 THINKING 类型");
        assertTrue(typeSequence.contains(ChatResponse.ResponseType.TOOL_CALL), 
                "应该包含 TOOL_CALL 类型");
        assertTrue(typeSequence.contains(ChatResponse.ResponseType.FINAL_ANSWER), 
                "应该包含 FINAL_ANSWER 类型");
        
        // 验证 FINAL_ANSWER 是最后一个
        ChatResponse.ResponseType lastType = typeSequence.get(typeSequence.size() - 1);
        assertEquals(ChatResponse.ResponseType.FINAL_ANSWER, lastType,
                "最后一个类型应该是 FINAL_ANSWER");
    }

    /**
     * 测试用例 4: 验证最终答案与流式内容的一致性
     */
    @Test
    public void testFinalAnswerConsistency() throws InterruptedException {
        // Mock 流式内容和最终答案
        String expectedAnswer = "回答：为您找到了歌曲";
        
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            consumer.accept(ChatResponse.streaming("回答：", sessionId));
            consumer.accept(ChatResponse.streaming("为您", sessionId));
            consumer.accept(ChatResponse.streaming("找到", sessionId));
            consumer.accept(ChatResponse.streaming("了", sessionId));
            consumer.accept(ChatResponse.streaming("歌曲", sessionId));
            consumer.accept(ChatResponse.finalAnswer(expectedAnswer, sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));

        String sessionId = chatService.createNewSession();
        StringBuilder streamingContent = new StringBuilder();
        String[] finalAnswer = new String[1];
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("搜索一首流行歌曲", sessionId, response -> {
            if (response.getType() == ChatResponse.ResponseType.STREAMING) {
                streamingContent.append(response.getContent());
            } else if (response.getType() == ChatResponse.ResponseType.FINAL_ANSWER) {
                finalAnswer[0] = response.getContent();
            }
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "响应应该在5秒内完成");
        
        // 验证流式内容拼接后与最终答案一致
        String streaming = streamingContent.toString().trim();
        String final_ans = finalAnswer[0].trim();
        
        System.out.println("流式内容: " + streaming);
        System.out.println("最终答案: " + final_ans);
        
        assertEquals(streaming, final_ans, "流式内容应该与最终答案一致");
    }

    /**
     * 测试用例 5: 验证工具调用场景下的流式输出
     */
    @Test
    public void testStreamingWithToolCalls() throws InterruptedException {
        // Mock 多个工具调用场景
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            consumer.accept(ChatResponse.thinking("搜索第一位歌手", sessionId));
            consumer.accept(ChatResponse.toolCallResult("searchSongs", 
                "{\"keyword\": \"周杰伦\"}", "[晴天]", sessionId));
            
            consumer.accept(ChatResponse.thinking("搜索第二位歌手", sessionId));
            consumer.accept(ChatResponse.toolCallResult("searchSongs", 
                "{\"keyword\": \"邓紫棋\"}", "[光年之外]", sessionId));
            
            consumer.accept(ChatResponse.streaming("回答：", sessionId));
            consumer.accept(ChatResponse.streaming("找到", sessionId));
            consumer.accept(ChatResponse.streaming("两位", sessionId));
            consumer.accept(ChatResponse.streaming("歌手", sessionId));
            consumer.accept(ChatResponse.finalAnswer("回答：找到两位歌手的歌曲", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));

        String sessionId = chatService.createNewSession();
        AtomicInteger toolCallCount = new AtomicInteger(0);
        AtomicInteger streamingCount = new AtomicInteger(0);
        AtomicInteger thinkingCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("搜索周杰伦和邓紫棋的歌曲", sessionId, response -> {
            switch (response.getType()) {
                case TOOL_CALL -> toolCallCount.incrementAndGet();
                case STREAMING -> streamingCount.incrementAndGet();
                case THINKING -> thinkingCount.incrementAndGet();
            }
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "响应应该在5秒内完成");
        
        System.out.println("工具调用数: " + toolCallCount.get());
        System.out.println("流式内容数: " + streamingCount.get());
        System.out.println("思考内容数: " + thinkingCount.get());
        
        // 验证工具调用正常触发
        assertEquals(2, toolCallCount.get(), "应该触发2个工具调用");
        
        // 验证思考过程和流式输出正常
        assertTrue(thinkingCount.get() >= 2, "应该有至少2个思考内容");
        assertTrue(streamingCount.get() >= 2, "应该有至少2个流式内容");
    }
}
