package com.react.agentdemo.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 聊天消息测试类
 * 
 * @author Kimi
 */
class ChatMessageTest {

    @Test
    @DisplayName("测试默认构造函数")
    void testDefaultConstructor() {
        // 当
        ChatMessage message = new ChatMessage();

        // 则
        assertNotNull(message.getTimestamp());
        assertNull(message.getId());
        assertNull(message.getType());
        assertNull(message.getContent());
    }

    @Test
    @DisplayName("测试带参数的构造函数")
    void testParameterizedConstructor() {
        // 当
        ChatMessage message = new ChatMessage(ChatMessage.MessageType.USER, "你好");

        // 则
        assertEquals(ChatMessage.MessageType.USER, message.getType());
        assertEquals("你好", message.getContent());
        assertNotNull(message.getTimestamp());
    }

    @Test
    @DisplayName("测试完整构造函数")
    void testFullConstructor() {
        // 当
        ChatMessage message = new ChatMessage("msg-123", ChatMessage.MessageType.ASSISTANT, "回复内容");

        // 则
        assertEquals("msg-123", message.getId());
        assertEquals(ChatMessage.MessageType.ASSISTANT, message.getType());
        assertEquals("回复内容", message.getContent());
    }

    @Test
    @DisplayName("测试设置和获取ID")
    void testSetAndGetId() {
        // 给定
        ChatMessage message = new ChatMessage();

        // 当
        message.setId("test-id-123");

        // 则
        assertEquals("test-id-123", message.getId());
    }

    @Test
    @DisplayName("测试设置和获取类型")
    void testSetAndGetType() {
        // 给定
        ChatMessage message = new ChatMessage();

        // 当
        message.setType(ChatMessage.MessageType.THINKING);

        // 则
        assertEquals(ChatMessage.MessageType.THINKING, message.getType());
    }

    @Test
    @DisplayName("测试设置和获取内容")
    void testSetAndGetContent() {
        // 给定
        ChatMessage message = new ChatMessage();

        // 当
        message.setContent("测试内容");

        // 则
        assertEquals("测试内容", message.getContent());
    }

    @Test
    @DisplayName("测试设置和获取时间戳")
    void testSetAndGetTimestamp() {
        // 给定
        ChatMessage message = new ChatMessage();
        LocalDateTime now = LocalDateTime.now();

        // 当
        message.setTimestamp(now);

        // 则
        assertEquals(now, message.getTimestamp());
    }

    @Test
    @DisplayName("测试设置和获取工具名称")
    void testSetAndGetToolName() {
        // 给定
        ChatMessage message = new ChatMessage();

        // 当
        message.setToolName("天气查询");

        // 则
        assertEquals("天气查询", message.getToolName());
    }

    @Test
    @DisplayName("测试创建工具调用消息")
    void testToolCallFactoryMethod() {
        // 当
        ChatMessage message = ChatMessage.toolCall("歌曲搜索");

        // 则
        assertEquals(ChatMessage.MessageType.TOOL_CALL, message.getType());
        assertEquals("为您调用歌曲搜索工具。", message.getContent());
        assertEquals("歌曲搜索", message.getToolName());
    }

    @Test
    @DisplayName("测试消息类型枚举")
    void testMessageTypeEnum() {
        // 则 - 验证所有枚举值存在
        assertNotNull(ChatMessage.MessageType.USER);
        assertNotNull(ChatMessage.MessageType.THINKING);
        assertNotNull(ChatMessage.MessageType.TOOL_CALL);
        assertNotNull(ChatMessage.MessageType.ASSISTANT);

        // 验证枚举值数量
        assertEquals(4, ChatMessage.MessageType.values().length);
    }

    @Test
    @DisplayName("测试字符串表示")
    void testToString() {
        // 给定
        ChatMessage message = new ChatMessage("id-123", ChatMessage.MessageType.USER, "测试");

        // 当
        String str = message.toString();

        // 则
        assertNotNull(str);
        assertTrue(str.contains("id-123"));
        assertTrue(str.contains("USER"));
        assertTrue(str.contains("测试"));
    }
}
