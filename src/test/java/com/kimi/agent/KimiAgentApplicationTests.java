package com.kimi.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 应用程序集成测试类
 * 
 * @author Kimi
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.ai.openai.api-key=test-key",
    "spring.ai.openai.base-url=http://localhost:9999"
})
class KimiAgentApplicationTests {

    @Test
    void contextLoads() {
        // 验证 Spring 上下文能够正确加载
    }
}
