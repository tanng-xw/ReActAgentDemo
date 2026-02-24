package com.react.agentdemo.tools;

import java.util.Map;

/**
 * 工具接口
 * 所有工具必须实现此接口
 * 
 * @author Kimi
 * @param <T> 工具返回结果类型
 */
public interface Tool<T> {

    /**
     * 获取工具名称
     * 
     * @return 工具名称
     */
    String getName();

    /**
     * 获取工具描述
     * 
     * @return 工具描述
     */
    String getDescription();

    /**
     * 获取工具参数定义
     * 
     * @return 参数定义，包含参数名和描述
     */
    Map<String, String> getParameters();

    /**
     * 执行工具
     * 
     * @param params 参数映射
     * @param sessionId 会话ID
     * @return 工具执行结果
     */
    T execute(Map<String, Object> params, String sessionId);

    /**
     * 获取参数JSON Schema（用于OpenAI Function Call）
     * 
     * @return JSON Schema 字符串
     */
    default String getParameterSchema() {
        StringBuilder schema = new StringBuilder();
        schema.append("{\n");
        schema.append("  \"type\": \"object\",\n");
        schema.append("  \"properties\": {\n");
        
        Map<String, String> params = getParameters();
        int i = 0;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            schema.append("    \"").append(entry.getKey()).append("\": {\n");
            schema.append("      \"type\": \"string\",\n");
            schema.append("      \"description\": \"").append(entry.getValue()).append("\"\n");
            schema.append("    }");
            if (i < params.size() - 1) {
                schema.append(",");
            }
            schema.append("\n");
            i++;
        }
        
        schema.append("  },\n");
        schema.append("  \"required\": [");
        
        i = 0;
        for (String key : params.keySet()) {
            schema.append("\"").append(key).append("\"");
            if (i < params.size() - 1) {
                schema.append(", ");
            }
            i++;
        }
        
        schema.append("]\n");
        schema.append("}");
        
        return schema.toString();
    }
}
