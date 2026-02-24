package com.react.agentdemo.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.react.agentdemo.model.ChatResponse;

/**
 * 请求上下文持有者
 * 使用 ConcurrentHashMap 按请求 ID 存储上下文，支持异步线程切换
 * 
 * @author Kimi
 */
public class RequestContextHolder {

    private static final Logger logger = LoggerFactory.getLogger(RequestContextHolder.class);

    /** 按请求 ID 存储上下文 */
    private static final Map<String, RequestContext> CONTEXT_MAP = new ConcurrentHashMap<>();

    /**
     * 请求上下文
     */
    public static class RequestContext {
        private final String requestId;
        private ToolContext toolContext;
        private Consumer<ChatResponse> callback;
        private long createTime = System.currentTimeMillis();

        public RequestContext(String requestId) {
            this.requestId = requestId;
        }

        public RequestContext setToolContext(ToolContext toolContext) {
            this.toolContext = toolContext;
            return this;
        }

        public RequestContext setCallback(Consumer<ChatResponse> callback) {
            this.callback = callback;
            return this;
        }

        public String getRequestId() {
            return requestId;
        }

        public ToolContext getToolContext() {
            return toolContext;
        }

        public Consumer<ChatResponse> getCallback() {
            return callback;
        }

        public long getCreateTime() {
            return createTime;
        }
    }

    /**
     * 创建并注册上下文
     * 
     * @param requestId 请求ID
     * @return 上下文
     */
    public static RequestContext createContext(String requestId) {
        RequestContext context = new RequestContext(requestId);
        CONTEXT_MAP.put(requestId, context);
        logger.debug("创建请求上下文: {}", requestId);
        return context;
    }

    /**
     * 获取上下文
     * 
     * @param requestId 请求ID
     * @return 上下文
     */
    public static RequestContext getContext(String requestId) {
        return CONTEXT_MAP.get(requestId);
    }

    /**
     * 清除上下文
     * 
     * @param requestId 请求ID
     */
    public static void clearContext(String requestId) {
        CONTEXT_MAP.remove(requestId);
        logger.debug("清除请求上下文: {}", requestId);
    }

    /**
     * 获取回调函数
     * 
     * @param requestId 请求ID
     * @return 回调函数
     */
    public static Consumer<ChatResponse> getCallback(String requestId) {
        RequestContext context = CONTEXT_MAP.get(requestId);
        return context != null ? context.getCallback() : null;
    }

    /**
     * 获取工具上下文
     * 
     * @param requestId 请求ID
     * @return 工具上下文
     */
    public static ToolContext getToolContext(String requestId) {
        RequestContext context = CONTEXT_MAP.get(requestId);
        return context != null ? context.getToolContext() : null;
    }

    /**
     * 清理过期上下文（防止内存泄漏）
     * 可以定时任务调用
     * 
     * @param maxAgeMs 最大存活时间（毫秒）
     */
    public static void cleanExpiredContexts(long maxAgeMs) {
        long now = System.currentTimeMillis();
        CONTEXT_MAP.entrySet().removeIf(entry -> {
            boolean expired = (now - entry.getValue().getCreateTime()) > maxAgeMs;
            if (expired) {
                logger.warn("清理过期上下文: {}", entry.getKey());
            }
            return expired;
        });
    }
}
