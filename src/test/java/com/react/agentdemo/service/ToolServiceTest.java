package com.react.agentdemo.service;

import com.react.agentdemo.tools.LocationTool;
import com.react.agentdemo.tools.SongSearchTool;
import com.react.agentdemo.tools.Tool;
import com.react.agentdemo.tools.WeatherTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具服务测试类
 * 
 * @author Kimi
 */
class ToolServiceTest {

    private ToolService toolService;
    private LocationTool locationTool;
    private WeatherTool weatherTool;
    private SongSearchTool songSearchTool;

    @BeforeEach
    void setUp() {
        locationTool = new LocationTool();
        weatherTool = new WeatherTool();
        songSearchTool = new SongSearchTool();
        toolService = new ToolService(locationTool, weatherTool, songSearchTool);
    }

    @Test
    @DisplayName("测试获取所有工具")
    void testGetAllTools() {
        // 当
        List<Tool<?>> tools = toolService.getAllTools();

        // 则
        assertNotNull(tools);
        assertEquals(3, tools.size());
    }

    @Test
    @DisplayName("测试根据名称获取工具")
    void testGetTool() {
        // 当
        Tool<?> location = toolService.getTool("get_user_location");
        Tool<?> weather = toolService.getTool("get_weather");
        Tool<?> song = toolService.getTool("search_songs");

        // 则
        assertNotNull(location);
        assertNotNull(weather);
        assertNotNull(song);
        
        assertTrue(location instanceof LocationTool);
        assertTrue(weather instanceof WeatherTool);
        assertTrue(song instanceof SongSearchTool);
    }

    @Test
    @DisplayName("测试获取不存在的工具")
    void testGetNonExistentTool() {
        // 当
        Tool<?> tool = toolService.getTool("不存在的工具");

        // 则
        assertNull(tool);
    }

    @Test
    @DisplayName("测试执行地点查询工具")
    void testExecuteLocationTool() {
        // 给定
        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", "test-session");

        // 当
        Object result = toolService.executeTool("get_user_location", params, "test-session");

        // 则
        assertNotNull(result);
        assertTrue(result instanceof String);
        String city = (String) result;
        assertTrue(city.equals("北京") || city.equals("上海") || city.equals("杭州"));
    }

    @Test
    @DisplayName("测试执行天气查询工具")
    void testExecuteWeatherTool() {
        // 给定
        Map<String, Object> params = new HashMap<>();
        params.put("city", "北京");

        // 当
        Object result = toolService.executeTool("get_weather", params, "test-session");

        // 则
        assertNotNull(result);
        assertTrue(result instanceof WeatherTool.WeatherInfo);
        WeatherTool.WeatherInfo info = (WeatherTool.WeatherInfo) result;
        assertEquals("北京", info.getCity());
    }

    @Test
    @DisplayName("测试执行歌曲搜索工具")
    void testExecuteSongSearchTool() {
        // 给定
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "周杰伦");

        // 当
        Object result = toolService.executeTool("search_songs", params, "test-session");

        // 则
        assertNotNull(result);
        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<SongSearchTool.Song> songs = (List<SongSearchTool.Song>) result;
        assertFalse(songs.isEmpty());
    }

    @Test
    @DisplayName("测试执行不存在的工具")
    void testExecuteNonExistentTool() {
        // 给定
        Map<String, Object> params = new HashMap<>();

        // 当 & 则
        assertThrows(IllegalArgumentException.class, () -> {
            toolService.executeTool("不存在的工具", params, "test-session");
        });
    }

    @Test
    @DisplayName("测试获取工具描述")
    void testGetToolsDescription() {
        // 当
        String description = toolService.getToolsDescription();

        // 则
        assertNotNull(description);
        assertTrue(description.contains("用户地点查询"));
        assertTrue(description.contains("天气查询"));
        assertTrue(description.contains("歌曲搜索"));
        assertTrue(description.contains("工具名称"));
        assertTrue(description.contains("描述"));
        assertTrue(description.contains("参数"));
    }

}
