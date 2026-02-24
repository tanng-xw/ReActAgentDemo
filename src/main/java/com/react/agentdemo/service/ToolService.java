package com.react.agentdemo.service;

import com.react.agentdemo.tools.LocationTool;
import com.react.agentdemo.tools.SongSearchTool;
import com.react.agentdemo.tools.Tool;
import com.react.agentdemo.tools.WeatherTool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具服务类
 * 管理所有可用的工具，提供工具发现和执行功能
 * 
 * @author Kimi
 */
@Service
public class ToolService {

    /** 工具注册表 */
    private final Map<String, Tool<?>> toolRegistry = new LinkedHashMap<>();

    /** 地点查询工具 */
    private final LocationTool locationTool;
    
    /** 天气查询工具 */
    private final WeatherTool weatherTool;
    
    /** 歌曲搜索工具 */
    private final SongSearchTool songSearchTool;

    /**
     * 构造函数，初始化工具注册
     */
    public ToolService(LocationTool locationTool, WeatherTool weatherTool, SongSearchTool songSearchTool) {
        this.locationTool = locationTool;
        this.weatherTool = weatherTool;
        this.songSearchTool = songSearchTool;
        
        // 注册所有工具
        registerTool(locationTool);
        registerTool(weatherTool);
        registerTool(songSearchTool);
    }

    /**
     * 注册工具
     * 
     * @param tool 工具实例
     */
    private void registerTool(Tool<?> tool) {
        toolRegistry.put(tool.getName(), tool);
    }

    /**
     * 获取所有可用工具
     * 
     * @return 工具列表
     */
    public List<Tool<?>> getAllTools() {
        return new ArrayList<>(toolRegistry.values());
    }

    /**
     * 根据名称获取工具
     * 
     * @param name 工具名称
     * @return 工具实例，不存在返回 null
     */
    public Tool<?> getTool(String name) {
        return toolRegistry.get(name);
    }

    /**
     * 执行工具
     * 
     * @param toolName 工具名称
     * @param params 工具参数
     * @param sessionId 会话ID
     * @return 工具执行结果
     */
    public Object executeTool(String toolName, Map<String, Object> params, String sessionId) {
        Tool<?> tool = toolRegistry.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("未找到工具: " + toolName);
        }
        return tool.execute(params, sessionId);
    }

    /**
     * 获取工具描述信息（用于构建系统提示词）
     * 
     * @return 工具描述字符串
     */
    public String getToolsDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("您可以使用以下工具来帮助回答用户的问题：\n\n");
        
        for (Tool<?> tool : toolRegistry.values()) {
            sb.append("工具名称：").append(tool.getName()).append("\n");
            sb.append("描述：").append(tool.getDescription()).append("\n");
            sb.append("参数：\n");
            
            Map<String, String> params = tool.getParameters();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append("  - ").append(entry.getKey()).append("：").append(entry.getValue()).append("\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
}
