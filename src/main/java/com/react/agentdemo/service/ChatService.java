package com.react.agentdemo.service;

import com.react.agentdemo.model.ChatMessage;
import com.react.agentdemo.model.ChatResponse;
import com.react.agentdemo.model.ChatSession;
import com.react.agentdemo.tool.ObservingToolCallingManager;
import com.react.agentdemo.tool.ToolContext;
import com.react.agentdemo.tool.ToolContextHolder;
import org.springframework.ai.tool.ToolCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 聊天服务类
 * 使用 ChatClient 处理用户输入，通过 ChatMemory 管理对话历史
 * 
 * @author Kimi
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    /** 最终答案标记前缀 */
    private static final String FINAL_ANSWER_PREFIX = "回答：";
    
    /** 最终答案标记前缀（兼容模式，处理"回答"+换行的情况） */
    private static final String FINAL_ANSWER_PREFIX_ALT = "回答\n";

    /** ChatClient 是 Spring AI 推荐的高层抽象 API */
    private final ChatClient chatClient;

    /** 系统提示词缓存 */
    private String cachedSystemPrompt = null;

    /** 系统提示词模板资源 */
    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemPromptResource;

    public ChatService(ChatClient.Builder chatClientBuilder, List<ToolCallback> toolCallbacks) {
        // 创建 ChatMemory 和 Advisor
        // MessageChatMemoryAdvisor 会自动管理对话历史，通过 conversation ID 区分不同会话
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)  // 保留最近20条消息
                .build();
        
        // ChatClient 会自动处理工具调用循环，通过 ObservingToolCallingManager 捕获中间步骤
        // 使用 MessageChatMemoryAdvisor 自动管理对话历史
        // 使用从配置动态加载的工具回调
        this.chatClient = chatClientBuilder
                .defaultToolCallbacks(toolCallbacks)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * 处理用户消息，支持流式响应
     * 使用 ChatClient 的自动工具调用功能和 ChatMemory 管理历史
     * 
     * @param userMessage 用户消息
     * @param sessionId 会话ID
     * @param responseConsumer 响应消费者（用于流式返回）
     */
    public void processMessage(String userMessage, String sessionId, Consumer<ChatResponse> responseConsumer) {
        try {
            // 获取或创建会话（用于兼容现有代码）
            ChatSession session = ChatSession.getOrCreate(sessionId);

            logger.info("处理用户消息，会话ID: {}, 消息: {}", sessionId, userMessage);

            // 创建工具上下文，包含 SessionId 等内部参数
            ToolContext toolContext = ToolContext.create()
                    .setSessionId(sessionId)
                    .setChatSession(session);
            
            ToolContextHolder.setContext(toolContext);
            ObservingToolCallingManager.setCallback(responseConsumer, sessionId);

            StringBuilder contentBuilder = new StringBuilder();
            try {
                // 使用 ChatClient 进行流式对话
                // 使用 MessageChatMemoryAdvisor 自动管理对话历史
                logger.info("开始流式调用模型...");
                
                // 追踪已发送的思考内容长度，避免重复发送
                final int[] lastSentThinkingLength = {0};
                
                chatClient.prompt()
                        .system(buildSystemPrompt())
                        .user(userMessage)
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                        .stream()
                        .chatResponse()
                        .subscribe(
                                chatResponse -> {
                                    // 处理每个流式响应
                                    if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                                        String chunk = chatResponse.getResult().getOutput().getText();
                                        if (chunk != null && !chunk.isEmpty()) {
                                            String currentContent = contentBuilder.toString();
                                            int currentAnswerIndex = currentContent.indexOf(FINAL_ANSWER_PREFIX);
                                            int currentAnswerIndexAlt = currentContent.indexOf(FINAL_ANSWER_PREFIX_ALT);
                                            int effectiveCurrentIndex = (currentAnswerIndex >= 0) ? currentAnswerIndex : currentAnswerIndexAlt;
                                            
                                            contentBuilder.append(chunk);
                                            String newContent = contentBuilder.toString();
                                            int newAnswerIndex = newContent.indexOf(FINAL_ANSWER_PREFIX);
                                            int newAnswerIndexAlt = newContent.indexOf(FINAL_ANSWER_PREFIX_ALT);
                                            
                                            // 检查"回答："或"回答\n"标记
                                            boolean useAltPrefix = newAnswerIndex < 0 && newAnswerIndexAlt >= 0;
                                            int effectiveNewIndex = useAltPrefix ? newAnswerIndexAlt : newAnswerIndex;
                                            String effectivePrefix = useAltPrefix ? FINAL_ANSWER_PREFIX_ALT : FINAL_ANSWER_PREFIX;
                                            
                                            if (effectiveNewIndex >= 0) {
                                                // 已经收到 "回答：" 或 "回答\n" 标记
                                                if (effectiveCurrentIndex < 0) {
                                                    // 第一次收到标记，发送之前的思考内容
                                                    String thinkingContent = newContent.substring(0, effectiveNewIndex).trim();
                                                    if (!thinkingContent.isEmpty() && thinkingContent.length() > lastSentThinkingLength[0]) {
                                                        // 只发送新增的思考内容
                                                        String newThinking = thinkingContent.substring(lastSentThinkingLength[0]);
                                                        responseConsumer.accept(ChatResponse.thinking(newThinking, sessionId));
                                                        lastSentThinkingLength[0] = thinkingContent.length();
                                                    }
                                                    // 发送标记之后的内容
                                                    String answerContent = newContent.substring(effectiveNewIndex + effectivePrefix.length());
                                                    if (!answerContent.isEmpty()) {
                                                        responseConsumer.accept(ChatResponse.streaming(answerContent, sessionId));
                                                    }
                                                } else {
                                                    // 继续发送 answer 内容
                                                    int alreadySent = currentContent.length() - effectiveCurrentIndex - 
                                                            (currentAnswerIndex >= 0 ? FINAL_ANSWER_PREFIX.length() : FINAL_ANSWER_PREFIX_ALT.length());
                                                    String answerPart = newContent.substring(effectiveNewIndex + effectivePrefix.length());
                                                    if (alreadySent >= 0 && alreadySent < answerPart.length()) {
                                                        String newChunk = answerPart.substring(alreadySent);
                                                        if (!newChunk.isEmpty()) {
                                                            responseConsumer.accept(ChatResponse.streaming(newChunk, sessionId));
                                                        }
                                                    }
                                                }
                                            } else {
                                                // 还没有收到 "回答："，作为思考内容发送
                                                // 这种情况发生在工具调用前的推理过程
                                                if (newContent.length() > lastSentThinkingLength[0]) {
                                                    String newThinking = newContent.substring(lastSentThinkingLength[0]);
                                                    // 如果 newThinking 以 "回答" 结尾，可能是标记的一部分，暂不发送
                                                    if (newThinking.endsWith("回答")) {
                                                        // 等待下一个字符确认是否是标记
                                                        newThinking = newThinking.substring(0, newThinking.length() - 2);
                                                        if (!newThinking.isEmpty()) {
                                                            responseConsumer.accept(ChatResponse.thinking(newThinking, sessionId));
                                                        }
                                                        // 无论是否发送，都要更新长度（"回答"已处理）
                                                        lastSentThinkingLength[0] = newContent.length() - 2;
                                                    } else if (newThinking.endsWith("回")) {
                                                        // 如果 newThinking 以 "回" 结尾，可能是 "回答" 的一部分，暂不发送
                                                        newThinking = newThinking.substring(0, newThinking.length() - 1);
                                                        if (!newThinking.isEmpty()) {
                                                            responseConsumer.accept(ChatResponse.thinking(newThinking, sessionId));
                                                        }
                                                        // 无论是否发送，都要更新长度（"回"已处理）
                                                        lastSentThinkingLength[0] = newContent.length() - 1;
                                                    } else {
                                                        responseConsumer.accept(ChatResponse.thinking(newThinking, sessionId));
                                                        lastSentThinkingLength[0] = newContent.length();
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                error -> {
                                    // 处理错误
                                    logger.error("流式处理时发生错误", error);
                                    responseConsumer.accept(ChatResponse.error("流式处理错误: " + error.getMessage(), sessionId));
                                    ToolContextHolder.clear();
                                    ObservingToolCallingManager.clearCallback();
                                },
                                () -> {
                                    // 流式处理完成
                                    String fullContent = contentBuilder.toString();
                                    logger.info("流式响应完成，总长度: {}", fullContent.length());
                                    
                                    // 发送最终答案
                                    parseAndSendFinalResponse(fullContent, sessionId, responseConsumer);
                                    
                                    // 清除上下文
                                    ToolContextHolder.clear();
                                    ObservingToolCallingManager.clearCallback();
                                }
                        );

            } catch (Exception e) {
                // 同步模式下的异常处理
                logger.error("处理消息时发生错误", e);
                responseConsumer.accept(ChatResponse.error("处理消息时发生错误: " + e.getMessage(), sessionId));
                ToolContextHolder.clear();
                ObservingToolCallingManager.clearCallback();
                return;
            }
            
            // 流式模式下，清除操作在 onComplete 回调中进行
            // 不要在这里清除，否则回调会在流式完成前被清除

        } catch (Exception e) {
            logger.error("处理消息时发生错误", e);
            responseConsumer.accept(ChatResponse.error("处理消息时发生错误: " + e.getMessage(), sessionId));
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
            responseConsumer.accept(ChatResponse.error("模型返回空响应", sessionId));
            return;
        }

        // 检查是否包含最终答案标记（"回答：" 或 "回答\n"）
        int finalAnswerIndex = content.indexOf(FINAL_ANSWER_PREFIX);
        int finalAnswerIndexAlt = content.indexOf(FINAL_ANSWER_PREFIX_ALT);
        
        if (finalAnswerIndex >= 0 || finalAnswerIndexAlt >= 0) {
            // 确定使用哪个标记
            int effectiveIndex;
            String effectivePrefix;
            if (finalAnswerIndex >= 0 && (finalAnswerIndexAlt < 0 || finalAnswerIndex < finalAnswerIndexAlt)) {
                effectiveIndex = finalAnswerIndex;
                effectivePrefix = FINAL_ANSWER_PREFIX;
            } else {
                effectiveIndex = finalAnswerIndexAlt;
                effectivePrefix = FINAL_ANSWER_PREFIX_ALT;
            }
            // 提取标记之后的最终答案
            String finalAnswer = content.substring(effectiveIndex + effectivePrefix.length()).trim();
            if (!finalAnswer.isEmpty()) {
                responseConsumer.accept(ChatResponse.finalAnswer(finalAnswer, sessionId));
            } else {
                responseConsumer.accept(ChatResponse.finalAnswer("模型未提供具体回答", sessionId));
            }
        } else {
            // 如果没有 "回答：" 标记，将整个内容作为最终答案
            // 这种情况发生在简单查询不涉及工具调用时
            String trimmedContent = content.trim();
            if (!trimmedContent.isEmpty()) {
                responseConsumer.accept(ChatResponse.finalAnswer(trimmedContent, sessionId));
            } else {
                responseConsumer.accept(ChatResponse.finalAnswer("模型未返回有效内容", sessionId));
            }
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
            logger.info("系统提示词资源: {}", systemPromptResource.getURI());
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(systemPromptResource.getInputStream(), StandardCharsets.UTF_8))) {
                cachedSystemPrompt = reader.lines().collect(Collectors.joining("\n"));
                logger.info("成功加载系统提示词模板，长度: {}", cachedSystemPrompt.length());
                return cachedSystemPrompt;
            }
        } catch (IOException e) {
            logger.error("无法加载系统提示词模板", e);
            throw new IllegalStateException(
                "无法加载系统提示词模板，请检查文件是否存在: classpath:/prompts/system-prompt.st", e);
        }
    }

    /**
     * 创建新会话
     * 
     * @return 新会话ID
     */
    public String createNewSession() {
        String sessionId = java.util.UUID.randomUUID().toString();
        logger.info("创建新会话: {}", sessionId);
        return sessionId;
    }
    
    /**
     * 清除指定会话的历史记录
     * 注意：由于使用 Spring AI 的 ChatMemory，它会自动管理历史消息
     * 此方法主要用于兼容现有 API
     * 
     * @param sessionId 会话ID
     */
    public void clearSession(String sessionId) {
        logger.info("清除会话历史: {}", sessionId);
        // ChatMemory 会自动管理消息窗口，无需手动清除
        // 如果需要立即清除，可以创建新的 ChatClient 实例
    }
}
