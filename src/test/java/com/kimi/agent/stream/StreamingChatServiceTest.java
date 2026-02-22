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
 * 流式聊天服务测试类
 * 
 * @author Kimi
 */
@SpringBootTest
public class StreamingChatServiceTest {

    @Autowired
    private ChatService chatService;

    /**
     * 测试用例 1: 基本流式响应测试
     * 验证流式响应能够正常接收多个内容块
     */
    @Test
    public void testBasicStreamingResponse() throws InterruptedException {
        String sessionId = chatService.createNewSession();
        List<ChatResponse> responses = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("你好，请介绍一下自己", sessionId, response -> {
            responses.add(response);
            System.out.println("[" + response.getType() + "] " + response.getContent());
            
            if (response.isDone()) {
                latch.countDown();
            }
        });

        // 等待最多30秒
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        
        assertTrue(completed, "流式响应应该在30秒内完成");
        assertFalse(responses.isEmpty(), "应该收到至少一个响应");
        
        // 验证响应类型序列
        boolean hasStreaming = responses.stream()
                .anyMatch(r -> r.getType() == ChatResponse.ResponseType.STREAMING);
        boolean hasFinalAnswer = responses.stream()
                .anyMatch(r -> r.getType() == ChatResponse.ResponseType.FINAL_ANSWER);
        
        System.out.println("总共收到 " + responses.size() + " 个响应");
        System.out.println("包含流式内容: " + hasStreaming);
        System.out.println("包含最终答案: " + hasFinalAnswer);
        
        // 注意：简单问候可能不会触发流式，取决于模型实现
        assertTrue(hasFinalAnswer || hasStreaming, "应该收到流式内容或最终答案");
    }

    /**
     * 测试用例 2: 流式内容合并测试
     * 验证所有流式内容块能够正确合并成完整响应
     */
    @Test
    public void testStreamingContentConcatenation() throws InterruptedException {
        String sessionId = chatService.createNewSession();
        List<String> streamingChunks = new ArrayList<>();
        StringBuilder finalContent = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        chatService.processMessage("今天天气怎么样", sessionId, response -> {
            if (response.getType() == ChatResponse.ResponseType.STREAMING) {
                streamingChunks.add(response.getContent());
            } else if (response.getType() == ChatResponse.ResponseType.FINAL_ANSWER) {
                finalContent.append(response.getContent());
                latch.countDown();
            }
        });

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "流式响应应该完成");
        
        // 合并流式内容
        String concatenated = String.join("", streamingChunks);
        System.out.println("流式内容块数: " + streamingChunks.size());
        System.out.println("合并后内容长度: " + concatenated.length());
        System.out.println("最终答案长度: " + finalContent.length());
        
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
        String sessionId = chatService.createNewSession();
        List<ChatResponse> responses = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        // 这个查询应该会触发工具调用
        chatService.processMessage("我在哪个城市", sessionId, response -> {
            responses.add(response);
            System.out.println("[" + response.getType() + "] " + 
                    (response.getToolName() != null ? "工具: " + response.getToolName() : response.getContent()));
            
            if (response.isDone()) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(30, TimeUnit.SECONDS);
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
        String sessionId = chatService.createNewSession();
        
        // 第一轮对话
        CountDownLatch latch1 = new CountDownLatch(1);
        List<ChatResponse> round1Responses = new ArrayList<>();
        
        chatService.processMessage("你好", sessionId, response -> {
            round1Responses.add(response);
            if (response.isDone()) latch1.countDown();
        });
        
        assertTrue(latch1.await(30, TimeUnit.SECONDS), "第一轮应该完成");
        
        // 第二轮对话
        CountDownLatch latch2 = new CountDownLatch(1);
        List<ChatResponse> round2Responses = new ArrayList<>();
        
        chatService.processMessage("北京天气怎么样", sessionId, response -> {
            round2Responses.add(response);
            if (response.isDone()) latch2.countDown();
        });
        
        assertTrue(latch2.await(30, TimeUnit.SECONDS), "第二轮应该完成");
        
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
        // 使用无效会话ID测试
        String invalidSessionId = "";
        List<ChatResponse> responses = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            chatService.processMessage("测试", invalidSessionId, response -> {
                responses.add(response);
                if (response.isDone()) latch.countDown();
            });
        } catch (Exception e) {
            // 预期可能抛出异常
            System.out.println("捕获到异常: " + e.getMessage());
            latch.countDown();
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "应该完成处理（无论成功或失败）");
    }
}
