package com.react.agentdemo.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.react.agentdemo.tool.StreamingObserverInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.List;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring AI Alibaba ReactAgent 配置类
 * 配置 ReactAgent 以使用原生 ReAct 能力
 * 
 * @author Kimi
 */
@Configuration
public class AlibabaAgentConfig {

    private static final Logger logger = LoggerFactory.getLogger(AlibabaAgentConfig.class);

    /** 系统提示词模板资源 */
    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemPromptResource;

    /**
     * 创建流式观察拦截器
     * 用于捕获思考和工具调用
     * 
     * @return StreamingObserverInterceptor 实例
     */
    @Bean
    public StreamingObserverInterceptor streamingObserverInterceptor() {
        return new StreamingObserverInterceptor();
    }

    /**
     * 创建 ReactAgent
     * 使用 Spring AI Alibaba 原生的 ReActAgent 能力
     * 
     * @param chatModel Spring AI 的 ChatModel
     * @param toolCallbacks 工具回调列表
     * @param observerInterceptor 流式观察拦截器
     * @return ReactAgent 实例
     */
    @Bean
    public ReactAgent reactAgent(ChatModel chatModel, List<ToolCallback> toolCallbacks,
                                  StreamingObserverInterceptor observerInterceptor) {
        logger.info("正在创建 ReactAgent，工具数量: {}", toolCallbacks.size());
        
        String systemPrompt = loadSystemPrompt();
        
        ReactAgent agent = ReactAgent.builder()
                .name("music_assistant")
                .model(chatModel)
                .tools(toolCallbacks.toArray(new ToolCallback[0]))
                .systemPrompt(systemPrompt)
                .saver(new MemorySaver())
                .interceptors(observerInterceptor)
                .build();
        
        logger.info("ReactAgent 创建完成");
        return agent;
    }
    
    /**
     * 加载系统提示词
     * 
     * @return 系统提示词内容
     */
    private String loadSystemPrompt() {
        try {
            if (systemPromptResource == null) {
                logger.warn("系统提示词资源未找到，使用默认提示词");
                return getDefaultSystemPrompt();
            }
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(systemPromptResource.getInputStream(), StandardCharsets.UTF_8))) {
                String prompt = reader.lines().collect(Collectors.joining("\n"));
                logger.info("成功加载系统提示词，长度: {}", prompt.length());
                return prompt;
            }
        } catch (IOException e) {
            logger.error("加载系统提示词失败", e);
            return getDefaultSystemPrompt();
        }
    }
    
    /**
     * 获取默认系统提示词
     * 
     * @return 默认提示词
     */
    private String getDefaultSystemPrompt() {
        return """
            你是一个智能音乐助手，能够帮助用户查询信息、搜索歌曲等。
            
            重要规则：
            1. 你可以使用工具来帮助回答用户问题。
            2. 在调用工具前，先说明你的思考过程。
            3. 你可以一次性并行调用多个工具。
            4. **最终答案必须以"回答："开头**（冒号后紧跟内容，不要换行）
            5. 如果不需要工具，直接输出最终答案即可。
            """;
    }
}
