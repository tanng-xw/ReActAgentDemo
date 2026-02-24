package com.react.agentdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具描述配置属性
 */
@Configuration
@ConfigurationProperties(prefix = "agent.tools")
public class ToolDescriptionProperties {
    
    private Map<String, ToolDescription> tools = new HashMap<>();
    
    public Map<String, ToolDescription> getTools() {
        return tools;
    }
    
    public void setTools(Map<String, ToolDescription> tools) {
        this.tools = tools;
    }
    
    /**
     * 获取指定工具的描述
     */
    public ToolDescription getToolDescription(String toolName) {
        return tools.get(toolName);
    }
    
    /**
     * 工具描述
     */
    public static class ToolDescription {
        private String description;
        private Map<String, String> parameters = new HashMap<>();
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public Map<String, String> getParameters() {
            return parameters;
        }
        
        public void setParameters(Map<String, String> parameters) {
            this.parameters = parameters;
        }
        
        /**
         * 获取指定参数的描述
         */
        public String getParameterDescription(String paramName) {
            return parameters.get(paramName);
        }
    }
}
