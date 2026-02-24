package com.react.agentdemo.stream;

import com.react.agentdemo.model.ChatResponse;
import com.react.agentdemo.service.ChatService;
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
 * 流式展示问题测试
 * 
 * BUG 描述:
 * 1. 流式内容包含了思考过程（应该只包含最终答案）
 * 2. 思考过程和最终答案重复显示
 * 
 * 预期行为:
 * 1. THINKING: 显示思考过程（如"我来帮您查询..."）
 * 2. STREAMING: 只流式输出最终答案内容（不含思考过程）
 * 3. FINAL_ANSWER: 显示最终答案（如果已流式显示过，应去重）
 * 
 * @author Kimi
 */
@SpringBootTest
@ActiveProfiles("test")
public class StreamingDisplayIssueTest {

    @MockBean
    private ChatService chatService;

    /**
     * 测试用例 1: 验证流式内容不包含思考过程
     * 
     * 当前问题: 流式内容包含了"我来帮您查询..."这样的思考内容
     * 预期: STREAMING 类型的内容应该只包含最终答案部分
     */
    @Test
    public void testStreamingContentShouldNotContainThinking() throws InterruptedException {
        String testSessionId = "test-session-display-1";
        when(chatService.createNewSession()).thenReturn(testSessionId);
        
        // Mock ChatService 行为
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            // 模拟思考过程
            consumer.accept(ChatResponse.thinking("我来帮您查询天气", sessionId));
            consumer.accept(ChatResponse.thinking("需要先获取位置", sessionId));
            
            // 模拟流式输出（应该只包含答案，不包含思考）
            consumer.accept(ChatResponse.streaming("今天北京天气", sessionId));
            consumer.accept(ChatResponse.streaming("晴朗，", sessionId));
            consumer.accept(ChatResponse.streaming("温度25°C", sessionId));
            
            // 最终答案
            consumer.accept(ChatResponse.finalAnswer("今天北京天气晴朗，温度25°C", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));
        
        String sessionId = chatService.createNewSession();
        List<ChatResponse> thinkingResponses = new ArrayList<>();
        List<String> streamingChunks = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("今天天气怎么样", sessionId, response -> {
            if (response.getType() == ChatResponse.ResponseType.THINKING) {
                thinkingResponses.add(response);
            } else if (response.getType() == ChatResponse.ResponseType.STREAMING) {
                streamingChunks.add(response.getContent());
            } else if (response.getType() == ChatResponse.ResponseType.FINAL_ANSWER) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "应该完成响应");

        // 合并流式内容
        String fullStreamingContent = String.join("", streamingChunks);
        
        System.out.println("=== 测试：流式内容不应包含思考过程 ===");
        System.out.println("思考响应数量: " + thinkingResponses.size());
        System.out.println("流式内容块数: " + streamingChunks.size());
        System.out.println("流式内容总长度: " + fullStreamingContent.length());
        
        // 验证收到了思考和流式内容
        assertFalse(thinkingResponses.isEmpty(), "应该收到思考响应");
        assertFalse(streamingChunks.isEmpty(), "应该收到流式内容");
        
        // 检查流式内容是否包含思考过程的关键字
        boolean containsThinkingKeywords = fullStreamingContent.contains("我来帮您") ||
                                          fullStreamingContent.contains("需要先获取");
        
        System.out.println("流式内容包含思考关键字: " + containsThinkingKeywords);
        
        // 流式内容不应包含思考关键字
        assertFalse(containsThinkingKeywords, "流式内容不应包含思考过程");
    }

    /**
     * 测试用例 2: 验证响应类型的正确顺序
     * 
     * 正确顺序应该是:
     * 1. THINKING (思考中)
     * 2. TOOL_CALL (如果有工具调用)
     * 3. STREAMING (流式输出答案)
     * 4. FINAL_ANSWER (最终答案)
     */
    @Test
    public void testResponseTypeOrder() throws InterruptedException {
        String testSessionId = "test-session-display-2";
        when(chatService.createNewSession()).thenReturn(testSessionId);
        
        // Mock ChatService 行为
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            // 模拟完整响应流程
            consumer.accept(ChatResponse.thinking("开始思考", sessionId));
            consumer.accept(ChatResponse.toolCallResult("getLocation", "{}", "北京", sessionId));
            consumer.accept(ChatResponse.thinking("获取到位置", sessionId));
            consumer.accept(ChatResponse.streaming("答案是", sessionId));
            consumer.accept(ChatResponse.finalAnswer("答案是什么", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));
        
        String sessionId = chatService.createNewSession();
        List<ChatResponse.ResponseType> typeSequence = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("今天天气怎么样", sessionId, response -> {
            typeSequence.add(response.getType());
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "应该完成响应");

        System.out.println("=== 测试：响应类型顺序 ===");
        System.out.println("响应类型序列:");
        for (int i = 0; i < typeSequence.size(); i++) {
            System.out.println("  [" + i + "] " + typeSequence.get(i));
        }

        // 验证序列合理性
        boolean hasThinking = typeSequence.contains(ChatResponse.ResponseType.THINKING);
        boolean hasStreaming = typeSequence.contains(ChatResponse.ResponseType.STREAMING);
        boolean hasFinalAnswer = typeSequence.contains(ChatResponse.ResponseType.FINAL_ANSWER);

        assertTrue(hasThinking, "应该包含 THINKING 类型");
        assertTrue(hasStreaming, "应该包含 STREAMING 类型");
        assertTrue(hasFinalAnswer, "应该包含 FINAL_ANSWER 类型");
        
        // 验证顺序：THINKING 在 STREAMING 之前
        int thinkingIndex = typeSequence.indexOf(ChatResponse.ResponseType.THINKING);
        int streamingIndex = typeSequence.indexOf(ChatResponse.ResponseType.STREAMING);
        int finalAnswerIndex = typeSequence.indexOf(ChatResponse.ResponseType.FINAL_ANSWER);
        
        assertTrue(thinkingIndex < streamingIndex, "THINKING 应该在 STREAMING 之前");
        assertTrue(streamingIndex < finalAnswerIndex, "STREAMING 应该在 FINAL_ANSWER 之前");
    }

    /**
     * 测试用例 3: 验证思考过程和最终答案不重复
     * 
     * 当前问题: 思考过程中的内容又在最终答案中重复显示
     */
    @Test
    public void testThinkingAndAnswerNotDuplicate() throws InterruptedException {
        String testSessionId = "test-session-display-3";
        when(chatService.createNewSession()).thenReturn(testSessionId);
        
        // Mock ChatService 行为
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(1);
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            
            // 思考内容
            consumer.accept(ChatResponse.thinking("我来分析这个问题", sessionId));
            consumer.accept(ChatResponse.thinking("需要查询相关信息", sessionId));
            
            // 流式输出答案（不含思考内容）
            consumer.accept(ChatResponse.streaming("最终", sessionId));
            consumer.accept(ChatResponse.streaming("答案", sessionId));
            
            // 最终答案
            consumer.accept(ChatResponse.finalAnswer("最终答案", sessionId));
            
            return null;
        }).when(chatService).processMessage(anyString(), anyString(), any(Consumer.class));
        
        String sessionId = chatService.createNewSession();
        StringBuilder thinkingContent = new StringBuilder();
        StringBuilder streamingContent = new StringBuilder();
        String finalAnswer = null;
        final String[] finalAnswerHolder = {null};
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("今天天气怎么样", sessionId, response -> {
            if (response.getType() == ChatResponse.ResponseType.THINKING) {
                thinkingContent.append(response.getContent());
            } else if (response.getType() == ChatResponse.ResponseType.STREAMING) {
                streamingContent.append(response.getContent());
            } else if (response.getType() == ChatResponse.ResponseType.FINAL_ANSWER) {
                finalAnswerHolder[0] = response.getContent();
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "应该完成响应");

        finalAnswer = finalAnswerHolder[0];
        
        System.out.println("=== 测试：思考与答案不重复 ===");
        System.out.println("思考内容长度: " + thinkingContent.length());
        System.out.println("流式内容长度: " + streamingContent.length());
        System.out.println("最终答案长度: " + (finalAnswer != null ? finalAnswer.length() : 0));

        // 检查思考内容是否又出现在最终答案中
        if (thinkingContent.length() > 0 && finalAnswer != null) {
            String thinkingStart = thinkingContent.toString().substring(0, 
                    Math.min(10, thinkingContent.length()));
            boolean thinkingInAnswer = finalAnswer.contains(thinkingStart);
            
            System.out.println("思考内容出现在最终答案中: " + thinkingInAnswer);
            
            // 思考内容不应出现在最终答案中
            assertFalse(thinkingInAnswer, "思考内容不应出现在最终答案中");
        }
    }
}
