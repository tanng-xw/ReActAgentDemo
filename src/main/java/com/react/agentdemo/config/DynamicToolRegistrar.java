package com.react.agentdemo.config;

import com.react.agentdemo.tools.AgentTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * 动态工具注册器
 * 从配置加载工具描述并注册到 Spring AI
 */
@Configuration
public class DynamicToolRegistrar {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamicToolRegistrar.class);
    
    private final ToolDescriptionProperties toolDescriptionProperties;
    private final AgentTools agentTools;
    
    // 存储工具方法映射
    private final Map<String, Method> toolMethods = new HashMap<>();
    
    public DynamicToolRegistrar(ToolDescriptionProperties toolDescriptionProperties, 
                                AgentTools agentTools) {
        this.toolDescriptionProperties = toolDescriptionProperties;
        this.agentTools = agentTools;
    }
    
    @PostConstruct
    public void init() {
        // 扫描 AgentTools 中所有带有 @Tool 注解的方法
        for (Method method : AgentTools.class.getDeclaredMethods()) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation != null) {
                String methodName = method.getName();
                toolMethods.put(methodName, method);
                
                // 检查配置中是否有该工具的描述
                ToolDescriptionProperties.ToolDescription desc = 
                    toolDescriptionProperties.getToolDescription(methodName);
                if (desc != null) {
                    logger.debug("找到工具 '{}' 的配置描述: {}", methodName, desc.getDescription());
                }
            }
        }
        logger.info("扫描到 {} 个工具方法", toolMethods.size());
    }
    
    /**
     * 获取工具方法
     */
    public Method getToolMethod(String name) {
        return toolMethods.get(name);
    }
    
    /**
     * 获取所有工具方法名称
     */
    public Set<String> getToolNames() {
        return toolMethods.keySet();
    }
    
    /**
     * 获取工具描述
     */
    public String getToolDescription(String toolName) {
        ToolDescriptionProperties.ToolDescription desc = 
            toolDescriptionProperties.getToolDescription(toolName);
        if (desc != null && desc.getDescription() != null) {
            return desc.getDescription();
        }
        // 返回默认描述（从注解获取）
        Method method = toolMethods.get(toolName);
        if (method != null) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation != null) {
                return toolAnnotation.description();
            }
        }
        return "";
    }
    
    /**
     * 获取参数描述
     */
    public String getParameterDescription(String toolName, String paramName) {
        ToolDescriptionProperties.ToolDescription desc = 
            toolDescriptionProperties.getToolDescription(toolName);
        if (desc != null) {
            String paramDesc = desc.getParameterDescription(paramName);
            if (paramDesc != null) {
                return paramDesc;
            }
        }
        // 返回默认描述（从注解获取）
        Method method = toolMethods.get(toolName);
        if (method != null) {
            for (Parameter param : method.getParameters()) {
                ToolParam toolParam = param.getAnnotation(ToolParam.class);
                if (toolParam != null && param.getName().equals(paramName)) {
                    return toolParam.description();
                }
            }
        }
        return "";
    }
}
