package com.react.agentdemo.tool;

import com.react.agentdemo.model.ChatSession;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具执行上下文
 * 包含工具执行所需的各种内部参数（SessionId、UserId 等）
 * 通过 ThreadLocal 传递给工具方法，对 AI 模型透明
 * 
 * @author Kimi
 */
public class ToolContext {
    
    private final Map<String, Object> attributes = new HashMap<>();
    
    private ToolContext() {
    }
    
    /**
     * 创建新的上下文
     * 
     * @return 新的 ToolContext 实例
     */
    public static ToolContext create() {
        return new ToolContext();
    }
    
    /**
     * 设置属性
     * 
     * @param key 键
     * @param value 值
     * @return 当前上下文（链式调用）
     */
    public ToolContext set(String key, Object value) {
        attributes.put(key, value);
        return this;
    }
    
    /**
     * 获取属性
     * 
     * @param key 键
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) attributes.get(key);
    }
    
    /**
     * 获取 SessionId
     * 
     * @return SessionId
     */
    public String getSessionId() {
        return get("sessionId");
    }
    
    /**
     * 设置 SessionId
     * 
     * @param sessionId 会话ID
     * @return 当前上下文（链式调用）
     */
    public ToolContext setSessionId(String sessionId) {
        return set("sessionId", sessionId);
    }
    
    /**
     * 获取 UserId
     * 
     * @return UserId
     */
    public String getUserId() {
        return get("userId");
    }
    
    /**
     * 设置 UserId
     * 
     * @param userId 用户ID
     * @return 当前上下文（链式调用）
     */
    public ToolContext setUserId(String userId) {
        return set("userId", userId);
    }
    
    /**
     * 获取 ChatSession
     * 
     * @return ChatSession
     */
    public ChatSession getChatSession() {
        return get("chatSession");
    }
    
    /**
     * 设置 ChatSession
     * 
     * @param chatSession 会话
     * @return 当前上下文（链式调用）
     */
    public ToolContext setChatSession(ChatSession chatSession) {
        return set("chatSession", chatSession);
    }
    
    /**
     * 获取所有属性
     * 
     * @return 属性 Map
     */
    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }
    
    @Override
    public String toString() {
        return "ToolContext{" + attributes + '}';
    }
}
