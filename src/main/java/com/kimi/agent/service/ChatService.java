package com.kimi.agent.service;

import com.kimi.agent.advisor.ThinkingCaptureAdvisor;
import com.kimi.agent.model.ChatMessage;
import com.kimi.agent.model.ChatResponse;
import com.kimi.agent.model.ChatSession;
import com.kimi.agent.tools.AgentTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 聊天服务类
 * 使用 ChatClient 高层抽象 API 处理用户输入
 * ChatClient 自动处理工具调用循环
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
    
    /** 思考捕获 Advisor */
    private final ThinkingCaptureAdvisor thinkingCaptureAdvisor;

    /** 系统提示词缓存 */
    private String cachedSystemPrompt = null;

    /** 系统提示词模板资源 */
    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemPromptResource;

    public ChatService(ChatClient.Builder chatClientBuilder, AgentTools agentTools) {
        // 创建思考捕获 Advisor
        this.thinkingCaptureAdvisor = new ThinkingCaptureAdvisor();
        
        // 使用 ChatClient.Builder 构建 ChatClient，注册默认工具和 Advisor
        // ChatClient 会自动处理工具调用循环
        this.chatClient = chatClientBuilder
                .defaultSystem(buildSystemPrompt())
                .defaultTools(agentTools)
                .defaultAdvisors(thinkingCaptureAdvisor)
                .build();
    }

    /**
     * 处理用户消息，支持流式响应
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

            // 构建对话历史
            String chatHistory = buildChatHistory(session);

            // 创建队列来接收中间步骤
            BlockingQueue<ThinkingCaptureAdvisor.IntermediateStep> stepQueue = new LinkedBlockingQueue<>();
            
            // 注册回调来接收中间步骤
            thinkingCaptureAdvisor.registerCallback(sessionId, step -> {
                stepQueue.offer(step);
                // 立即发送给前端
                sendStepToFrontend(step, sessionId, responseConsumer);
            });

            try {
                // 创建 advisor 上下文，传递会话ID
                Map<String, Object> advisorContext = new HashMap<>();
                advisorContext.put("sessionId", sessionId);

                // 使用 ChatClient 进行对话
                // ChatClient 会自动检测模型是否需要调用工具，并执行工具调用循环
                String content = chatClient.prompt()
                        .system(buildSystemPrompt() + "\n\n历史对话上下文：\n" + chatHistory)
                        .user(userMessage)
                        .advisors(advisor -> advisor.param("sessionId", sessionId))
                        .call()
                        .content();

                logger.info("模型响应: {}", content);

                // 将模型响应添加到会话
                session.addMessage(new ChatMessage(ChatMessage.MessageType.ASSISTANT, content));

                // 解析响应，分离思考过程和最终答案
                parseAndSendResponse(content, sessionId, responseConsumer);

            } finally {
                // 注销回调
                thinkingCaptureAdvisor.unregisterCallback(sessionId);
            }

        } catch (Exception e) {
            logger.error("处理消息时发生错误", e);
            responseConsumer.accept(ChatResponse.error("处理消息时发生错误: " + e.getMessage(), sessionId));
        }
    }

    /**
     * 发送步骤到前端
     * 
     * @param step 中间步骤
     * @param sessionId 会话ID
     * @param responseConsumer 响应消费者
     */
    private void sendStepToFrontend(ThinkingCaptureAdvisor.IntermediateStep step, String sessionId, 
                                     Consumer<ChatResponse> responseConsumer) {
        logger.info("发送步骤到前端 - 会话: {}, 类型: {}", sessionId, step.getType());
        switch (step.getType()) {
            case THINKING:
                // 思考内容
                logger.info("发送思考内容: {}", step.getContent());
                responseConsumer.accept(ChatResponse.thinking(step.getContent(), sessionId));
                break;
            case TOOL_CALL:
                // 工具调用
                logger.info("发送工具调用: {}", step.getToolName());
                responseConsumer.accept(ChatResponse.toolCall(step.getToolName(), sessionId));
                break;
        }
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
     * 构建对话历史
     * 
     * @param session 会话
     * @return 对话历史字符串
     */
    private String buildChatHistory(ChatSession session) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : session.getMessages()) {
            switch (msg.getType()) {
                case USER:
                    sb.append("用户：").append(msg.getContent()).append("\n");
                    break;
                case ASSISTANT:
                    sb.append("助手：").append(msg.getContent()).append("\n");
                    break;
                case TOOL_CALL:
                    sb.append("工具结果：").append(msg.getContent()).append("\n");
                    break;
                default:
                    break;
            }
        }
        return sb.toString();
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
