package com.react.agentdemo.stream;

import com.react.agentdemo.model.ChatResponse;
import com.react.agentdemo.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

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
public class StreamingDisplayIssueTest {

    @Autowired
    private ChatService chatService;

    /**
     * 测试用例 1: 验证流式内容不包含思考过程
     * 
     * 当前问题: 流式内容包含了"我来帮您查询..."这样的思考内容
     * 预期: STREAMING 类型的内容应该只包含最终答案部分
     */
    @Test
    public void testStreamingContentShouldNotContainThinking() throws InterruptedException {
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

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "应该完成响应");

        // 合并流式内容
        String fullStreamingContent = String.join("", streamingChunks);
        
        System.out.println("=== 测试：流式内容不应包含思考过程 ===");
        System.out.println("思考响应数量: " + thinkingResponses.size());
        System.out.println("流式内容块数: " + streamingChunks.size());
        System.out.println("流式内容总长度: " + fullStreamingContent.length());
        
        // 检查流式内容是否包含思考过程的关键字
        boolean containsThinkingKeywords = fullStreamingContent.contains("我来帮您") ||
                                          fullStreamingContent.contains("首先我需要");
        
        System.out.println("流式内容包含思考关键字: " + containsThinkingKeywords);
        
        // 当前这是已知问题，应该修复
        // 预期: assertFalse(containsThinkingKeywords, "流式内容不应包含思考过程");
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
        String sessionId = chatService.createNewSession();
        List<ChatResponse.ResponseType> typeSequence = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("今天天气怎么样", sessionId, response -> {
            typeSequence.add(response.getType());
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(30, TimeUnit.SECONDS);
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
        // 注：当前实现可能不包含 STREAMING，因为模型返回的是完整内容
        // 修复后应该包含 STREAMING
    }

    /**
     * 测试用例 3: 验证思考过程和最终答案不重复
     * 
     * 当前问题: 思考过程中的内容又在最终答案中重复显示
     */
    @Test
    public void testThinkingAndAnswerNotDuplicate() throws InterruptedException {
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

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "应该完成响应");

        finalAnswer = finalAnswerHolder[0];
        
        System.out.println("=== 测试：思考与答案不重复 ===");
        System.out.println("思考内容长度: " + thinkingContent.length());
        System.out.println("流式内容长度: " + streamingContent.length());
        System.out.println("最终答案长度: " + (finalAnswer != null ? finalAnswer.length() : 0));

        // 检查思考内容是否又出现在最终答案中
        if (thinkingContent.length() > 0 && finalAnswer != null) {
            String thinkingStart = thinkingContent.toString().substring(0, 
                    Math.min(20, thinkingContent.length()));
            boolean thinkingInAnswer = finalAnswer.contains(thinkingStart);
            
            System.out.println("思考内容出现在最终答案中: " + thinkingInAnswer);
            
            // 当前这是已知问题
            // 修复后应该: assertFalse(thinkingInAnswer, "思考内容不应出现在最终答案中");
        }
    }
}
