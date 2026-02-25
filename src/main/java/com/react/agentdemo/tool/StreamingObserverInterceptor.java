package com.react.agentdemo.tool;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 流式观察拦截器
 * 用于捕获模型的思考过程和工具调用，并发送到前端
 * 
 * @author Kimi
 */
public class StreamingObserverInterceptor extends ToolInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(StreamingObserverInterceptor.class);

    /** 响应回调映射 */
    private final ConcurrentHashMap<String, Consumer<com.react.agentdemo.model.ChatResponse>> callbackMap = new ConcurrentHashMap<>();
    
    /** 会话ID */
    private final ThreadLocal<String> sessionIdHolder = new ThreadLocal<>();

    /**
     * 注册回调函数
     * 
     * @param sessionId 会话ID
     * @param callback 响应回调
     */
    public void registerCallback(String sessionId, Consumer<com.react.agentdemo.model.ChatResponse> callback) {
        callbackMap.put(sessionId, callback);
        sessionIdHolder.set(sessionId);
        logger.debug("注册回调函数，会话ID: {}", sessionId);
    }

    /**
     * 注销回调函数
     * 
     * @param sessionId 会话ID
     */
    public void unregisterCallback(String sessionId) {
        callbackMap.remove(sessionId);
        logger.debug("注销回调函数，会话ID: {}", sessionId);
    }

    @Override
    public String getName() {
        return "StreamingObserverInterceptor";
    }

    /**
     * 拦截模型调用
     * 
     * @param request 模型请求
     * @param handler 模型调用处理器
     * @return 模型响应
     */
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        String sessionId = sessionIdHolder.get();
        Consumer<com.react.agentdemo.model.ChatResponse> callback = sessionId != null ? callbackMap.get(sessionId) : null;
        
        // 执行模型调用
        ModelResponse response = handler.call(request);
        
        // 提取思考内容
        org.springframework.ai.chat.model.ChatResponse chatResponse = response.getChatResponse();
        if (chatResponse != null && chatResponse.getResult() != null) {
            AssistantMessage message = (AssistantMessage) chatResponse.getResult().getOutput();
            if (message != null) {
                String content = message.getText();
                
                // 发送思考内容
                if (content != null && !content.isEmpty() && callback != null) {
                    logger.debug("发送思考内容: {}", content.substring(0, Math.min(50, content.length())));
                    callback.accept(com.react.agentdemo.model.ChatResponse.thinking(content, sessionId));
                }
                
                // 检查是否有工具调用
                if (message.hasToolCalls()) {
                    message.getToolCalls().forEach(toolCall -> {
                        String toolName = toolCall.name();
                        String arguments = toolCall.arguments();
                        String toolCallId = toolCall.id();
                        
                        logger.info("工具调用: {}，参数: {}", toolName, arguments);
                        
                        if (callback != null) {
                            callback.accept(com.react.agentdemo.model.ChatResponse.toolCallResult(
                                    toolName, arguments, null, sessionId, toolCallId));
                        }
                    });
                }
            }
        }
        
        return response;
    }

    /**
     * 拦截工具调用
     * 
     * @param request 工具调用请求
     * @param handler 工具调用处理器
     * @return 工具调用响应
     */
    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        String sessionId = sessionIdHolder.get();
        Consumer<com.react.agentdemo.model.ChatResponse> callback = sessionId != null ? callbackMap.get(sessionId) : null;
        
        String toolName = request.getToolName();
        String toolCallId = request.getToolCallId();
        
        logger.info("执行工具: {}，ID: {}", toolName, toolCallId);
        
        // 执行工具调用
        ToolCallResponse response = handler.call(request);
        
        // 发送工具执行结果
        if (callback != null) {
            String result = response.getResult();
            callback.accept(com.react.agentdemo.model.ChatResponse.toolCallResult(
                    toolName, null, result, sessionId, toolCallId));
        }
        
        return response;
    }
    
    /**
     * 设置当前会话ID
     * 
     * @param sessionId 会话ID
     */
    public void setCurrentSessionId(String sessionId) {
        sessionIdHolder.set(sessionId);
    }
    
    /**
     * 清除当前会话ID
     */
    public void clearSessionId() {
        sessionIdHolder.remove();
    }
}
