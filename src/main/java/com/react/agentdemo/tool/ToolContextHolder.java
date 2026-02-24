package com.react.agentdemo.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工具上下文持有者
 * 优先从 ThreadLocal 获取，如果不存在则从 RequestContextHolder 获取（支持异步）
 * 
 * @author Kimi
 */
public class ToolContextHolder {

    private static final Logger logger = LoggerFactory.getLogger(ToolContextHolder.class);

    /** ThreadLocal 存储（同步场景） */
    private static final ThreadLocal<ToolContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置上下文（同步场景）
     * 
     * @param context 工具上下文
     */
    public static void setContext(ToolContext context) {
        CONTEXT_HOLDER.set(context);
        logger.debug("设置工具上下文到 ThreadLocal: {}", context);
    }

    /**
     * 获取上下文
     * 优先从 ThreadLocal 获取，如果失败则从 RequestContextHolder 获取（异步场景）
     * 
     * @return 工具上下文
     */
    public static ToolContext getContext() {
        // 1. 先尝试 ThreadLocal（同步场景）
        ToolContext context = CONTEXT_HOLDER.get();
        if (context != null) {
            return context;
        }

        // 2. 尝试从 RequestContextHolder 获取（异步场景）
        // 注意：这里需要通过某种方式知道当前请求ID
        // 可以将 requestId 也放到 ThreadLocal 中，或者通过方法参数传递
        // 简化起见，这里假设异步场景下调用方会主动设置 ThreadLocal
        
        logger.debug("ThreadLocal 中未找到工具上下文");
        return null;
    }

    /**
     * 获取上下文（异步场景，需要传入 requestId）
     * 
     * @param requestId 请求ID
     * @return 工具上下文
     */
    public static ToolContext getContext(String requestId) {
        if (requestId != null) {
            RequestContextHolder.RequestContext requestContext = 
                RequestContextHolder.getContext(requestId);
            if (requestContext != null) {
                return requestContext.getToolContext();
            }
        }
        // 回退到 ThreadLocal
        return CONTEXT_HOLDER.get();
    }

    /**
     * 获取属性
     * 
     * @param key 键
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        ToolContext context = getContext();
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
        ToolContext context = getContext();
        if (context != null) {
            return context.getSessionId();
        }
        return null;
    }

    /**
     * 获取 SessionId（异步场景）
     * 
     * @param requestId 请求ID
     * @return SessionId
     */
    public static String getSessionId(String requestId) {
        ToolContext context = getContext(requestId);
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
        ToolContext context = getContext();
        if (context != null) {
            return context.getUserId();
        }
        return null;
    }

    /**
     * 清除 ThreadLocal 上下文
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
        logger.debug("清除 ThreadLocal 工具上下文");
    }
}
