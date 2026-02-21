package com.kimi.agent.service;

import com.kimi.agent.model.ChatMessage;
import com.kimi.agent.model.ChatResponse;
import com.kimi.agent.model.ChatSession;
import com.kimi.agent.tools.AgentTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 聊天服务类
 * 使用 ChatClient 和 ToolCallingManager 处理用户输入，捕获中间步骤
 * 
 * @author Kimi
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    /** 最终答案标记前缀 */
    private static final String FINAL_ANSWER_PREFIX = "回答：";

    /** ChatClient 是 Spring AI 推荐的高层抽象 API */
    private final ChatClient chatClient;
    
    /** ToolCallingManager 用于管理工具调用 */
    private final ToolCallingManager toolCallingManager;
    
    /** Agent 工具 */
    private final AgentTools agentTools;

    /** 系统提示词缓存 */
    private String cachedSystemPrompt = null;

    /** 系统提示词模板资源 */
    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemPromptResource;

    public ChatService(ChatClient.Builder chatClientBuilder, ToolCallingManager toolCallingManager, AgentTools agentTools) {
        this.toolCallingManager = toolCallingManager;
        this.agentTools = agentTools;
        // 使用 ChatClient.Builder 构建 ChatClient，**不**注册默认工具（使用 ToolCallingManager 手动管理）
        this.chatClient = chatClientBuilder
                .defaultSystem(buildSystemPrompt())
                .build();
    }

    /**
     * 处理用户消息，支持流式响应
     * 使用 ToolCallingManager 手动处理工具调用循环，捕获所有中间步骤
     * 
     * @param userMessage 用户消息
     * @param sessionId 会话ID
     * @param responseConsumer 响应消费者（用于流式返回）
     */
    public void processMessage(String userMessage, String sessionId, Consumer<ChatResponse> responseConsumer) {
        try {
            // 获取或创建会话
            ChatSession session = ChatSession.getOrCreate(sessionId);

            // 记录用户消息
            ChatMessage userChatMessage = new ChatMessage(ChatMessage.MessageType.USER, userMessage);
            userChatMessage.setId(String.valueOf(System.currentTimeMillis()));
            session.addMessage(userChatMessage);

            logger.info("处理用户消息，会话ID: {}, 消息: {}", sessionId, userMessage);

            // 发送初始思考提示
            responseConsumer.accept(ChatResponse.thinking("正在思考问题...", sessionId));

            // 构建消息列表
            List<Message> messages = buildMessages(session, userMessage);
            
            // 获取工具回调
            List<ToolCallback> toolCallbacks = getToolCallbacks();
            
            // 创建工具调用选项
            ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(toolCallbacks)
                    .build();
            
            // 最大工具调用轮数，防止无限循环
            int maxToolCalls = 5;
            int toolCallCount = 0;
            
            while (toolCallCount < maxToolCalls) {
                // 创建提示
                Prompt prompt = new Prompt(messages, toolOptions);
                
                // 调用模型
                org.springframework.ai.chat.model.ChatResponse chatResponse = chatClient.prompt(prompt).call().chatResponse();
                
                if (chatResponse == null || chatResponse.getResult() == null) {
                    logger.error("模型返回空响应");
                    responseConsumer.accept(ChatResponse.error("模型返回空响应", sessionId));
                    return;
                }

                org.springframework.ai.chat.model.Generation generation = chatResponse.getResult();
                AssistantMessage assistantMessage = (AssistantMessage) generation.getOutput();
                
                // 获取模型回复内容
                String content = assistantMessage.getText();
                logger.info("模型响应 (轮次 {}): {}", toolCallCount, content);
                
                // 检查是否有工具调用
                if (assistantMessage.hasToolCalls()) {
                    // 这是带有工具调用的思考过程
                    logger.info("检测到工具调用，数量: {}", assistantMessage.getToolCalls().size());
                    
                    // 发送思考内容到前端
                    if (content != null && !content.isEmpty()) {
                        responseConsumer.accept(ChatResponse.thinking(content, sessionId));
                    }
                    
                    // 发送工具调用信息到前端
                    for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                        String toolName = toolCall.name();
                        logger.info("准备执行工具: {}", toolName);
                        responseConsumer.accept(ChatResponse.toolCall(toolName, sessionId));
                    }
                    
                    // 使用 ToolCallingManager 执行工具调用
                    ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
                    
                    logger.info("工具执行完成，返回对话历史消息数: {}", 
                            toolExecutionResult.conversationHistory().size());
                    
                    // 从 ToolExecutionResult 获取更新后的对话历史
                    messages = toolExecutionResult.conversationHistory();
                    
                    toolCallCount++;
                } else {
                    // 没有工具调用，这是最终答案
                    logger.info("收到最终答案");
                    
                    // 添加助手消息到会话历史
                    session.addMessage(new ChatMessage(ChatMessage.MessageType.ASSISTANT, content));
                    
                    // 处理最终答案
                    parseAndSendResponse(content, sessionId, responseConsumer);
                    break;
                }
            }

            if (toolCallCount >= maxToolCalls) {
                logger.warn("达到最大工具调用次数限制");
                responseConsumer.accept(ChatResponse.error("处理时间过长，请重试", sessionId));
            }

        } catch (Exception e) {
            logger.error("处理消息时发生错误", e);
            responseConsumer.accept(ChatResponse.error("处理消息时发生错误: " + e.getMessage(), sessionId));
        }
    }

    /**
     * 获取工具回调列表
     * 
     * @return 工具回调列表
     */
    private List<ToolCallback> getToolCallbacks() {
        List<ToolCallback> callbacks = new ArrayList<>();
        // 通过 Spring AI 的工具解析机制获取工具回调
        // 这里我们依赖 ToolCallingManager 从 Spring 上下文中解析工具
        return callbacks;
    }

    /**
     * 构建消息列表
     * 
     * @param session 会话
     * @param userMessage 用户消息
     * @return 消息列表
     */
    private List<Message> buildMessages(ChatSession session, String userMessage) {
        List<Message> messages = new ArrayList<>();
        
        // 添加历史对话
        for (ChatMessage msg : session.getMessages()) {
            switch (msg.getType()) {
                case USER:
                    messages.add(new UserMessage(msg.getContent()));
                    break;
                case ASSISTANT:
                    messages.add(new AssistantMessage(msg.getContent()));
                    break;
                default:
                    break;
            }
        }
        
        // 添加当前用户消息
        messages.add(new UserMessage(userMessage));
        
        return messages;
    }

    /**
     * 解析模型响应，分离思考过程和最终答案
     * 
     * @param content 模型响应内容
     * @param sessionId 会话ID
     * @param responseConsumer 响应消费者
     */
    private void parseAndSendResponse(String content, String sessionId, Consumer<ChatResponse> responseConsumer) {
        if (content == null || content.isEmpty()) {
            responseConsumer.accept(ChatResponse.error("模型返回空响应", sessionId));
            return;
        }

        // 检查是否包含最终答案标记
        int finalAnswerIndex = content.indexOf(FINAL_ANSWER_PREFIX);
        
        if (finalAnswerIndex >= 0) {
            // 提取思考过程（标记前的内容）
            String thinking = content.substring(0, finalAnswerIndex).trim();
            if (!thinking.isEmpty()) {
                // 发送思考过程
                responseConsumer.accept(ChatResponse.thinking(thinking, sessionId));
            }

            // 提取最终答案
            String finalAnswer = content.substring(finalAnswerIndex + FINAL_ANSWER_PREFIX.length()).trim();
            
            // 发送最终答案
            responseConsumer.accept(ChatResponse.finalAnswer(finalAnswer, sessionId));
        } else {
            // 没有标记，将整个内容作为最终答案
            responseConsumer.accept(ChatResponse.finalAnswer(content, sessionId));
        }
    }

    /**
     * 构建系统提示词
     * 
     * @return 系统提示词内容
     */
    private String buildSystemPrompt() {
        if (cachedSystemPrompt != null) {
            return cachedSystemPrompt;
        }

        try {
            if (systemPromptResource != null && systemPromptResource.exists()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(systemPromptResource.getInputStream(), StandardCharsets.UTF_8))) {
                    cachedSystemPrompt = reader.lines().collect(Collectors.joining("\n"));
                    return cachedSystemPrompt;
                }
            }
        } catch (IOException e) {
            logger.error("读取系统提示词失败", e);
        }

        return getDefaultSystemPrompt();
    }

    /**
     * 获取默认系统提示词
     * 
     * @return 默认系统提示词
     */
    private String getDefaultSystemPrompt() {
        return """
                你是一个智能音乐助手，能够帮助用户查询信息、搜索歌曲等。

                重要规则：
                1. 你可以使用工具来帮助回答用户问题。
                2. 当你需要使用工具时，系统会自动调用相应的工具。
                3. 工具调用结果会自动返回给你，你可以基于结果继续思考。
                4. 在给出最终答案前，请先说明你的思考过程，包括是否调用了工具以及工具结果。
                5. **最终答案必须以"回答："开头**，这样系统才能识别并显示给用户。

                可用工具：
                1. getUserLocation - 查询用户所在的城市位置（北京、上海或杭州）
                2. getWeather - 查询指定城市的天气信息
                3. searchSongs - 根据关键词搜索歌曲
                """;
    }

    /**
     * 创建新会话
     * 
     * @return 新会话ID
     */
    public String createNewSession() {
        ChatSession session = ChatSession.createNew();
        return session.getSessionId();
    }

    /**
     * 清除会话
     * 
     * @param sessionId 会话ID
     */
    public void clearSession(String sessionId) {
        ChatSession.remove(sessionId);
    }
}
