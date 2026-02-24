package com.react.agentdemo.tool;

import com.react.agentdemo.model.ChatMessage;
import com.react.agentdemo.model.ChatSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 可观察的工具调用管理器
 * 包装 DefaultToolCallingManager，在工具调用前后发送事件到前端
 * 通过 ToolContextHolder 传递内部上下文参数
 * 
 * @author Kimi
 */
public class ObservingToolCallingManager implements ToolCallingManager {

    private static final Logger logger = LoggerFactory.getLogger(ObservingToolCallingManager.class);

    /** ThreadLocal 存储回调函数 */
    private static final ThreadLocal<Consumer<com.react.agentdemo.model.ChatResponse>> CALLBACK_HOLDER = new ThreadLocal<>();
    
    /** 全局回调存储（用于流式模式，跨线程访问） */
    private static volatile Consumer<com.react.agentdemo.model.ChatResponse> globalCallback = null;
    private static volatile String globalSessionId = null;

    /** 委托的 ToolCallingManager */
    private final ToolCallingManager delegate;

    public ObservingToolCallingManager(ToolCallbackResolver toolCallbackResolver) {
        this.delegate = DefaultToolCallingManager.builder()
                .toolCallbackResolver(toolCallbackResolver)
                .build();
    }

    /**
     * 设置回调函数
     * 
     * @param callback 响应回调
     */
    public static void setCallback(Consumer<com.react.agentdemo.model.ChatResponse> callback) {
        CALLBACK_HOLDER.set(callback);
        globalCallback = callback;
        logger.debug("设置回调函数");
    }
    
    /**
     * 设置回调函数和会话ID（用于流式模式）
     * 
     * @param callback 响应回调
     * @param sessionId 会话ID
     */
    public static void setCallback(Consumer<com.react.agentdemo.model.ChatResponse> callback, String sessionId) {
        CALLBACK_HOLDER.set(callback);
        globalCallback = callback;
        globalSessionId = sessionId;
        logger.debug("设置回调函数和会话ID: {}", sessionId);
    }

    /**
     * 清除回调函数
     */
    public static void clearCallback() {
        CALLBACK_HOLDER.remove();
        globalCallback = null;
        globalSessionId = null;
        logger.debug("清除回调函数");
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, org.springframework.ai.chat.model.ChatResponse chatResponse) {
        // 首先尝试从 ThreadLocal 获取，如果获取不到则使用全局变量（流式模式）
        Consumer<com.react.agentdemo.model.ChatResponse> callbackHolder = CALLBACK_HOLDER.get();
        if (callbackHolder == null) {
            callbackHolder = globalCallback;
            logger.debug("从全局变量获取回调函数");
        }
        final Consumer<com.react.agentdemo.model.ChatResponse> callback = callbackHolder;
        
        ToolContext context = ToolContextHolder.getContext();
        final String sessionId;
        if (context != null) {
            sessionId = context.getSessionId();
        } else if (globalSessionId != null) {
            sessionId = globalSessionId;
        } else {
            sessionId = "unknown";
        }
        ChatSession chatSession = context != null ? context.getChatSession() : null;
        
        logger.info("executeToolCalls - 会话ID: {}, 回调函数是否存在: {}", sessionId, callback != null);

        // 记录当前轮次的工具调用，用于后续匹配结果
        List<ToolCallInfo> currentToolCalls = new ArrayList<>();

        // 在工具调用前，发送思考内容和工具调用信息
        AssistantMessage assistantMessage = null;
        if (chatResponse.getResult() != null) {
            assistantMessage = (AssistantMessage) chatResponse.getResult().getOutput();
            
            // 发送思考内容到前端
            if (callback != null) {
                String content = assistantMessage.getText();
                if (content != null && !content.isEmpty()) {
                    logger.info("发送思考内容到前端: {}", content);
                    callback.accept(com.react.agentdemo.model.ChatResponse.thinking(content, sessionId));
                }
            }
            
            // 发送工具调用信息（包含参数）
            if (assistantMessage.hasToolCalls()) {
                assistantMessage.getToolCalls().forEach(toolCall -> {
                    String toolName = toolCall.name();
                    String arguments = toolCall.arguments();
                    String toolCallId = toolCall.id();
                    
                    // 记录当前轮次的工具调用
                    currentToolCalls.add(new ToolCallInfo(toolCallId, toolName, arguments));
                    
                    logger.info("发送工具调用信息到前端: {}, 参数: {}", toolName, arguments);
                    
                    // 发送包含参数的工具调用响应到前端（包含toolCallId）
                    if (callback != null) {
                        com.react.agentdemo.model.ChatResponse toolResponse = 
                            com.react.agentdemo.model.ChatResponse.toolCallResult(toolName, arguments, null, sessionId, toolCallId);
                        callback.accept(toolResponse);
                    }
                });
            }
        }

        // 调用委托的工具执行逻辑
        ToolExecutionResult result = delegate.executeToolCalls(prompt, chatResponse);
        
        logger.info("工具执行完成，返回对话历史消息数: {}", 
                result.conversationHistory().size());
        
        // 从对话历史中提取工具执行结果（只提取当前轮次的）并保存到 ChatSession
        if (!currentToolCalls.isEmpty()) {
            extractAndProcessToolResults(result, sessionId, callback, currentToolCalls, chatSession, assistantMessage);
        }
        
        return result;
    }
    
