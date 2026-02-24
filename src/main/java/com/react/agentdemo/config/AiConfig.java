package com.react.agentdemo.config;

import com.react.agentdemo.tool.ObservingToolCallingManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI 配置类
 * 注册自定义的 ToolCallingManager 并关联到 ChatClient
 * 
 * @author Kimi
 */
@Configuration
public class AiConfig {

    /**
     * 注册自定义的可观察 ToolCallingManager
     * 覆盖默认的 ToolCallingManager Bean
     * 
     * @param toolCallbackResolver 工具回调解析器
     * @return 自定义的 ToolCallingManager
     */
    @Bean
    @Primary
    public ToolCallingManager toolCallingManager(ToolCallbackResolver toolCallbackResolver) {
        return new ObservingToolCallingManager(toolCallbackResolver);
    }
}
