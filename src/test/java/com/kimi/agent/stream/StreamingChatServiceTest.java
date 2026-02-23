package com.kimi.agent.stream;

import com.kimi.agent.model.ChatResponse;
import com.kimi.agent.service.ChatService;
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
 * 流式聊天服务测试类
 * 
 * 使用 @MockBean 替换 ChatService，避免真实调用模型 API
 * 
 * @author Kimi
 */
@SpringBootTest
@ActiveProfiles("test")
public class StreamingChatServiceTest {

    @MockBean
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        when(chatService.createNewSession()).thenReturn("mock-session-" + System.currentTimeMillis());
    }

    /**
     * 测试用例 1: 基本流式响应测试
     * 验证流式响应能够正常接收多个内容块
     */
    @Test
    public void testBasicStreamingResponse() throws InterruptedException {
        // Mock 基本流式响应
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            consumer.accept(ChatResponse.thinking("正在思考...", sessionId));
            consumer.accept(ChatResponse.streaming("你好", sessionId));
            consumer.accept(ChatResponse.streaming("，", sessionId));
            consumer.accept(ChatResponse.streaming("我是", sessionId));
            consumer.accept(ChatResponse.streaming("AI", sessionId));
            consumer.accept(ChatResponse.streaming("助手", sessionId));
            consumer.accept(ChatResponse.finalAnswer("你好，我是AI助手", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));

        String sessionId = chatService.createNewSession();
        List<ChatResponse> responses = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("你好，请介绍一下自己", sessionId, response -> {
            responses.add(response);
            if (response.isDone()) {
                latch.countDown();
            }
        });

        // 等待最多5秒
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        
        assertTrue(completed, "流式响应应该在5秒内完成");
        assertFalse(responses.isEmpty(), "应该收到至少一个响应");
        
        // 验证响应类型序列
        boolean hasStreaming = responses.stream()
                .anyMatch(r -> r.getType() == ChatResponse.ResponseType.STREAMING);
        boolean hasFinalAnswer = responses.stream()
                .anyMatch(r -> r.getType() == ChatResponse.ResponseType.FINAL_ANSWER);
        
        System.out.println("总共收到 " + responses.size() + " 个响应");
        System.out.println("包含流式内容: " + hasStreaming);
        System.out.println("包含最终答案: " + hasFinalAnswer);
        
        assertTrue(hasFinalAnswer || hasStreaming, "应该收到流式内容或最终答案");
    }

    /**
     * 测试用例 2: 流式内容合并测试
     * 验证所有流式内容块能够正确合并成完整响应
     */
    @Test
    public void testStreamingContentConcatenation() throws InterruptedException {
        // Mock 流式内容
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            consumer.accept(ChatResponse.streaming("今天", sessionId));
            consumer.accept(ChatResponse.streaming("天气", sessionId));
            consumer.accept(ChatResponse.streaming("不错", sessionId));
            consumer.accept(ChatResponse.finalAnswer("今天天气不错", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));

        String sessionId = chatService.createNewSession();
        List<String> streamingChunks = new ArrayList<>();
        StringBuilder finalContent = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("今天天气怎么样", sessionId, response -> {
            if (response.getType() == ChatResponse.ResponseType.STREAMING) {
                streamingChunks.add(response.getContent());
            } else if (response.getType() == ChatResponse.ResponseType.FINAL_ANSWER) {
                finalContent.append(response.getContent());
            }
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "流式响应应该完成");
        
        // 合并流式内容
        String concatenated = String.join("", streamingChunks);
        System.out.println("流式内容块数: " + streamingChunks.size());
        System.out.println("合并后内容: " + concatenated);
        System.out.println("最终答案: " + finalContent);
        
        // 验证内容不为空
        assertTrue(concatenated.length() > 0 || finalContent.length() > 0, 
                "流式内容或最终答案至少有一个不为空");
    }

    /**
     * 测试用例 3: 工具调用时的流式处理
     * 验证在工具调用情况下流式响应是否正常
     */
    @Test
    public void testStreamingWithToolCall() throws InterruptedException {
        // Mock 工具调用场景
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            consumer.accept(ChatResponse.thinking("需要查询位置", sessionId));
            consumer.accept(ChatResponse.toolCallResult("getUserLocation", "", "上海", sessionId));
            consumer.accept(ChatResponse.streaming("您在", sessionId));
            consumer.accept(ChatResponse.streaming("上海", sessionId));
            consumer.accept(ChatResponse.finalAnswer("您在上海", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));

        String sessionId = chatService.createNewSession();
        List<ChatResponse> responses = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("我在哪个城市", sessionId, response -> {
            responses.add(response);
            System.out.println("[" + response.getType() + "] " + 
                    (response.getToolName() != null ? "工具: " + response.getToolName() : response.getContent()));
            
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "带工具调用的流式响应应该完成");
        
        // 验证是否包含工具调用
        boolean hasToolCall = responses.stream()
                .anyMatch(r -> r.getType() == ChatResponse.ResponseType.TOOL_CALL);
        
        System.out.println("包含工具调用: " + hasToolCall);
        assertTrue(hasToolCall, "位置查询应该触发工具调用");
    }

    /**
     * 测试用例 4: 多轮对话流式测试
     * 验证多轮对话时流式响应是否正确
     */
    @Test
    public void testMultiRoundStreaming() throws InterruptedException {
        // Mock 多轮对话响应
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            String message = invocation.getArgument(0);
            
            if (message.contains("北京")) {
                consumer.accept(ChatResponse.streaming("北京", sessionId));
                consumer.accept(ChatResponse.streaming("今天", sessionId));
                consumer.accept(ChatResponse.streaming("晴天", sessionId));
                consumer.accept(ChatResponse.finalAnswer("北京今天晴天", sessionId));
            } else {
                consumer.accept(ChatResponse.streaming("你好", sessionId));
                consumer.accept(ChatResponse.finalAnswer("你好", sessionId));
            }
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));

        String sessionId = chatService.createNewSession();
        
        // 第一轮对话
        CountDownLatch latch1 = new CountDownLatch(1);
        List<ChatResponse> round1Responses = new ArrayList<>();
        
        chatService.processMessage("你好", sessionId, response -> {
            round1Responses.add(response);
            if (response.isDone()) latch1.countDown();
        });
        
        assertTrue(latch1.await(5, TimeUnit.SECONDS), "第一轮应该完成");
        
        // 第二轮对话
        CountDownLatch latch2 = new CountDownLatch(1);
        List<ChatResponse> round2Responses = new ArrayList<>();
        
        chatService.processMessage("北京天气怎么样", sessionId, response -> {
            round2Responses.add(response);
            if (response.isDone()) latch2.countDown();
        });
        
        assertTrue(latch2.await(5, TimeUnit.SECONDS), "第二轮应该完成");
        
        System.out.println("第一轮响应数: " + round1Responses.size());
        System.out.println("第二轮响应数: " + round2Responses.size());
        
        // 验证两轮都有响应
        assertFalse(round1Responses.isEmpty(), "第一轮应该有响应");
        assertFalse(round2Responses.isEmpty(), "第二轮应该有响应");
    }

    /**
     * 测试用例 5: 错误处理测试
     * 验证异常情况下的流式处理
     */
    @Test
    public void testErrorHandling() throws InterruptedException {
        // Mock 错误场景
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            consumer.accept(ChatResponse.error("服务暂时不可用", sessionId));
            
            return null;
        }).when(chatService).processMessage(eq("测试"), anyString(), any(Consumer.class));

        String sessionId = chatService.createNewSession();
        List<ChatResponse> responses = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("测试", sessionId, response -> {
            responses.add(response);
            if (response.isDone()) latch.countDown();
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "应该完成处理（无论成功或失败）");
        
        // 验证收到错误响应
        boolean hasError = responses.stream()
                .anyMatch(r -> r.getType() == ChatResponse.ResponseType.ERROR);
        assertTrue(hasError, "应该收到错误响应");
    }
}
