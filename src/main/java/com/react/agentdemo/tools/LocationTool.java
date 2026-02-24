package com.react.agentdemo.tools;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户地点查询工具
 * 根据会话ID哈希返回用户所在城市
 * 
 * @author Kimi
 */
@Component
public class LocationTool implements Tool<String> {

    /** 预置的城市列表 */
    private static final String[] CITIES = {"北京", "上海", "杭州"};

    /**
     * 获取工具名称
     * 
     * @return 工具名称
     */
    @Override
    public String getName() {
        return "get_user_location";
    }

    /**
     * 获取工具描述
     * 
     * @return 工具描述
     */
    @Override
    public String getDescription() {
        return "用户地点查询：根据会话ID返回用户当前所在的城市（北京、上海或杭州）";
    }

    /**
     * 获取工具参数定义
     * 
     * @return 参数定义
     */
    @Override
    public Map<String, String> getParameters() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("sessionId", "会话ID，用于确定用户位置");
        return params;
    }

    /**
     * 执行工具，查询用户地点
     * 
     * @param params 参数映射，需要包含 sessionId
     * @param sessionId 会话ID
     * @return 用户所在城市
     */
    @Override
    public String execute(Map<String, Object> params, String sessionId) {
        // 优先使用传入的参数，如果没有则使用会话ID
        String targetSessionId = sessionId;
        if (params.containsKey("sessionId") && params.get("sessionId") != null) {
            targetSessionId = params.get("sessionId").toString();
        }
        
        // 基于会话ID哈希选择城市
        int hash = Math.abs(targetSessionId.hashCode());
        int index = hash % CITIES.length;
        
        return CITIES[index];
    }
}
