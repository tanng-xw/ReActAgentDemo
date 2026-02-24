package com.react.agentdemo.tools;

import com.react.agentdemo.tool.ToolContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 智能助手工具类
 * 使用 @Tool 注解定义工具方法
 * SessionId 等内部参数通过 ToolContextHolder (ThreadLocal) 传递，不暴露给 AI 模型
 * 
 * @author Kimi
 */
@Component
public class AgentTools {

    private static final Logger logger = LoggerFactory.getLogger(AgentTools.class);

    private final LocationTool locationTool;
    private final WeatherTool weatherTool;
    private final SongSearchTool songSearchTool;

    public AgentTools(LocationTool locationTool, WeatherTool weatherTool, SongSearchTool songSearchTool) {
        this.locationTool = locationTool;
        this.weatherTool = weatherTool;
        this.songSearchTool = songSearchTool;
    }

    /**
     * 获取用户当前所在城市
     * SessionId 通过 ToolContextHolder 获取，不暴露给模型
     * 
     * @return 用户所在城市（北京、上海或杭州）
     */
    @Tool(description = "查询用户当前所在的城市位置。支持北京、上海、杭州。")
    public String getUserLocation() {
        // 从 ThreadLocal 获取真实的 SessionId
        String sessionId = ToolContextHolder.getSessionId();
        if (sessionId == null) {
            sessionId = "default";
            logger.warn("未找到 SessionId，使用默认值");
        }
        
        logger.info("获取用户位置，SessionId: {}", sessionId);
        
        Map<String, Object> params = Map.of("sessionId", sessionId);
        Object result = locationTool.execute(params, sessionId);
        return result != null ? result.toString() : "未知";
    }

    /**
     * 查询指定城市的天气
     * 
     * @param city 城市名称
     * @return 天气信息
     */
    @Tool(description = "查询指定城市的天气信息。支持北京、上海、杭州的天气查询，返回天气状况、温度、湿度和风力。")
    public String getWeather(
            @ToolParam(description = "城市名称，如：北京、上海、杭州") String city) {
        Map<String, Object> params = Map.of("city", city != null ? city : "北京");
        Object result = weatherTool.execute(params, "default");
        return result != null ? result.toString() : "查询失败";
    }

    /**
     * 搜索歌曲
     * 
     * @param keyword 搜索关键词
     * @return 歌曲列表
     */
    @Tool(description = "根据关键词搜索歌曲。可以按歌名、歌手名或专辑名搜索，返回歌曲ID、歌名、歌手名、专辑名。")
    public String searchSongs(
            @ToolParam(description = "搜索关键词，可以是歌名、歌手名或专辑名") String keyword) {
        Map<String, Object> params = Map.of("keyword", keyword != null ? keyword : "歌曲");
        Object result = songSearchTool.execute(params, "default");
        return result != null ? result.toString() : "搜索失败";
    }
}
