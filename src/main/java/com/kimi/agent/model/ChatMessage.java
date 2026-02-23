package com.kimi.agent.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天消息模型
 * 
 * @author Kimi
 */
public class ChatMessage {

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        /** 用户消息 */
        USER,
        /** 助手思考过程 */
        THINKING,
        /** 工具调用信息 */
        TOOL_CALL,
        /** 助手最终回答 */
        ASSISTANT
    }

    /** 消息唯一标识 */
    private String id;
    
    /** 消息类型 */
    private MessageType type;
    
    /** 消息内容 */
    private String content;
    
    /** 消息创建时间 */
    private LocalDateTime timestamp;
    
    /** 工具名称（仅当类型为 TOOL_CALL 时有效） */
    private String toolName;
    
    /** 工具调用列表（用于 ASSISTANT 消息包含 tool_calls 的情况） */
    private List<ToolCall> toolCalls;
    
    /**
     * 工具调用内部类
     */
    public static class ToolCall {
        private String id;
        private String type;
        private Function function;
        
        public ToolCall() {}
        
        public ToolCall(String id, String type, String name, String arguments) {
            this.id = id;
            this.type = type;
            this.function = new Function(name, arguments);
        }
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Function getFunction() { return function; }
        public void setFunction(Function function) { this.function = function; }
        
        public static class Function {
            private String name;
            private String arguments;
            
            public Function() {}
            
            public Function(String name, String arguments) {
                this.name = name;
                this.arguments = arguments;
            }
            
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public String getArguments() { return arguments; }
            public void setArguments(String arguments) { this.arguments = arguments; }
        }
    }

    public ChatMessage() {
        this.timestamp = LocalDateTime.now();
    }

    public ChatMessage(MessageType type, String content) {
        this();
        this.type = type;
        this.content = content;
    }

    public ChatMessage(String id, MessageType type, String content) {
        this(type, content);
        this.id = id;
    }

    // Getter 和 Setter 方法
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }
    
    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }
    
    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    /**
     * 创建工具调用消息
     * 
     * @param toolName 工具名称
     * @return 工具调用消息
     */
    public static ChatMessage toolCall(String toolName) {
        ChatMessage message = new ChatMessage(MessageType.TOOL_CALL, "为您调用" + toolName + "工具。");
        message.setToolName(toolName);
        return message;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                ", toolName='" + toolName + '\'' +
                '}';
    }
}
