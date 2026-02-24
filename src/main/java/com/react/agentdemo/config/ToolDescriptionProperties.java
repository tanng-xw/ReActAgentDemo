package com.react.agentdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        private Map<String, ParameterDefinition> parameters = new HashMap<>();
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public Map<String, ParameterDefinition> getParameters() {
            return parameters;
        }
        
        public void setParameters(Map<String, ParameterDefinition> parameters) {
            this.parameters = parameters;
        }
        
        /**
         * 获取指定参数的描述（兼容旧版字符串配置）
         */
        public String getParameterDescription(String paramName) {
            ParameterDefinition def = parameters.get(paramName);
            return def != null ? def.getDescription() : null;
        }
    }
    
    /**
     * 参数定义
     */
    public static class ParameterDefinition {
        private String description;
        private String type;
        private List<String> enumValues;
        private Boolean required;
        private ItemDefinition items;
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public List<String> getEnum() {
            return enumValues;
        }
        
        public void setEnum(List<String> enumValues) {
            this.enumValues = enumValues;
        }
        
        public Boolean getRequired() {
            return required;
        }
        
        public void setRequired(Boolean required) {
            this.required = required;
        }
        
        public ItemDefinition getItems() {
            return items;
        }
        
        public void setItems(ItemDefinition items) {
            this.items = items;
        }
    }
    
    /**
     * 数组项定义
     */
    public static class ItemDefinition {
        private String type;
        private List<String> enumValues;
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public List<String> getEnum() {
            return enumValues;
        }
        
        public void setEnum(List<String> enumValues) {
            this.enumValues = enumValues;
        }
    }
}
