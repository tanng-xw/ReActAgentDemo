package com.kimi.agent.service;

import com.kimi.agent.model.ChatMessage;
import com.kimi.agent.model.ChatResponse;
import com.kimi.agent.model.ChatSession;
import com.kimi.agent.tool.ObservingToolCallingManager;
import com.kimi.agent.tool.ToolContext;
import com.kimi.agent.tool.ToolContextHolder;
import com.kimi.agent.tools.AgentTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;
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
 * 使用 ChatClient 处理用户输入，通过自定义 ToolCallingManager 捕获中间步骤
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

    /** 系统提示词缓存 */
    private String cachedSystemPrompt = null;

    /** 系统提示词模板资源 */
    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemPromptResource;

    public ChatService(ChatClient.Builder chatClientBuilder, AgentTools agentTools) {
        // 注意：@Value 注入在构造后才完成，所以这里不能调用 buildSystemPrompt()
        // 系统提示词将在每次请求时通过 .system() 方法添加
        // ChatClient 会自动处理工具调用循环，通过 ObservingToolCallingManager 捕获中间步骤
        this.chatClient = chatClientBuilder
                .defaultTools(agentTools)
                .build();
    }

    /**
     * 处理用户消息，支持流式响应
     * 使用 ChatClient 的自动工具调用功能
     * 通过 ThreadLocal 将回调传递给 ObservingToolCallingManager
     * 
     * @param userMessage 用户消息
     * @param sessionId 会话ID
     * @param responseConsumer 响应消费者（用于流式返回）
     */
    public void processMessage(String userMessage, String sessionId, Consumer<com.kimi.agent.model.ChatResponse> responseConsumer) {
        try {
            // 获取或创建会话
            ChatSession session = ChatSession.getOrCreate(sessionId);

            logger.info("处理用户消息，会话ID: {}, 消息: {}", sessionId, userMessage);

            // 发送初始思考提示
            responseConsumer.accept(com.kimi.agent.model.ChatResponse.thinking("正在思考问题...", sessionId));

            // 创建工具上下文，包含 SessionId 等内部参数
            ToolContext toolContext = ToolContext.create()
                    .setSessionId(sessionId)
                    .setChatSession(session);
            
            ToolContextHolder.setContext(toolContext);
            ObservingToolCallingManager.setCallback(responseConsumer);

            StringBuilder contentBuilder = new StringBuilder();
            try {
                // 构建对话历史消息列表（不包含当前用户消息）
                List<Message> historyMessages = buildHistoryMessages(session);

                // 使用 ChatClient 进行流式对话
                logger.info("开始流式调用模型...");
                
                // Spring AI 的 stream() 返回 StreamResponseSpec，我们需要订阅它
                chatClient.prompt()
                        .system(buildSystemPrompt())
                        .messages(historyMessages)
                        .user(userMessage)
                        .stream()
                        .content()
                        .subscribe(
                                chunk -> {
                                    // 处理每个流式响应块
                                    if (chunk != null && !chunk.isEmpty()) {
                                        contentBuilder.append(chunk);
                                        
                                        // 检查是否已经接收到 "回答：" 标记
                                        String currentContent = contentBuilder.toString();
                                        int answerIndex = currentContent.indexOf(FINAL_ANSWER_PREFIX);
                                        
                                        if (answerIndex >= 0) {
                                            // 只发送 "回答：" 之后的内容
                                            String answerContent = currentContent.substring(answerIndex + FINAL_ANSWER_PREFIX.length());
                                            
                                            // 计算这次新增的 answer 内容
                                            int prevAnswerIndex = (contentBuilder.length() - chunk.length() - FINAL_ANSWER_PREFIX.length());
                                            if (prevAnswerIndex < answerIndex) {
                                                // 这是第一次收到 "回答：" 之后的 chunk
                                                // 只发送超出之前内容的部分
                                                responseConsumer.accept(com.kimi.agent.model.ChatResponse.streaming(answerContent, sessionId));
                                            } else {
                                                // 继续发送新增的 chunk（但只发送属于 answer 的部分）
                                                int alreadySent = contentBuilder.length() - chunk.length() - answerIndex - FINAL_ANSWER_PREFIX.length();
                                                if (alreadySent >= 0 && alreadySent < answerContent.length()) {
                                                    String newChunk = answerContent.substring(alreadySent);
                                                    if (!newChunk.isEmpty()) {
                                                        responseConsumer.accept(com.kimi.agent.model.ChatResponse.streaming(newChunk, sessionId));
                                                    }
                                                }
                                            }
                                        }
                                        // 如果还没有收到 "回答："，不发送流式内容（思考过程不显示）
                                    }
                                },
                                error -> {
                                    // 处理错误
                                    logger.error("流式处理时发生错误", error);
                                    responseConsumer.accept(com.kimi.agent.model.ChatResponse.error("流式处理错误: " + error.getMessage(), sessionId));
                                    ToolContextHolder.clear();
                                    ObservingToolCallingManager.clearCallback();
                                },
                                () -> {
                                    // 流式处理完成
                                    String fullContent = contentBuilder.toString();
                                    logger.info("流式响应完成，总长度: {}", fullContent.length());
                                    
                                    // 保存消息到会话
                                    session.addMessage(new ChatMessage(ChatMessage.MessageType.USER, userMessage));
                                    session.addMessage(new ChatMessage(ChatMessage.MessageType.ASSISTANT, fullContent));
                                    
                                    // 发送最终答案（只发送 "回答：" 之后的部分）
                                    parseAndSendFinalResponse(fullContent, sessionId, responseConsumer);
                                    
                                    // 清除上下文
                                    ToolContextHolder.clear();
                                    ObservingToolCallingManager.clearCallback();
                                }
                        );

            } finally {
                // 确保清除上下文（如果上面未完成）
                ToolContextHolder.clear();
                ObservingToolCallingManager.clearCallback();
            }

        } catch (Exception e) {
            logger.error("处理消息时发生错误", e);
            responseConsumer.accept(com.kimi.agent.model.ChatResponse.error("处理消息时发生错误: " + e.getMessage(), sessionId));
            ToolContextHolder.clear();
            ObservingToolCallingManager.clearCallback();
        }
    }

    /**
     * 解析最终响应，分离思考过程和最终答案
     * 在流式完成后调用，发送最终结果
     * 
     * @param content 完整模型响应内容
     * @param sessionId 会话ID
     * @param responseConsumer 响应消费者
     */
    private void parseAndSendFinalResponse(String content, String sessionId, Consumer<ChatResponse> responseConsumer) {
        if (content == null || content.isEmpty()) {
            responseConsumer.accept(com.kimi.agent.model.ChatResponse.error("模型返回空响应", sessionId));
            return;
        }

        // 检查是否包含最终答案标记
        int finalAnswerIndex = content.indexOf(FINAL_ANSWER_PREFIX);
        
        if (finalAnswerIndex >= 0) {
            // 提取最终答案
            String finalAnswer = content.substring(finalAnswerIndex + FINAL_ANSWER_PREFIX.length()).trim();
            
            // 发送最终答案
            responseConsumer.accept(com.kimi.agent.model.ChatResponse.finalAnswer(finalAnswer, sessionId));
        } else {
            // 没有标记，将整个内容作为最终答案
            responseConsumer.accept(com.kimi.agent.model.ChatResponse.finalAnswer(content, sessionId));
        }
    }

    /**
     * 构建对话历史消息列表（转换为 Spring AI Message 对象）
     * 只包含 USER 和 ASSISTANT 消息，工具调用细节已包含在 ASSISTANT 消息中
     * 
     * @param session 会话
     * @return 对话历史消息列表
     */
    private List<Message> buildHistoryMessages(ChatSession session) {
        List<Message> messages = new ArrayList<>();
        
        for (ChatMessage msg : session.getMessages()) {
            switch (msg.getType()) {
                case USER:
                    messages.add(new UserMessage(msg.getContent()));
                    break;
                case ASSISTANT:
                    messages.add(new AssistantMessage(msg.getContent()));
                    break;
                case TOOL_CALL:
                    // 工具调用消息已在 ASSISTANT 消息中体现，不需要单独添加
                    // 这些消息主要用于前端展示和调试
                    break;
                default:
                    break;
            }
        }
        return messages;
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
