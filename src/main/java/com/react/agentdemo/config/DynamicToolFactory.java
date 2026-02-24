package com.react.agentdemo.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.react.agentdemo.tools.AgentToolFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 动态工具工厂
 * 从配置构建 FunctionToolCallback
 */
@Configuration
public class DynamicToolFactory {

    private static final Logger logger = LoggerFactory.getLogger(DynamicToolFactory.class);

    private final ToolDescriptionProperties toolProperties;
    private final AgentToolFunctions toolFunctions;
    private final ObjectMapper objectMapper;

    public DynamicToolFactory(ToolDescriptionProperties toolProperties, 
                              AgentToolFunctions toolFunctions,
                              ObjectMapper objectMapper) {
        this.toolProperties = toolProperties;
        this.toolFunctions = toolFunctions;
        this.objectMapper = objectMapper;
    }

    @Bean
    public List<ToolCallback> dynamicToolCallbacks() {
        List<ToolCallback> callbacks = new ArrayList<>();
        
        // 从配置加载所有工具
        Map<String, ToolDescriptionProperties.ToolDescription> tools = toolProperties.getTools();
        
        for (Map.Entry<String, ToolDescriptionProperties.ToolDescription> entry : tools.entrySet()) {
            String toolName = entry.getKey();
            ToolDescriptionProperties.ToolDescription desc = entry.getValue();
            
            try {
                ToolCallback callback = createToolCallback(toolName, desc);
                if (callback != null) {
                    callbacks.add(callback);
                    logger.debug("成功创建工具回调: {}", toolName);
                }
            } catch (Exception e) {
                logger.error("创建工具回调失败: {}", toolName, e);
            }
        }
        
        logger.info("成功创建 {} 个动态工具回调", callbacks.size());
        return callbacks;
    }

    @SuppressWarnings("unchecked")
    private ToolCallback createToolCallback(String toolName, 
                                             ToolDescriptionProperties.ToolDescription desc) {
        // 根据工具名获取对应的 Function
        Function<?, String> function = getFunctionByName(toolName);
        if (function == null) {
            logger.warn("未找到工具对应的 Function: {}", toolName);
            return null;
        }

        // 构建 inputSchema
        String inputSchema = buildInputSchema(desc.getParameters());

        // 获取输入类型
        Class<?> inputType = getInputType(toolName);

        // 使用 FunctionToolCallback 构建工具
        return FunctionToolCallback.builder(toolName, function)
                .description(desc.getDescription())
                .inputType(inputType)
                .inputSchema(inputSchema)
                .build();
    }

    /**
     * 根据工具名获取对应的 Function
     */
    @SuppressWarnings("unchecked")
    private Function<?, String> getFunctionByName(String toolName) {
        return switch (toolName) {
            case "getUserLocation" -> toolFunctions.getUserLocation();
            case "getWeather" -> toolFunctions.getWeather();
            case "webSearch" -> toolFunctions.webSearch();
            case "songSearch" -> toolFunctions.songSearch();
            case "songListSearch" -> toolFunctions.songListSearch();
            case "songSlotMatch" -> toolFunctions.songSlotMatch();
            case "personalizedRecommend" -> toolFunctions.personalizedRecommend();
            case "topMusicChart" -> toolFunctions.topMusicChart();
            case "playbackControl" -> toolFunctions.playbackControl();
            case "userAsset" -> toolFunctions.userAsset();
            default -> null;
        };
    }

    /**
     * 获取工具输入类型
     */
    private Class<?> getInputType(String toolName) {
        return switch (toolName) {
            case "getUserLocation", "personalizedRecommend" -> Void.class;
            case "getWeather" -> AgentToolFunctions.GetWeatherRequest.class;
            case "webSearch" -> AgentToolFunctions.WebSearchRequest.class;
            case "songSearch" -> AgentToolFunctions.SongSearchRequest.class;
            case "songListSearch" -> AgentToolFunctions.SongListSearchRequest.class;
            case "songSlotMatch" -> AgentToolFunctions.SongSlotMatchRequest.class;
            case "topMusicChart" -> AgentToolFunctions.TopMusicChartRequest.class;
            case "playbackControl" -> AgentToolFunctions.PlaybackControlRequest.class;
            case "userAsset" -> AgentToolFunctions.UserAssetRequest.class;
            default -> Object.class;
        };
    }

    /**
     * 构建 JSON Schema
     */
    private String buildInputSchema(Map<String, ToolDescriptionProperties.ParameterDefinition> parameters) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        
        for (Map.Entry<String, ToolDescriptionProperties.ParameterDefinition> entry : parameters.entrySet()) {
            String paramName = entry.getKey();
            ToolDescriptionProperties.ParameterDefinition paramDef = entry.getValue();
            
            Map<String, Object> prop = new HashMap<>();
            prop.put("description", paramDef.getDescription());
            
            // 处理类型
            String type = paramDef.getType();
            if (type == null) type = "string";
            
            switch (type) {
                case "array":
                    prop.put("type", "array");
                    if (paramDef.getItems() != null) {
                        Map<String, Object> items = new HashMap<>();
                        items.put("type", paramDef.getItems().getType());
                        // 处理 items 中的枚举值
                        if (paramDef.getItems().getEnum() != null && !paramDef.getItems().getEnum().isEmpty()) {
                            items.put("enum", paramDef.getItems().getEnum());
                        }
                        prop.put("items", items);
                    } else {
                        prop.put("items", Map.of("type", "string"));
                    }
                    break;
                case "integer":
                    prop.put("type", "integer");
                    break;
                case "boolean":
                    prop.put("type", "boolean");
                    break;
                default:
                    prop.put("type", "string");
                    break;
            }
            
            // 处理枚举值
            if (paramDef.getEnum() != null && !paramDef.getEnum().isEmpty()) {
                prop.put("enum", paramDef.getEnum());
            }
            
            properties.put(paramName, prop);
            
            // 处理 required
            if (Boolean.TRUE.equals(paramDef.getRequired())) {
                required.add(paramName);
            }
        }
        
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            logger.error("构建 JSON Schema 失败", e);
            return "{}";
        }
    }
}
