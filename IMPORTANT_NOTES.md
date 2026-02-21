# 项目关键信息记录

## 架构设计原则

### 1. 使用 ChatClient 而非 ChatModel

**原则说明**：
- **ChatClient** 是 Spring AI 推荐的高层抽象 API
- **ChatModel** 是底层实现细节，不推荐直接使用

**原因**：
- ChatClient 提供更简洁的 fluent API
- 自动处理工具调用循环
- 更好的抽象层次，符合 Spring AI 最佳实践
- 更容易测试和维护

**正确用法示例**：
```java
// 推荐：使用 ChatClient
@Autowired
private ChatClient chatClient;

public String chat(String message) {
    return chatClient.prompt()
        .user(message)
        .tools(myTools)  // 自动处理工具调用
        .call()
        .content();
}
```

**避免用法**：
```java
// 不推荐：直接使用 ChatModel
@Autowired
private ChatModel chatModel;

public String chat(String message) {
    // 需要手动处理工具调用、构建 Prompt 等
    ChatResponse response = chatModel.call(prompt);
    // 需要手动检查 toolCalls 并执行
}
```

## 工具调用最佳实践

### 使用 @Tool 注解定义工具
```java
@Component
public class MyTools {
    @Tool(description = "工具描述，帮助模型理解用途")
    public String myTool(@ToolParam(description = "参数描述") String param) {
        // 工具逻辑
    }
}
```

### 工具注册方式
```java
// 方式1：通过 ChatClient.Builder 注册默认工具
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultTools(new MyTools())
    .build();

// 方式2：每次调用时动态传入
String response = chatClient.prompt()
    .user(message)
    .tools(new MyTools())  // 动态传入
    .call()
    .content();
```

## 版本信息

- **Spring Boot**: 3.2.0
- **Spring AI**: 1.1.2
- **Java**: 21

## 参考资料

- Spring AI 官方文档: https://docs.spring.io/spring-ai/reference/
- Spring AI Tool API文档: doc/spring-ai/tools.adoc
- Spring AI ChatClient API 文档: doc/spring-ai/Chat Client API.adoc
- Spring AI Chat Memory API 文档: doc/spring-ai/chat-memory.adoc
- Spring AI prompt API 文档: doc/spring-ai/prompt.adoc
- Spring AI advisors API 文档: doc/spring-ai/advisors.adoc
