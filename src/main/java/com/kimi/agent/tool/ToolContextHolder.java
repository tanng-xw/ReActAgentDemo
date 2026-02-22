package com.kimi.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具上下文持有者
 * 通过 ThreadLocal 传递工具执行所需的上下文参数（如 SessionId）
 * 避免将这些内部参数暴露给 AI 模型
 * 
 * @author Kimi
 */
public class ToolContextHolder {

    private static final Logger logger = LoggerFactory.getLogger(ToolContextHolder.class);

    /** ThreadLocal 存储上下文 */
    private static final ThreadLocal<Map<String, Object>> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置上下文
     * 
     * @param context 上下文 Map
     */
    public static void setContext(Map<String, Object> context) {
        CONTEXT_HOLDER.set(context);
        logger.debug("设置工具上下文: {}", context);
    }

    /**
     * 设置单个上下文值
     * 
     * @param key 键
     * @param value 值
     */
    public static void set(String key, Object value) {
        Map<String, Object> context = CONTEXT_HOLDER.get();
        if (context == null) {
            context = new HashMap<>();
            CONTEXT_HOLDER.set(context);
        }
        context.put(key, value);
    }

    /**
     * 获取上下文值
     * 
     * @param key 键
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        Map<String, Object> context = CONTEXT_HOLDER.get();
        if (context != null) {
            return (T) context.get(key);
        }
        return null;
    }

    /**
     * 获取 SessionId
     * 
     * @return SessionId
     */
    public static String getSessionId() {
        return get("sessionId");
    }

    /**
     * 清除上下文
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
        logger.debug("清除工具上下文");
    }

    /**
     * 创建默认上下文（仅包含 SessionId）
     * 
     * @param sessionId 会话ID
     * @return 上下文 Map
     */
    public static Map<String, Object> createDefaultContext(String sessionId) {
        Map<String, Object> context = new HashMap<>();
        context.put("sessionId", sessionId);
        return context;
    }
}
