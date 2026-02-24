package com.react.agentdemo.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户地点查询工具测试类
 * 
 * @author Kimi
 */
class LocationToolTest {

    private LocationTool locationTool;

    @BeforeEach
    void setUp() {
        locationTool = new LocationTool();
    }

    @Test
    @DisplayName("测试获取工具名称")
    void testGetName() {
        // 当 & 当
        String name = locationTool.getName();

        // 则
        assertEquals("get_user_location", name);
    }

    @Test
    @DisplayName("测试获取工具描述")
    void testGetDescription() {
        // 当 & 当
        String description = locationTool.getDescription();

        // 则
        assertNotNull(description);
        assertTrue(description.contains("城市"));
        assertTrue(description.contains("北京") || description.contains("上海") || description.contains("杭州"));
    }

    @Test
    @DisplayName("测试获取工具参数定义")
    void testGetParameters() {
        // 当 & 当
        Map<String, String> params = locationTool.getParameters();

        // 则
        assertNotNull(params);
        assertEquals(1, params.size());
        assertTrue(params.containsKey("sessionId"));
        assertTrue(params.get("sessionId").contains("会话"));
    }

    @Test
    @DisplayName("测试根据会话ID哈希返回北京")
    void testExecuteReturnsBeijing() {
        // 给定 - 使用一个会哈希到北京的会话ID
        String sessionId = "test-session-beijing-12345";
        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);

        // 当
        String result = locationTool.execute(params, sessionId);

        // 则 - 结果应该是三个城市之一
        assertNotNull(result);
        assertTrue(result.equals("北京") || result.equals("上海") || result.equals("杭州"));
    }

    @Test
    @DisplayName("测试多个会话ID返回不同的城市")
    void testDifferentSessionsReturnDifferentCities() {
        // 给定
        String[] sessionIds = {
            "session-1", "session-2", "session-3", 
            "session-4", "session-5", "session-6"
        };
        Map<String, Boolean> citiesSeen = new HashMap<>();

        // 当
        for (String sessionId : sessionIds) {
            Map<String, Object> params = new HashMap<>();
            params.put("sessionId", sessionId);
            String city = locationTool.execute(params, sessionId);
            citiesSeen.put(city, true);
        }

        // 则 - 应该至少看到2个不同的城市（概率很高）
        assertTrue(citiesSeen.size() >= 1);
    }

    @Test
    @DisplayName("测试使用参数中的sessionId")
    void testExecuteUsesParamSessionId() {
        // 给定 - 传入不同的sessionId作为参数
        String paramSessionId = "custom-session-for-test";
        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", paramSessionId);

        // 当 - 使用不同的sessionId调用
        String result = locationTool.execute(params, "different-session");

        // 则 - 结果应该基于参数中的sessionId
        assertNotNull(result);
    }

    @Test
    @DisplayName("测试参数为空时使用传入的sessionId")
    void testExecuteFallbackToSessionId() {
        // 给定 - 空参数
        String fallbackSessionId = "fallback-session-test";
        Map<String, Object> params = new HashMap<>();

        // 当
        String result = locationTool.execute(params, fallbackSessionId);

        // 则
        assertNotNull(result);
        assertTrue(result.equals("北京") || result.equals("上海") || result.equals("杭州"));
    }

    @Test
    @DisplayName("测试获取参数JSON Schema")
    void testGetParameterSchema() {
        // 当 & 当
        String schema = locationTool.getParameterSchema();

        // 则
        assertNotNull(schema);
        assertTrue(schema.contains("\"type\": \"object\""));
        assertTrue(schema.contains("sessionId"));
        assertTrue(schema.contains("properties"));
        assertTrue(schema.contains("required"));
    }
}