    /**
     * 工具调用信息内部类
     */
    private record ToolCallInfo(String id, String name, String arguments) {}
    
    /**
     * 从 ToolExecutionResult 中提取工具执行结果并处理
     * 只发送给前端，不保存到 ChatSession（由 ChatService 统一保存）
     * 只处理当前轮次的工具结果，避免重复处理历史结果
     * 
     * @param result 工具执行结果
     * @param sessionId 会话ID
     * @param callback 回调函数
     * @param currentToolCalls 当前轮次的工具调用信息列表
     * @param chatSession 聊天会话（可能为null，目前不用于保存消息）
     * @param assistantMessage 助手消息（包含思考内容和工具调用）
     */
    private void extractAndProcessToolResults(ToolExecutionResult result, String sessionId, 
                                              Consumer<com.react.agentdemo.model.ChatResponse> callback,
                                              List<ToolCallInfo> currentToolCalls,
                                              ChatSession chatSession,
                                              AssistantMessage assistantMessage) {
        // 注意：不在此处保存消息到 ChatSession，避免顺序错乱
        // 消息保存由 ChatService 在模型调用成功后统一处理
        
        // 从对话历史中查找 ToolResponseMessage 来获取工具执行结果
        for (org.springframework.ai.chat.messages.Message message : result.conversationHistory()) {
            if (message instanceof ToolResponseMessage) {
                ToolResponseMessage toolResponseMsg = (ToolResponseMessage) message;
                
                toolResponseMsg.getResponses().forEach(toolResponse -> {
                    String toolCallId = toolResponse.id();
                    String toolName = toolResponse.name();
                    String responseData = toolResponse.responseData();
                    
                    // 查找对应的工具调用信息
                    ToolCallInfo toolCallInfo = currentToolCalls.stream()
                            .filter(tc -> tc.id().equals(toolCallId))
                            .findFirst()
                            .orElse(null);
                    
                    // 只处理当前轮次的工具结果
                    if (toolCallInfo != null) {
                        logger.info("处理工具执行结果: {}, 结果: {}", toolName, responseData);
                        
                        // 发送结果给前端（包含参数、结果和toolCallId）
                        if (callback != null) {
                            com.react.agentdemo.model.ChatResponse resultResponse = 
                                com.react.agentdemo.model.ChatResponse.toolCallResult(
                                    toolName, toolCallInfo.arguments(), responseData, sessionId, toolCallInfo.id());
                            callback.accept(resultResponse);
                        }
                    }
                });
            }
        }
    }
}
