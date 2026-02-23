package com.kimi.agent.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天响应模型
 * 支持流式返回多个部分（思考过程、工具调用、最终回答）
 * 
 * @author Kimi
 */
public class ChatResponse {

    /** 响应类型枚举 */
    public enum ResponseType {
        /** 思考过程 */
        THINKING,
        /** 工具调用 */
        TOOL_CALL,
        /** 流式内容 */
        STREAMING,
        /** 最终回答 */
        FINAL_ANSWER,
        /** 错误 */
        ERROR
    }

    /** 响应类型 */
    private ResponseType type;
    
    /** 响应内容 */
    private String content;
    
    /** 工具名称（当类型为 TOOL_CALL 时） */
    private String toolName;
    
    /** 工具参数（JSON格式，当类型为 TOOL_CALL 时） */
    private String toolArguments;
    
    /** 工具调用结果（当类型为 TOOL_CALL 时） */
    private String toolResult;
    
    /** 工具调用ID（唯一标识符） */
    private String toolCallId;
    
    /** 会话ID */
    private String sessionId;
    
    /** 是否完成 */
    private boolean done;
    
    /** 错误信息 */
    private String error;

    public ChatResponse() {
    }

    public ChatResponse(ResponseType type, String content) {
        this.type = type;
        this.content = content;
    }

    public ChatResponse(ResponseType type, String content, String sessionId) {
        this.type = type;
        this.content = content;
        this.sessionId = sessionId;
    }

    // Getter 和 Setter 方法

    public ResponseType getType() {
        return type;
    }

    public void setType(ResponseType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolArguments() {
        return toolArguments;
    }

    public void setToolArguments(String toolArguments) {
        this.toolArguments = toolArguments;
    }

    public String getToolResult() {
        return toolResult;
    }

    public void setToolResult(String toolResult) {
        this.toolResult = toolResult;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    /**
     * 创建思考过程响应
     * 
     * @param content 思考内容
     * @param sessionId 会话ID
     * @return 响应对象
     */
    public static ChatResponse thinking(String content, String sessionId) {
        return new ChatResponse(ResponseType.THINKING, content, sessionId);
    }

    /**
     * 创建工具调用响应
     * 
     * @param toolName 工具名称
     * @param sessionId 会话ID
     * @return 响应对象
     */
    public static ChatResponse toolCall(String toolName, String sessionId) {
        ChatResponse response = new ChatResponse(ResponseType.TOOL_CALL, "为您调用" + toolName + "工具。", sessionId);
        response.setToolName(toolName);
        return response;
    }

    /**
     * 创建工具调用响应（包含参数和结果）
     * 
     * @param toolName 工具名称
     * @param toolArguments 工具参数
     * @param toolResult 工具结果
     * @param sessionId 会话ID
     * @return 响应对象
     */
    public static ChatResponse toolCallResult(String toolName, String toolArguments, String toolResult, String sessionId) {
        return toolCallResult(toolName, toolArguments, toolResult, sessionId, null);
    }

    /**
     * 创建工具调用响应（包含参数、结果和调用ID）
     * 
     * @param toolName 工具名称
     * @param toolArguments 工具参数
     * @param toolResult 工具结果
     * @param sessionId 会话ID
     * @param toolCallId 工具调用ID
     * @return 响应对象
     */
    public static ChatResponse toolCallResult(String toolName, String toolArguments, String toolResult, String sessionId, String toolCallId) {
        ChatResponse response = new ChatResponse(ResponseType.TOOL_CALL, "", sessionId);
        response.setToolName(toolName);
        response.setToolArguments(toolArguments);
        response.setToolResult(toolResult);
        response.setToolCallId(toolCallId);
        return response;
    }

    /**
     * 创建流式内容响应
     * 
     * @param content 流式内容块
     * @param sessionId 会话ID
     * @return 响应对象
     */
    public static ChatResponse streaming(String content, String sessionId) {
        return new ChatResponse(ResponseType.STREAMING, content, sessionId);
    }

    /**
     * 创建最终回答响应
     * 
     * @param content 回答内容
     * @param sessionId 会话ID
     * @return 响应对象
     */
    public static ChatResponse finalAnswer(String content, String sessionId) {
        ChatResponse response = new ChatResponse(ResponseType.FINAL_ANSWER, content, sessionId);
        response.setDone(true);
        return response;
    }

    /**
     * 创建错误响应
     * 
     * @param error 错误信息
     * @param sessionId 会话ID
     * @return 响应对象
     */
    public static ChatResponse error(String error, String sessionId) {
        ChatResponse response = new ChatResponse(ResponseType.ERROR, null, sessionId);
        response.setError(error);
        response.setDone(true);
        return response;
    }

    @Override
    public String toString() {
        return "ChatResponse{" +
                "type=" + type +
                ", content='" + content + '\'' +
                ", toolName='" + toolName + '\'' +
                ", toolArguments='" + toolArguments + '\'' +
                ", toolResult='" + toolResult + '\'' +
                ", toolCallId='" + toolCallId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", done=" + done +
                ", error='" + error + '\'' +
                '}';
    }
}
