package com.react.agentdemo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * 工具描述配置加载器
 * 从 JSON 配置文件加载工具描述
 */
@Configuration
public class ToolDescriptionConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(ToolDescriptionConfig.class);
    
    private final ToolDescriptionProperties toolDescriptionProperties;
    private final ObjectMapper objectMapper;
    
    public ToolDescriptionConfig(ToolDescriptionProperties toolDescriptionProperties, ObjectMapper objectMapper) {
        this.toolDescriptionProperties = toolDescriptionProperties;
        this.objectMapper = objectMapper;
    }
    
    @PostConstruct
    public void loadToolDescriptions() {
        try {
            ClassPathResource resource = new ClassPathResource("config/tool-descriptions.json");
            if (!resource.exists()) {
                logger.warn("工具描述配置文件不存在: config/tool-descriptions.json");
                return;
            }
            
            try (InputStream is = resource.getInputStream()) {
                ToolDescriptionWrapper wrapper = objectMapper.readValue(is, ToolDescriptionWrapper.class);
                if (wrapper != null && wrapper.getTools() != null) {
                    toolDescriptionProperties.setTools(wrapper.getTools());
                    logger.info("成功加载 {} 个工具描述配置", wrapper.getTools().size());
                }
            }
        } catch (IOException e) {
            logger.error("加载工具描述配置失败", e);
        }
    }
    
    /**
     * JSON 包装类
     */
    public static class ToolDescriptionWrapper {
        private Map<String, ToolDescriptionProperties.ToolDescription> tools;
        
        public Map<String, ToolDescriptionProperties.ToolDescription> getTools() {
            return tools;
        }
        
        public void setTools(Map<String, ToolDescriptionProperties.ToolDescription> tools) {
            this.tools = tools;
        }
    }
}
