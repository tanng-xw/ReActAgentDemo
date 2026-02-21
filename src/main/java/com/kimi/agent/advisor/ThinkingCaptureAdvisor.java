package com.kimi.agent.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 思考过程捕获 Advisor
 * 捕获模型响应中的思考内容和工具调用，实时发送给前端
 * 
 * @author Kimi
 */
public class ThinkingCaptureAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(ThinkingCaptureAdvisor.class);

    /** 用于存储每个会话的中间步骤回调 */
    private final Map<String, Consumer<IntermediateStep>> stepCallbacks = new ConcurrentHashMap<>();

    /** Advisor 名称 */
    private final String name;

    /** 执行顺序 */
    private final int order;

    public ThinkingCaptureAdvisor() {
        this("ThinkingCaptureAdvisor", 0);
    }

    public ThinkingCaptureAdvisor(String name, int order) {
        this.name = name;
        this.order = order;
    }

    /**
     * 注册会话的步骤回调
     * 
     * @param sessionId 会话ID
     * @param callback 回调函数
     */
    public void registerCallback(String sessionId, Consumer<IntermediateStep> callback) {
        stepCallbacks.put(sessionId, callback);
        logger.debug("为会话 {} 注册了步骤回调", sessionId);
    }

    /**
     * 注销会话的步骤回调
     * 
     * @param sessionId 会话ID
     */
    public void unregisterCallback(String sessionId) {
        stepCallbacks.remove(sessionId);
        logger.debug("为会话 {} 注销了步骤回调", sessionId);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // 从上下文中获取会话ID
        String sessionId = getSessionIdFromContext(request);
        
        // 执行下一个 advisor，获取响应
        ChatClientResponse response = chain.nextCall(request);
        
        // 处理响应，提取思考内容和工具调用
        processResponse(response, sessionId);
        
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        // 从上下文中获取会话ID
        String sessionId = getSessionIdFromContext(request);
        
        // 执行下一个 advisor，获取流式响应
        return chain.nextStream(request)
                .map(response -> {
                    // 处理每个响应块
                    processResponse(response, sessionId);
                    return response;
                });
    }

    /**
     * 处理响应，提取思考内容和工具调用
     * 
     * @param response 响应
     * @param sessionId 会话ID
     */
    private void processResponse(ChatClientResponse response, String sessionId) {
        if (response == null || response.chatResponse() == null) {
            return;
        }

        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse.getResults() == null || chatResponse.getResults().isEmpty()) {
            return;
        }

        Generation generation = chatResponse.getResult();
        if (generation == null || generation.getOutput() == null) {
            return;
        }

        // 提取思考内容
        String content = generation.getOutput().getText();
        if (content != null && !content.isEmpty()) {
            // 检查是否包含工具调用
            if (generation.getOutput().hasToolCalls()) {
                // 这是带有工具调用的思考
                sendStep(sessionId, IntermediateStep.thinking(content));
                
                // 发送工具调用信息
                generation.getOutput().getToolCalls().forEach(toolCall -> {
                    String toolName = toolCall.name();
                    sendStep(sessionId, IntermediateStep.toolCall(toolName, toolCall.arguments()));
                });
            } else if (!isFinalAnswer(content)) {
                // 纯思考内容（不是最终答案）
                sendStep(sessionId, IntermediateStep.thinking(content));
            }
        }
    }

    /**
     * 检查是否是最终答案
     * 
     * @param content 内容
     * @return 是否是最终答案
     */
    private boolean isFinalAnswer(String content) {
        return content != null && content.startsWith("回答：");
    }

    /**
     * 发送步骤到回调
     * 
     * @param sessionId 会话ID
     * @param step 中间步骤
     */
    private void sendStep(String sessionId, IntermediateStep step) {
        Consumer<IntermediateStep> callback = stepCallbacks.get(sessionId);
        if (callback != null) {
            try {
                callback.accept(step);
                logger.debug("会话 {} 发送步骤: {}", sessionId, step.getType());
            } catch (Exception e) {
                logger.error("发送步骤时发生错误", e);
            }
        }
    }

    /**
     * 从上下文中获取会话ID
     * 
     * @param request 请求
     * @return 会话ID
     */
    private String getSessionIdFromContext(ChatClientRequest request) {
        Map<String, Object> context = request.context();
        if (context != null && context.containsKey("sessionId")) {
            return context.get("sessionId").toString();
        }
        return "default";
    }

    /**
     * 中间步骤类
     */
    public static class IntermediateStep {
        private final StepType type;
        private final String content;
        private final String toolName;
        private final String toolArguments;

        private IntermediateStep(StepType type, String content, String toolName, String toolArguments) {
            this.type = type;
            this.content = content;
            this.toolName = toolName;
            this.toolArguments = toolArguments;
        }

        public static IntermediateStep thinking(String content) {
            return new IntermediateStep(StepType.THINKING, content, null, null);
        }

        public static IntermediateStep toolCall(String toolName, String arguments) {
            return new IntermediateStep(StepType.TOOL_CALL, null, toolName, arguments);
        }

        public StepType getType() {
            return type;
        }

        public String getContent() {
            return content;
        }

        public String getToolName() {
            return toolName;
        }

        public String getToolArguments() {
            return toolArguments;
        }

        public enum StepType {
            THINKING,
            TOOL_CALL
        }
    }
}
