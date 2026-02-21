package com.kimi.agent.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 聊天会话测试类
 * 
 * @author Kimi
 */
class ChatSessionTest {

    @AfterEach
    void tearDown() {
        // 清理测试会话
        ChatSession.remove("test-session-1");
        ChatSession.remove("test-session-2");
        ChatSession.remove("test-session-new");
    }

    @Test
    @DisplayName("测试创建新会话")
    void testCreateNew() {
        // 当
        ChatSession session = ChatSession.createNew();

        // 则
        assertNotNull(session);
        assertNotNull(session.getSessionId());
        assertFalse(session.getSessionId().isEmpty());
        assertTrue(session.getMessages().isEmpty());
        assertTrue(session.getCreateTime() > 0);
    }

    @Test
    @DisplayName("测试获取或创建会话")
    void testGetOrCreate() {
        // 给定
        String sessionId = "test-session-1";

        // 当 - 第一次获取
        ChatSession session1 = ChatSession.getOrCreate(sessionId);

        // 则
        assertNotNull(session1);
        assertEquals(sessionId, session1.getSessionId());

        // 当 - 再次获取同一个ID
        ChatSession session2 = ChatSession.getOrCreate(sessionId);

        // 则 - 应该是同一个实例
        assertSame(session1, session2);
    }

    @Test
    @DisplayName("测试添加消息到会话")
    void testAddMessage() {
        // 给定
        ChatSession session = ChatSession.createNew();
        ChatMessage message = new ChatMessage(ChatMessage.MessageType.USER, "你好");

        // 当
        session.addMessage(message);

        // 则
        assertEquals(1, session.getMessageCount());
        List<ChatMessage> messages = session.getMessages();
        assertEquals("你好", messages.get(0).getContent());
    }

    @Test
    @DisplayName("测试获取消息列表是副本")
    void testGetMessagesReturnsCopy() {
        // 给定
        ChatSession session = ChatSession.createNew();
        session.addMessage(new ChatMessage(ChatMessage.MessageType.USER, "消息1"));

        // 当
        List<ChatMessage> messages1 = session.getMessages();
        messages1.add(new ChatMessage(ChatMessage.MessageType.USER, "消息2"));

        // 则 - 原始会话不应该受影响
        assertEquals(1, session.getMessageCount());
    }

    @Test
    @DisplayName("测试清空会话消息")
    void testClearMessages() {
        // 给定
        ChatSession session = ChatSession.createNew();
        session.addMessage(new ChatMessage(ChatMessage.MessageType.USER, "消息1"));
        session.addMessage(new ChatMessage(ChatMessage.MessageType.ASSISTANT, "回复1"));

        // 当
        session.clearMessages();

        // 则
        assertEquals(0, session.getMessageCount());
        assertTrue(session.getMessages().isEmpty());
    }

    @Test
    @DisplayName("测试移除会话")
    void testRemove() {
        // 给定
        String sessionId = "test-session-remove";
        ChatSession session = ChatSession.getOrCreate(sessionId);
        assertNotNull(session);

        // 当
        ChatSession.remove(sessionId);

        // 则 - 移除后应该创建新实例
        ChatSession newSession = ChatSession.getOrCreate(sessionId);
        assertNotSame(session, newSession);
    }

    @Test
    @DisplayName("测试多会话独立")
    void testMultipleSessionsIndependent() {
        // 给定
        ChatSession session1 = ChatSession.getOrCreate("test-session-1");
        ChatSession session2 = ChatSession.getOrCreate("test-session-2");

        // 当
        session1.addMessage(new ChatMessage(ChatMessage.MessageType.USER, "会话1消息"));
        session2.addMessage(new ChatMessage(ChatMessage.MessageType.USER, "会话2消息"));

        // 则
        assertEquals(1, session1.getMessageCount());
        assertEquals(1, session2.getMessageCount());
        assertEquals("会话1消息", session1.getMessages().get(0).getContent());
        assertEquals("会话2消息", session2.getMessages().get(0).getContent());
    }

    @Test
    @DisplayName("测试会话字符串表示")
    void testToString() {
        // 给定
        ChatSession session = ChatSession.createNew();
        session.addMessage(new ChatMessage(ChatMessage.MessageType.USER, "测试"));

        // 当
        String str = session.toString();

        // 则
        assertNotNull(str);
        assertTrue(str.contains(session.getSessionId()));
        assertTrue(str.contains("messages.size=1"));
    }

    @Test
    @DisplayName("测试会话创建时间")
    void testCreateTime() {
        // 给定
        long before = System.currentTimeMillis();

        // 当
        ChatSession session = ChatSession.createNew();

        // 给定
        long after = System.currentTimeMillis();

        // 则
        long createTime = session.getCreateTime();
        assertTrue(createTime >= before);
        assertTrue(createTime <= after);
    }
}
