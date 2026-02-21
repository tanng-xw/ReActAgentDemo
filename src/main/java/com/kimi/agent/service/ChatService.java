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
import org.springframework.ai.chat.messages.UserMessage;


import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
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
    
    /** ToolCallbackResolver 用于解析工具回调 */
    private final ToolCallbackResolver toolCallbackResolver;
    
    /** Agent 工具 */
    private final AgentTools agentTools;

    /** 系统提示词缓存 */
    private String cachedSystemPrompt = null;

    /** 系统提示词模板资源 */
    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemPromptResource;

    public ChatService(ChatClient.Builder chatClientBuilder, ToolCallingManager toolCallingManager, 
                       ToolCallbackResolver toolCallbackResolver, AgentTools agentTools) {
        this.toolCallingManager = toolCallingManager;
        this.toolCallbackResolver = toolCallbackResolver;
        this.agentTools = agentTools;
        
        // 注意：@Value 注入在构造后才完成，所以这里不能调用 buildSystemPrompt()
        // 系统提示词将在每次请求时通过消息列表添加
        this.chatClient = chatClientBuilder.build();
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

            // 构建消息列表（不包含系统消息，因为 ChatClient 已经设置了 defaultSystem）
            List<Message> messages = buildMessages(session);
            
            // 获取工具回调
            List<ToolCallback> toolCallbacks = getToolCallbacks();
            logger.info("获取到 {} 个工具回调", toolCallbacks.size());
            
            // 创建工具调用选项
            ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(toolCallbacks)
                    .build();
            
            // 最大工具调用轮数，防止无限循环
            int maxToolCalls = 5;
            int toolCallCount = 0;
            
            while (toolCallCount < maxToolCalls) {
                // 创建提示（包含工具选项）
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
        // 通过 ToolCallbackResolver 解析所有可用工具的回调
        List<ToolCallback> callbacks = new ArrayList<>();
        
        // 定义所有可用工具的名称
        String[] toolNames = {"getUserLocation", "getWeather", "searchSongs"};
        
        for (String toolName : toolNames) {
            try {
                ToolCallback callback = toolCallbackResolver.resolve(toolName);
                if (callback != null) {
                    callbacks.add(callback);
                    logger.debug("成功解析工具回调: {}", toolName);
                }
            } catch (Exception e) {
                logger.warn("无法解析工具回调: {}", toolName, e);
            }
        }
        
        return callbacks;
    }

    /**
     * 构建消息列表（包含系统消息和历史对话）
     * 
     * @param session 会话
     * @return 消息列表
     */
    private List<Message> buildMessages(ChatSession session) {
        List<Message> messages = new ArrayList<>();
        
        // 添加系统消息（延迟加载）
        String systemPrompt = buildSystemPrompt();
        messages.add(new org.springframework.ai.chat.messages.SystemMessage(systemPrompt));
        
        // 添加历史对话（不包含当前用户消息，因为它已经在 session 中被添加了）
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
     * 必须从 classpath:/prompts/system-prompt.st 加载，如果加载失败则抛出异常
     * 
     * @return 系统提示词内容
     * @throws IllegalStateException 如果无法加载系统提示词模板
     */
    private String buildSystemPrompt() {
        if (cachedSystemPrompt != null) {
            return cachedSystemPrompt;
        }

        logger.info("开始加载系统提示词模板...");
        
        if (systemPromptResource == null) {
            logger.error("systemPromptResource 为 null，@Value 注入失败");
            throw new IllegalStateException("系统提示词资源注入失败，请检查 @Value 注解");
        }

        try {
            logger.info("系统提示词资源: {}", systemPromptResource);
            logger.info("系统提示词资源是否存在: {}", systemPromptResource.exists());
            logger.info("系统提示词资源 URI: {}", systemPromptResource.getURI());
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(systemPromptResource.getInputStream(), StandardCharsets.UTF_8))) {
                cachedSystemPrompt = reader.lines().collect(Collectors.joining("\n"));
                logger.info("成功加载系统提示词模板，长度: {}", cachedSystemPrompt.length());
                return cachedSystemPrompt;
            }
        } catch (IOException e) {
            logger.error("读取系统提示词模板失败: {}", systemPromptResource, e);
            throw new IllegalStateException("无法加载系统提示词模板: " + e.getMessage(), e);
        }
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
