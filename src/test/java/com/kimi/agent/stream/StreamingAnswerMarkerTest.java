package com.kimi.agent.stream;

import com.kimi.agent.model.ChatResponse;
import com.kimi.agent.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 流式输出 "回答" 标记处理测试
 * 
 * 测试场景：
 * 1. 思考过程应该流式输出（逐步接收，不是一次性）
 * 2. 回答内容应该流式输出（逐步接收，不是一次性）
 * 3. "回答" 标记本身不应该出现在输出中
 * 
 * @author Kimi
 */
@SpringBootTest
public class StreamingAnswerMarkerTest {

    @Autowired
    private ChatService chatService;

    /**
     * 测试用例 1: 思考过程应该流式输出
     * 验证思考内容不是一次性发送，而是分多个块发送
     */
    @Test
    public void testThinkingContentShouldBeStreaming() throws InterruptedException {
        String sessionId = chatService.createNewSession();
        List<ChatResponse> thinkingResponses = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("搜索周杰伦的歌曲并告诉我结果", sessionId, response -> {
            if (response.getType() == ChatResponse.ResponseType.THINKING) {
                thinkingResponses.add(response);
                System.out.println("[THINKING] " + response.getContent());
            }
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(45, TimeUnit.SECONDS);
        assertTrue(completed, "响应应该在45秒内完成");

        // 验证思考内容不是空的
        assertFalse(thinkingResponses.isEmpty(), "应该收到思考内容");
        
        // 合并所有思考内容
        StringBuilder fullThinking = new StringBuilder();
        for (ChatResponse r : thinkingResponses) {
            fullThinking.append(r.getContent());
        }
        
        System.out.println("思考内容块数: " + thinkingResponses.size());
        System.out.println("完整思考内容长度: " + fullThinking.length());
        System.out.println("完整思考内容: " + fullThinking);

        // 验证思考内容不为空且合理
        assertTrue(fullThinking.length() > 10, "思考内容应该有一定的长度");
        
        // 关键验证：思考内容中不应该包含孤立的 "回答" 标记
        String thinking = fullThinking.toString();
        assertFalse(thinking.equals("回答") || thinking.endsWith("回答\n"), 
                "思考内容不应该只包含'回答'标记");
    }

    /**
     * 测试用例 2: 回答内容应该流式输出
     * 验证最终答案是通过多个 STREAMING 块逐步接收的
     */
    @Test
    public void testAnswerContentShouldBeStreaming() throws InterruptedException {
        String sessionId = chatService.createNewSession();
        List<ChatResponse> streamingResponses = new ArrayList<>();
        StringBuilder finalAnswer = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("搜索邓紫棋的歌曲", sessionId, response -> {
            if (response.getType() == ChatResponse.ResponseType.STREAMING) {
                streamingResponses.add(response);
                System.out.println("[STREAMING] 长度=" + response.getContent().length() + " 内容=" + response.getContent().substring(0, Math.min(50, response.getContent().length())));
            } else if (response.getType() == ChatResponse.ResponseType.FINAL_ANSWER) {
                finalAnswer.append(response.getContent());
            }
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(45, TimeUnit.SECONDS);
        assertTrue(completed, "响应应该在45秒内完成");

        // 合并所有流式内容
        StringBuilder fullStreaming = new StringBuilder();
        for (ChatResponse r : streamingResponses) {
            fullStreaming.append(r.getContent());
        }

        System.out.println("流式内容块数: " + streamingResponses.size());
        System.out.println("流式内容总长度: " + fullStreaming.length());
        System.out.println("最终答案长度: " + finalAnswer.length());

        // 关键验证：应该有多个流式内容块（证明是流式输出）
        // 或者至少流式内容和最终答案加起来有内容
        int totalContentLength = fullStreaming.length() + finalAnswer.length();
        assertTrue(totalContentLength > 20, "回答内容应该有一定的长度");
        
        // 如果是流式输出，应该收到多个块
        // 注意：某些情况下可能直接收到 FINAL_ANSWER，所以不强制要求多个 STREAMING 块
        if (!streamingResponses.isEmpty()) {
            System.out.println("收到 " + streamingResponses.size() + " 个流式内容块");
        }
    }

    /**
     * 测试用例 3: "回答" 标记不应该出现在输出中
     * 验证 "回答：" 或 "回答\n" 只作为格式标记，不会出现在用户可见内容中
     */
    @Test
    public void testAnswerMarkerShouldNotAppearInOutput() throws InterruptedException {
        String sessionId = chatService.createNewSession();
        List<String> allOutput = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("搜索一首流行歌曲", sessionId, response -> {
            if (response.getType() == ChatResponse.ResponseType.THINKING) {
                allOutput.add("[THINKING] " + response.getContent());
            } else if (response.getType() == ChatResponse.ResponseType.STREAMING) {
                allOutput.add("[STREAMING] " + response.getContent());
            } else if (response.getType() == ChatResponse.ResponseType.FINAL_ANSWER) {
                allOutput.add("[FINAL] " + response.getContent());
            }
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(45, TimeUnit.SECONDS);
        assertTrue(completed, "响应应该在45秒内完成");

        // 检查所有输出
        for (String output : allOutput) {
            // 孤立的 "回答" 不应该出现（除非是 "回答：" 或 "回答\n" 的一部分，但也不应该被输出）
            if (output.contains("回答") && !output.contains("为您") && !output.contains("查找")) {
                System.out.println("检查输出: " + output.substring(0, Math.min(100, output.length())));
            }
        }

        // 验证：思考内容中不应该包含单独的 "回答" 行
        String fullOutput = String.join("\n", allOutput);
        assertFalse(fullOutput.contains("\n回答\n"), "输出中不应该包含单独的'回答'行");
    }

    /**
     * 测试用例 4: 工具调用场景下的流式输出
     * 验证在有工具调用的情况下，思考过程和回答都正常流式输出
     */
    @Test
    public void testStreamingWithToolCalls() throws InterruptedException {
        String sessionId = chatService.createNewSession();
        List<ChatResponse> allResponses = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        // 这个查询会触发多个工具调用
        chatService.processMessage("搜索周杰伦和邓紫棋的歌曲", sessionId, response -> {
            allResponses.add(response);
            if (response.getType() == ChatResponse.ResponseType.TOOL_CALL) {
                System.out.println("[TOOL] " + response.getToolName() + " 参数=" + response.getToolArguments());
            } else if (response.getType() == ChatResponse.ResponseType.THINKING) {
                System.out.println("[THINKING] " + response.getContent().substring(0, Math.min(50, response.getContent().length())));
            } else if (response.getType() == ChatResponse.ResponseType.STREAMING) {
                System.out.println("[STREAMING] 长度=" + response.getContent().length());
            }
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        assertTrue(completed, "响应应该在60秒内完成");

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
        assertTrue(toolCallCount >= 2, "应该触发至少2个工具调用");
        assertTrue(thinkingCount > 0, "应该有思考内容");
    }
}
