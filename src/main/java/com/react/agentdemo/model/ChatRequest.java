package com.react.agentdemo.model;

/**
 * 聊天请求模型
 * 
 * @author Kimi
 */
public class ChatRequest {

    /** 用户输入内容 */
    private String message;
    
    /** 会话ID，用于保持上下文 */
    private String sessionId;

    public ChatRequest() {
    }

    public ChatRequest(String message, String sessionId) {
        this.message = message;
        this.sessionId = sessionId;
    }

    // Getter 和 Setter 方法

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public String toString() {
        return "ChatRequest{" +
                "message='" + message + '\'' +
                ", sessionId='" + sessionId + '\'' +
                '}';
    }
}
