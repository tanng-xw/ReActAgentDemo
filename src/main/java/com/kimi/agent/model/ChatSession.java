package com.kimi.agent.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天会话管理器
 * 负责管理多个会话的上下文记忆
 * 
 * @author Kimi
 */
public class ChatSession {

    /** 会话ID */
    private final String sessionId;
    
    /** 消息历史记录 */
    private final List<ChatMessage> messages;
    
    /** 会话创建时间 */
    private final long createTime;

    /**
     * 全局会话存储
     */
    private static final ConcurrentHashMap<String, ChatSession> SESSIONS = new ConcurrentHashMap<>();

    /**
     * 私有构造函数，通过工厂方法创建实例
     * 
     * @param sessionId 会话ID
     */
    private ChatSession(String sessionId) {
        this.sessionId = sessionId;
        this.messages = new ArrayList<>();
        this.createTime = System.currentTimeMillis();
    }

    /**
     * 获取或创建会话
     * 
     * @param sessionId 会话ID
     * @return 会话实例
     */
    public static ChatSession getOrCreate(String sessionId) {
        return SESSIONS.computeIfAbsent(sessionId, ChatSession::new);
    }

    /**
     * 创建新会话
     * 
     * @return 新会话实例
     */
    public static ChatSession createNew() {
        String sessionId = UUID.randomUUID().toString();
        ChatSession session = new ChatSession(sessionId);
        SESSIONS.put(sessionId, session);
        return session;
    }

    /**
     * 移除会话
     * 
     * @param sessionId 会话ID
     */
    public static void remove(String sessionId) {
        SESSIONS.remove(sessionId);
    }

    /**
     * 获取会话ID
     * 
     * @return 会话ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 添加消息到会话
     * 
     * @param message 消息
     */
    public void addMessage(ChatMessage message) {
        messages.add(message);
    }

    /**
     * 获取所有消息
     * 
     * @return 消息列表
     */
    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    /**
     * 获取消息数量
     * 
     * @return 消息数量
     */
    public int getMessageCount() {
        return messages.size();
    }

    /**
     * 清空会话消息
     */
    public void clearMessages() {
        messages.clear();
    }

    /**
     * 获取会话创建时间
     * 
     * @return 创建时间戳
     */
    public long getCreateTime() {
        return createTime;
    }

    @Override
    public String toString() {
        return "ChatSession{" +
                "sessionId='" + sessionId + '\'' +
                ", messages.size=" + messages.size() +
                ", createTime=" + createTime +
                '}';
    }
}
