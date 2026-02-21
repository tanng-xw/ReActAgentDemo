package com.kimi.agent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 天气查询工具测试类
 * 
 * @author Kimi
 */
class WeatherToolTest {

    private WeatherTool weatherTool;

    @BeforeEach
    void setUp() {
        weatherTool = new WeatherTool();
    }

    @Test
    @DisplayName("测试获取工具名称")
    void testGetName() {
        // 当 & 当
        String name = weatherTool.getName();

        // 则
        assertEquals("get_weather", name);
    }

    @Test
    @DisplayName("测试获取工具描述")
    void testGetDescription() {
        // 当 & 当
        String description = weatherTool.getDescription();

        // 则
        assertNotNull(description);
        assertTrue(description.contains("天气"));
        assertTrue(description.contains("北京") || description.contains("上海") || description.contains("杭州"));
    }

    @Test
    @DisplayName("测试获取工具参数定义")
    void testGetParameters() {
        // 当 & 当
        Map<String, String> params = weatherTool.getParameters();

        // 则
        assertNotNull(params);
        assertEquals(1, params.size());
        assertTrue(params.containsKey("city"));
    }

    @Test
    @DisplayName("测试查询北京天气")
    void testExecuteBeijing() {
        // 给定
        Map<String, Object> params = new HashMap<>();
        params.put("city", "北京");

        // 当
        WeatherTool.WeatherInfo result = weatherTool.execute(params, "test-session");

        // 则
        assertNotNull(result);
        assertEquals("北京", result.getCity());
        assertNotNull(result.getWeather());
        assertNotNull(result.getTemperature());
        assertNotNull(result.getHumidity());
        assertNotNull(result.getWind());
    }

    @Test
    @DisplayName("测试查询上海天气")
    void testExecuteShanghai() {
        // 给定
        Map<String, Object> params = new HashMap<>();
        params.put("city", "上海");

        // 当
        WeatherTool.WeatherInfo result = weatherTool.execute(params, "test-session");

        // 则
        assertNotNull(result);
        assertEquals("上海", result.getCity());
    }

    @Test
    @DisplayName("测试查询杭州天气")
    void testExecuteHangzhou() {
        // 给定
        Map<String, Object> params = new HashMap<>();
        params.put("city", "杭州");

        // 当
        WeatherTool.WeatherInfo result = weatherTool.execute(params, "test-session");

        // 则
        assertNotNull(result);
        assertEquals("杭州", result.getCity());
    }

    @Test
    @DisplayName("测试查询英文城市名")
    void testExecuteEnglishCityName() {
        // 给定 - 使用英文城市名
        Map<String, Object> params = new HashMap<>();
        params.put("city", "beijing");

        // 当
        WeatherTool.WeatherInfo result = weatherTool.execute(params, "test-session");

        // 则
        assertNotNull(result);
        assertEquals("北京", result.getCity());
    }

    @Test
    @DisplayName("测试查询未知城市")
    void testExecuteUnknownCity() {
        // 给定 - 未知城市
        Map<String, Object> params = new HashMap<>();
        params.put("city", "广州");

        // 当
        WeatherTool.WeatherInfo result = weatherTool.execute(params, "test-session");

        // 则
        assertNotNull(result);
        assertEquals("广州", result.getCity());
        assertEquals("暂无数据", result.getWeather());
    }

    @Test
    @DisplayName("测试空城市参数")
    void testExecuteEmptyCity() {
        // 给定 - 空城市参数
        Map<String, Object> params = new HashMap<>();
        params.put("city", "");

        // 当
        WeatherTool.WeatherInfo result = weatherTool.execute(params, "test-session");

        // 则
        assertNotNull(result);
        assertEquals("未知", result.getCity());
    }

    @Test
    @DisplayName("测试天气信息字符串表示")
    void testWeatherInfoToString() {
        // 给定
        Map<String, Object> params = new HashMap<>();
        params.put("city", "北京");

        // 当
        WeatherTool.WeatherInfo result = weatherTool.execute(params, "test-session");
        String str = result.toString();

        // 则
        assertNotNull(str);
        assertTrue(str.contains("北京"));
        assertTrue(str.contains("温度"));
    }

    @Test
    @DisplayName("测试获取参数JSON Schema")
    void testGetParameterSchema() {
        // 当 & 当
        String schema = weatherTool.getParameterSchema();

        // 则
        assertNotNull(schema);
        assertTrue(schema.contains("city"));
    }
}
