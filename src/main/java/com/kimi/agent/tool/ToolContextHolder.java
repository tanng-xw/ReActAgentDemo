package com.kimi.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工具上下文持有者
 * 通过 ThreadLocal 传递 ToolContext 给工具方法
 * 对 AI 模型隐藏内部参数（SessionId、UserId 等）
 * 
 * @author Kimi
 */
public class ToolContextHolder {

    private static final Logger logger = LoggerFactory.getLogger(ToolContextHolder.class);

    /** ThreadLocal 存储 ToolContext */
    private static final ThreadLocal<ToolContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置上下文
     * 
     * @param context 工具上下文
     */
    public static void setContext(ToolContext context) {
        CONTEXT_HOLDER.set(context);
        logger.debug("设置工具上下文: {}", context);
    }

    /**
     * 获取上下文
     * 
     * @return 工具上下文
     */
    public static ToolContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 获取上下文属性
     * 
     * @param key 键
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        ToolContext context = CONTEXT_HOLDER.get();
        if (context != null) {
            return context.get(key);
        }
        return null;
    }

    /**
     * 获取 SessionId
     * 
     * @return SessionId
     */
    public static String getSessionId() {
        ToolContext context = CONTEXT_HOLDER.get();
        if (context != null) {
            return context.getSessionId();
        }
        return null;
    }

    /**
     * 获取 UserId
     * 
     * @return UserId
     */
    public static String getUserId() {
        ToolContext context = CONTEXT_HOLDER.get();
        if (context != null) {
            return context.getUserId();
        }
        return null;
    }

    /**
     * 清除上下文
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
        logger.debug("清除工具上下文");
    }
}
