package com.react.agentdemo.service;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.react.agentdemo.model.ChatResponse;
import com.react.agentdemo.tool.StreamingObserverInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 聊天服务类
 * 使用 Spring AI Alibaba 的 ReactAgent 处理用户输入
 * 
 * @author Kimi
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    /** 最终答案标记前缀 */
    private static final String FINAL_ANSWER_PREFIX = "回答：";
    
    /** 最终答案标记前缀（兼容模式） */
    private static final String FINAL_ANSWER_PREFIX_ALT = "回答\n";

    /** ReactAgent 实例 */
    private final ReactAgent reactAgent;
    
    /** 流式观察拦截器 */
    private final StreamingObserverInterceptor observerInterceptor;
    
    /** 会话存储 */
    private final ConcurrentHashMap<String, List<Message>> sessionMemory = new ConcurrentHashMap<>();

    public ChatService(ReactAgent reactAgent, StreamingObserverInterceptor observerInterceptor) {
        this.reactAgent = reactAgent;
        this.observerInterceptor = observerInterceptor;
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
            logger.info("处理用户消息，会话ID: {}, 消息: {}", sessionId, userMessage);

            // 注册回调
            observerInterceptor.registerCallback(sessionId, responseConsumer);
            observerInterceptor.setCurrentSessionId(sessionId);

            // 构建 RunnableConfig
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

            // 使用流式调用
            Flux<NodeOutput> stream = reactAgent.stream(userMessage, config);

            // 收集完整响应
            StringBuilder fullResponse = new StringBuilder();
            
            stream.subscribe(
                    output -> {
                        // 处理每个节点输出
                        if (output.state() != null) {
                            Optional<Object> messagesOpt = output.state().value("messages");
                            if (messagesOpt.isPresent()) {
                                @SuppressWarnings("unchecked")
                                List<Message> messages = (List<Message>) messagesOpt.get();
                                if (!messages.isEmpty()) {
                                    Message lastMessage = messages.get(messages.size() - 1);
                                    if (lastMessage instanceof AssistantMessage assistantMsg) {
                                        String text = assistantMsg.getText();
                                        if (text != null && !text.isEmpty()) {
                                            fullResponse.setLength(0);
                                            fullResponse.append(text);
                                        }
                                    }
                                }
                            }
                        }
                    },
                    error -> {
                        // 错误处理
                        logger.error("流式处理时发生错误", error);
                        responseConsumer.accept(ChatResponse.error("处理错误: " + error.getMessage(), sessionId));
                        cleanup(sessionId);
                    },
                    () -> {
                        // 流式处理完成
                        String finalContent = fullResponse.toString();
                        logger.info("流式响应完成，总长度: {}", finalContent.length());
                        
                        // 解析并发送最终响应
                        parseAndSendFinalResponse(finalContent, sessionId, responseConsumer);
                        
                        cleanup(sessionId);
                    }
            );

        } catch (Exception e) {
            logger.error("处理消息时发生错误", e);
            responseConsumer.accept(ChatResponse.error("处理消息时发生错误: " + e.getMessage(), sessionId));
            cleanup(sessionId);
        }
    }

    /**
     * 解析最终响应，分离思考过程和最终答案
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

        // 检查是否包含最终答案标记
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
            // 如果没有标记，将整个内容作为最终答案
            String trimmedContent = content.trim();
            if (!trimmedContent.isEmpty()) {
                responseConsumer.accept(ChatResponse.finalAnswer(trimmedContent, sessionId));
            } else {
                responseConsumer.accept(ChatResponse.finalAnswer("模型未返回有效内容", sessionId));
            }
        }
    }

    /**
     * 清理资源
     * 
     * @param sessionId 会话ID
     */
    private void cleanup(String sessionId) {
        observerInterceptor.unregisterCallback(sessionId);
        observerInterceptor.clearSessionId();
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
     * 
     * @param sessionId 会话ID
     */
    public void clearSession(String sessionId) {
        logger.info("清除会话历史: {}", sessionId);
        sessionMemory.remove(sessionId);
    }
}
