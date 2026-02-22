package com.kimi.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import java.util.List;
import java.util.function.Consumer;

/**
 * 可观察的工具调用管理器
 * 包装 DefaultToolCallingManager，在工具调用前后发送事件到前端
 * 
 * @author Kimi
 */
public class ObservingToolCallingManager implements ToolCallingManager {

    private static final Logger logger = LoggerFactory.getLogger(ObservingToolCallingManager.class);

    /** ThreadLocal 存储当前请求的回调函数 */
    private static final ThreadLocal<Consumer<com.kimi.agent.model.ChatResponse>> CALLBACK_HOLDER = new ThreadLocal<>();
    
    /** ThreadLocal 存储当前会话ID */
    private static final ThreadLocal<String> SESSION_ID_HOLDER = new ThreadLocal<>();

    /** 委托的 ToolCallingManager */
    private final ToolCallingManager delegate;

    public ObservingToolCallingManager(ToolCallbackResolver toolCallbackResolver) {
        // 使用 DefaultToolCallingManager.builder() 构建委托实例
        this.delegate = DefaultToolCallingManager.builder()
                .toolCallbackResolver(toolCallbackResolver)
                .build();
    }

    /**
     * 设置当前请求的上下文
     * 
     * @param callback 响应回调
     * @param sessionId 会话ID
     */
    public static void setContext(Consumer<com.kimi.agent.model.ChatResponse> callback, String sessionId) {
        CALLBACK_HOLDER.set(callback);
        SESSION_ID_HOLDER.set(sessionId);
        logger.debug("设置 ToolCallingManager 上下文 - 会话ID: {}", sessionId);
    }

    /**
     * 清除当前请求的上下文
     */
    public static void clearContext() {
        CALLBACK_HOLDER.remove();
        SESSION_ID_HOLDER.remove();
        logger.debug("清除 ToolCallingManager 上下文");
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, org.springframework.ai.chat.model.ChatResponse chatResponse) {
        Consumer<com.kimi.agent.model.ChatResponse> callback = CALLBACK_HOLDER.get();
        String sessionId = SESSION_ID_HOLDER.get();

        // 在工具调用前，发送思考内容和工具调用信息
        if (callback != null && sessionId != null && chatResponse.getResult() != null) {
            AssistantMessage assistantMessage = (AssistantMessage) chatResponse.getResult().getOutput();
            
            // 发送思考内容
            String content = assistantMessage.getText();
            if (content != null && !content.isEmpty()) {
                logger.info("发送思考内容到前端: {}", content);
                callback.accept(com.kimi.agent.model.ChatResponse.thinking(content, sessionId));
            }
            
            // 发送工具调用信息（包含参数）
            if (assistantMessage.hasToolCalls()) {
                assistantMessage.getToolCalls().forEach(toolCall -> {
                    String toolName = toolCall.name();
                    String arguments = toolCall.arguments();
                    logger.info("发送工具调用信息到前端: {}, 参数: {}", toolName, arguments);
                    
                    // 发送包含参数的工具调用响应
                    com.kimi.agent.model.ChatResponse toolResponse = 
                        com.kimi.agent.model.ChatResponse.toolCallResult(toolName, arguments, null, sessionId);
                    callback.accept(toolResponse);
                });
            }
        }

        // 调用委托的工具执行逻辑
        ToolExecutionResult result = delegate.executeToolCalls(prompt, chatResponse);
        
        logger.info("工具执行完成，返回对话历史消息数: {}", 
                result.conversationHistory().size());
        
        // 从对话历史中提取工具执行结果
        if (callback != null && sessionId != null) {
            extractAndSendToolResults(result, sessionId, callback);
        }
        
        return result;
    }
    
    /**
     * 从 ToolExecutionResult 中提取工具执行结果并发送给前端
     * 
     * @param result 工具执行结果
     * @param sessionId 会话ID
     * @param callback 回调函数
     */
    private void extractAndSendToolResults(ToolExecutionResult result, String sessionId, 
                                          Consumer<com.kimi.agent.model.ChatResponse> callback) {
        // 从对话历史中查找 ToolResponseMessage 来获取工具执行结果
        for (org.springframework.ai.chat.messages.Message message : result.conversationHistory()) {
            if (message instanceof org.springframework.ai.chat.messages.ToolResponseMessage) {
                org.springframework.ai.chat.messages.ToolResponseMessage toolResponseMsg = 
                    (org.springframework.ai.chat.messages.ToolResponseMessage) message;
                
                toolResponseMsg.getResponses().forEach(toolResponse -> {
                    String toolName = toolResponse.name();
                    String responseData = toolResponse.responseData();
                    logger.info("发送工具执行结果到前端: {}, 结果: {}", toolName, responseData);
                    
                    // 发送包含结果的工具调用响应
                    com.kimi.agent.model.ChatResponse resultResponse = 
                        com.kimi.agent.model.ChatResponse.toolCallResult(toolName, null, responseData, sessionId);
                    callback.accept(resultResponse);
                });
            }
        }
    }
}
