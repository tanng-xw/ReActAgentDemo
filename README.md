# ReAct Agent Demo

基于 Java 21 和 Spring AI 实现的智能助手，支持 ReAct 思维链、多轮对话和动态工具调用。

## 功能特性

- **ReAct 思维链**：展示完整思考过程（观察-思考-行动）
- **动态工具配置**：工具描述、参数描述、枚举值均通过配置文件管理
- **流式响应**：使用 SSE 实现流式响应，实时显示思考过程和最终答案
- **多轮对话**：支持上下文记忆的多轮对话
- **丰富工具集**：
  - 用户地点查询
  - 天气查询
  - 联网知识搜索
  - 曲库歌曲搜索
  - 曲库歌单搜索
  - 槽位匹配（按歌名/艺术家/专辑/曲风/语种/年代）
  - 个性化推荐
  - 榜单查询
  - 播放控制
  - 用户资产获取

## 技术栈

- **后端**：Java 21、Spring Boot 3.2.0、Spring AI 1.1.2
- **前端**：HTML5、CSS3、JavaScript (原生)
- **构建工具**：Maven
- **数据**：Mock JSON 数据（无需外部数据库）

## 快速开始

### 环境要求

- JDK 21 或更高版本
- Maven 3.8+
- OpenAI API Key（或其他兼容 OpenAI API 的大模型服务）

### 配置 API Key

```cmd
set OPENAI_API_KEY=your-api-key-here
set OPENAI_BASE_URL=https://api.openai.com/v1  # 可选，默认为 OpenAI 官方 API
```

或在 `src/main/resources/application.yml` 中配置：

```yaml
spring:
  ai:
    openai:
      api-key: your-api-key-here
      base-url: https://api.openai.com/v1
```

### 启动服务

Windows：
```cmd
# 启动服务
start.bat

# 停止服务
stop.bat

# 重启服务
restart.bat
```

手动构建：
```cmd
mvn clean package -DskipTests
java -jar target/react-agent-demo-1.0.0.jar
```

启动成功后，访问 http://localhost:8080

## 使用说明

1. 打开浏览器访问 http://localhost:8080
2. 在输入框中输入您的问题，例如：
   - "今天北京天气怎么样？"
   - "搜索周杰伦的歌曲"
   - "推荐几首类似稻香的歌"
   - "播放第3首"
3. 助手会展示思考过程，并在需要时调用工具
4. 最终答案会显示在对话中

## 项目结构

```
ReActAgentDemo/
├── src/
│   ├── main/
│   │   ├── java/com/react/agentdemo/
│   │   │   ├── ReActAgentDemoApplication.java      # 应用入口
│   │   │   ├── controller/ChatController.java      # 聊天控制器
│   │   │   ├── service/ChatService.java            # 聊天服务
│   │   │   ├── service/MockDataService.java        # Mock 数据服务
│   │   │   ├── advisor/                            # Advisor 实现
│   │   │   │   └── ThinkingCaptureAdvisor.java     # 思考过程捕获
│   │   │   ├── config/                             # 配置类
│   │   │   │   ├── ToolDescriptionProperties.java  # 工具描述配置属性
│   │   │   │   ├── ToolDescriptionConfig.java      # 工具描述配置加载器
│   │   │   │   └── DynamicToolFactory.java         # 动态工具工厂
│   │   │   ├── tool/                               # 工具调用相关
│   │   │   │   └── ObservingToolCallingManager.java # 可观察的工具调用管理器
│   │   │   ├── tools/                              # 工具实现
│   │   │   │   ├── AgentToolFunctions.java         # 工具函数集合
│   │   │   │   ├── LocationTool.java               # 地点查询工具
│   │   │   │   └── WeatherTool.java                # 天气查询工具
│   │   │   └── model/                              # 数据模型
│   │   │       ├── ChatSession.java                # 会话管理
│   │   │       ├── RelationType.java               # 关系类型枚举
│   │   │       └── enums/                          # 其他枚举
│   │   └── resources/
│   │       ├── application.yml                     # 应用配置
│   │       ├── config/tool-descriptions.json       # 工具描述配置
│   │       ├── prompts/system-prompt.st            # 系统提示词
│   │       ├── mock-data/                          # Mock 数据
│   │       │   ├── web-search.json
│   │       │   ├── song-search.json
│   │       │   ├── songlist-search.json
│   │       │   ├── charts.json
│   │       │   └── user-assets.json
│   │       └── static/index.html                   # 前端页面
│   └── test/                                       # 测试代码
├── start.bat                                       # 启动脚本
├── stop.bat                                        # 停止脚本
├── restart.bat                                     # 重启脚本
├── pom.xml                                         # Maven配置
└── README.md                                       # 项目说明
```

## 核心功能实现方案

### 1. ReAct 思维链实现

ReAct (Reasoning + Acting) 是一种让大语言模型能够进行推理和行动的方法。本项目的实现方案如下：

#### 1.1 系统提示词设计

```
你是一个智能音乐助手，能够帮助用户查询信息、搜索歌曲等。

重要规则：
1. 你可以使用工具来帮助回答用户问题。
2. 在调用工具前，先说明你的思考过程。
3. 你可以一次性并行调用多个工具。
4. **最终答案必须以"回答："开头**（冒号后紧跟内容，不要换行）
5. 如果不需要工具，直接输出最终答案即可。
```

通过系统提示词约束模型的输出格式，使其在思考过程中输出推理内容，最终答案以特定标记开头。

#### 1.2 思考与答案分离机制

在 `ChatService.java` 中，通过解析模型响应内容来区分思考过程和最终答案：

```java
// 检查"回答："或"回答\n"标记
int finalAnswerIndex = content.indexOf(FINAL_ANSWER_PREFIX);
int finalAnswerIndexAlt = content.indexOf(FINAL_ANSWER_PREFIX_ALT);

if (effectiveNewIndex >= 0) {
    // 已经收到 "回答：" 标记
    // 发送之前的思考内容
    String thinkingContent = newContent.substring(0, effectiveNewIndex).trim();
    responseConsumer.accept(ChatResponse.thinking(newThinking, sessionId));
    
    // 发送标记之后的最终答案内容
    String answerContent = newContent.substring(effectiveNewIndex + effectivePrefix.length());
    responseConsumer.accept(ChatResponse.streaming(answerContent, sessionId));
}
```

#### 1.3 流式处理中的边界处理

为了处理流式响应中"回答："标记可能被分割的问题，实现了特殊的边界处理逻辑：

```java
// 如果 newThinking 以 "回答" 结尾，可能是标记的一部分，暂不发送
if (newThinking.endsWith("回答")) {
    newThinking = newThinking.substring(0, newThinking.length() - 2);
    if (!newThinking.isEmpty()) {
        responseConsumer.accept(ChatResponse.thinking(newThinking, sessionId));
    }
    lastSentThinkingLength[0] = newContent.length() - 2;
}
```

### 2. 动态工具配置系统

工具的配置完全通过 `tool-descriptions.json` 文件管理，无需修改代码即可调整工具行为。

#### 2.1 配置结构

```json
{
  "tools": {
    "toolName": {
      "description": "工具描述，LLM通过此描述理解工具用途",
      "parameters": {
        "paramName": {
          "description": "参数描述",
          "type": "string|integer|array|boolean",
          "enum": ["可选的枚举值"],
          "required": true
        }
      }
    }
  }
}
```

#### 2.2 动态加载机制

`ToolDescriptionProperties` 使用 Spring Boot 的 `@ConfigurationProperties` 机制加载配置：

```java
@Configuration
@ConfigurationProperties(prefix = "tools")
public class ToolDescriptionProperties {
    private Map<String, ToolDescription> tools = new HashMap<>();
    // ... getter/setter
}
```

#### 2.3 动态构建 ToolCallback

`DynamicToolFactory` 在应用启动时，根据配置动态构建 `FunctionToolCallback`：

```java
@Bean
public List<ToolCallback> dynamicToolCallbacks() {
    List<ToolCallback> callbacks = new ArrayList<>();
    
    for (Map.Entry<String, ToolDescription> entry : tools.entrySet()) {
        String toolName = entry.getKey();
        ToolDescription desc = entry.getValue();
        
        // 获取对应的 Function 实现
        Function<?, String> function = getFunctionByName(toolName);
        
        // 构建 JSON Schema
        String inputSchema = buildInputSchema(desc.getParameters());
        
        // 创建 ToolCallback
        ToolCallback callback = FunctionToolCallback.builder(toolName, function)
                .description(desc.getDescription())
                .inputType(inputType)
                .inputSchema(inputSchema)
                .build();
        
        callbacks.add(callback);
    }
    return callbacks;
}
```

#### 2.4 JSON Schema 构建

支持多种参数类型：
- **基础类型**：`string`、`integer`、`boolean`
- **数组类型**：支持嵌套的 `items` 定义
- **枚举类型**：通过 `enum` 限制取值范围
- **必填标记**：通过 `required` 标记必填参数

```java
private String buildInputSchema(Map<String, ParameterDefinition> parameters) {
    Map<String, Object> schema = new HashMap<>();
    schema.put("type", "object");
    
    Map<String, Object> properties = new HashMap<>();
    List<String> required = new ArrayList<>();
    
    for (Map.Entry<String, ParameterDefinition> entry : parameters.entrySet()) {
        // 处理不同类型和枚举值
        // ...
    }
    
    schema.put("properties", properties);
    schema.put("required", required);
    return objectMapper.writeValueAsString(schema);
}
```

### 3. 流式响应机制

#### 3.1 SSE 服务端实现

使用 Spring 的 `SseEmitter` 实现 Server-Sent Events：

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamChat(@RequestBody ChatRequest request) {
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
    
    CompletableFuture.runAsync(() -> {
        chatService.processMessage(request.getMessage(), finalSessionId, response -> {
            // 使用 SseEmitter 发送事件
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name("message")
                    .data(response);
            emitter.send(event);
            
            if (response.isDone()) {
                emitter.complete();
            }
        });
    });
    
    return emitter;
}
```

#### 3.2 前端实时展示

前端通过 `EventSource` 接收 SSE 事件，根据响应类型分别展示：
- **思考内容**：显示在思考区域
- **流式答案**：实时追加到答案区域
- **工具调用**：显示"正在调用 XXX 工具"
- **最终结果**：完成时展示完整答案

#### 3.3 ChatClient 流式调用

使用 Spring AI 的 `ChatClient` 进行流式对话：

```java
chatClient.prompt()
    .system(buildSystemPrompt())
    .user(userMessage)
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
    .stream()
    .chatResponse()
    .subscribe(
        chatResponse -> {
            // 处理每个流式响应块
            String chunk = chatResponse.getResult().getOutput().getText();
            // 解析并发送给前端
        },
        error -> { /* 错误处理 */ },
        () -> { /* 完成处理 */ }
    );
```

### 4. 多轮对话上下文管理

#### 4.1 Spring AI ChatMemory

使用 Spring AI 提供的 `MessageChatMemoryAdvisor` 自动管理对话历史：

```java
// 创建 ChatMemory
ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .chatMemoryRepository(new InMemoryChatMemoryRepository())
        .maxMessages(20)  // 保留最近20条消息
        .build();

// 构建 ChatClient
this.chatClient = chatClientBuilder
        .defaultToolCallbacks(toolCallbacks)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .build();
```

#### 4.2 会话隔离

通过 `ChatMemory.CONVERSATION_ID` 参数区分不同会话：

```java
chatClient.prompt()
    .user(userMessage)
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
    .stream()
    // ...
```

每个会话拥有独立的对话历史，互不干扰。

#### 4.3 本地会话管理

`ChatSession` 类提供额外的本地会话管理：

```java
public class ChatSession {
    private static final ConcurrentHashMap<String, ChatSession> SESSIONS = 
        new ConcurrentHashMap<>();
    
    public static ChatSession getOrCreate(String sessionId) {
        return SESSIONS.computeIfAbsent(sessionId, ChatSession::new);
    }
}
```

### 5. 工具调用观察机制

#### 5.1 包装 ToolCallingManager

`ObservingToolCallingManager` 包装了 Spring AI 的 `DefaultToolCallingManager`，在工具调用前后发送事件：

```java
public class ObservingToolCallingManager implements ToolCallingManager {
    private final ToolCallingManager delegate;
    
    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        // 1. 发送思考内容到前端
        AssistantMessage assistantMessage = (AssistantMessage) chatResponse.getResult().getOutput();
        callback.accept(ChatResponse.thinking(assistantMessage.getText(), sessionId));
        
        // 2. 发送工具调用信息（包含参数）
        assistantMessage.getToolCalls().forEach(toolCall -> {
            callback.accept(ChatResponse.toolCallResult(
                toolCall.name(), toolCall.arguments(), null, sessionId, toolCall.id()));
        });
        
        // 3. 执行工具调用
        ToolExecutionResult result = delegate.executeToolCalls(prompt, chatResponse);
        
        // 4. 发送工具执行结果
        extractAndProcessToolResults(result, sessionId, callback);
        
        return result;
    }
}
```

#### 5.2 工具上下文传递

通过 `ToolContextHolder` 传递会话ID等内部参数：

```java
public class ToolContextHolder {
    private static final ThreadLocal<ToolContext> CONTEXT = new ThreadLocal<>();
    
    public static void setContext(ToolContext context) {
        CONTEXT.set(context);
    }
    
    public static ToolContext getContext() {
        return CONTEXT.get();
    }
}
```

工具函数通过 `ToolContextHolder` 获取当前会话上下文：

```java
public Function<Void, String> getUserLocation() {
    return (unused) -> {
        ToolContext context = ToolContextHolder.getContext();
        String sessionId = context.getSessionId();
        // 根据 sessionId 计算用户位置
        return location;
    };
}
```

### 6. 响应数据结构

`ChatResponse` 定义了统一的响应格式：

```java
public class ChatResponse {
    private ResponseType type;      // 响应类型
    private String content;         // 内容
    private String sessionId;       // 会话ID
    private String toolName;        // 工具名称（工具调用时）
    private String toolArguments;   // 工具参数（工具调用时）
    private String toolResult;      // 工具结果（工具调用时）
    private String toolCallId;      // 工具调用ID
    
    public enum ResponseType {
        THINKING,       // 思考过程
        STREAMING,      // 流式答案
        FINAL_ANSWER,   // 最终答案
        TOOL_CALL,      // 工具调用
        ERROR           // 错误
    }
}
```

## 动态工具配置

工具描述、参数描述和枚举值通过 `src/main/resources/config/tool-descriptions.json` 配置：

```json
{
  "tools": {
    "userAsset": {
      "description": "获取用户的音乐资产信息",
      "parameters": {
        "assetType": {
          "description": "资产类型",
          "type": "string",
          "enum": ["play", "favorites"]
        }
      }
    }
  }
}
```

### 配置说明

- **description**: 工具描述，LLM 通过此描述理解工具用途
- **parameters**: 参数定义
  - **type**: 参数类型（string/integer/array/boolean）
  - **description**: 参数描述
  - **enum**: 枚举值列表（可选）
  - **items**: 数组项定义（当 type 为 array 时）

修改配置后需要重启服务生效。

## 扩展工具

要添加新工具，需要：

1. **添加工具函数**：在 `AgentToolFunctions.java` 中添加 Function 方法

```java
public Function<MyToolRequest, String> myTool() {
    return (request) -> {
        // 工具逻辑
        return "执行结果";
    };
}

public record MyToolRequest(String param1, Integer param2) {}
```

2. **注册工具**：在 `DynamicToolFactory.getFunctionByName()` 中注册

```java
private Function<?, String> getFunctionByName(String toolName) {
    return switch (toolName) {
        // ... 已有工具
        case "myTool" -> toolFunctions.myTool();
        default -> null;
    };
}
```

3. **注册输入类型**：在 `getInputType()` 中注册

```java
private Class<?> getInputType(String toolName) {
    return switch (toolName) {
        // ... 已有工具
        case "myTool" -> AgentToolFunctions.MyToolRequest.class;
        default -> Object.class;
    };
}
```

4. **配置描述**：在 `tool-descriptions.json` 中添加配置

```json
{
  "tools": {
    "myTool": {
      "description": "工具描述",
      "parameters": {
        "param1": {
          "description": "参数1描述",
          "type": "string"
        },
        "param2": {
          "description": "参数2描述", 
          "type": "integer"
        }
      }
    }
  }
}
```

## 测试

运行测试：

```cmd
mvn test
```

项目包含以下测试：
- 工具配置测试（动态加载验证）
- 工具类单元测试
- 流式响应测试
- 模型类单元测试

## 配置说明

### application.yml

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}      # OpenAI API Key
      base-url: ${OPENAI_BASE_URL}    # API 基础URL（可选）
      chat:
        options:
          model: gpt-4o-mini          # 使用的模型
          temperature: 0.7            # 温度参数
          max-tokens: 2000            # 最大token数

server:
  port: 8080                        # 服务端口

# 企业网络 SSL 配置（可选）
http:
  ssl:
    insecure: true                  # 禁用 SSL 证书验证（仅内部网络）
```

### tool-descriptions.json

详见 [动态工具配置](#动态工具配置) 章节。

## 架构流程图

```
┌─────────────┐     HTTP POST     ┌─────────────────┐
│   浏览器     │ ────────────────> │ ChatController  │
└─────────────┘                   └────────┬────────┘
     ^                                     │
     │ SSE 流式响应                         │
     │                                     v
     │                            ┌─────────────────┐
     │                            │   ChatService   │
     │                            └────────┬────────┘
     │                                     │
     │                 ┌───────────────────┼───────────────────┐
     │                 │                   │                   │
     │                 v                   v                   v
     │        ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
     │        │   ChatClient │     │ ChatMemory  │     │ ToolContext │
     │        └──────┬──────┘     └─────────────┘     └─────────────┘
     │               │
     │               │ 调用 LLM API
     │               v
     │        ┌─────────────┐
     │        │  Spring AI  │
     │        └──────┬──────┘
     │               │
     │               │ Function Call
     │               v
     │        ┌─────────────────────────┐
     └────────┤ ObservingToolCallingMgr │
              └───────────┬─────────────┘
                          │
          ┌───────────────┼───────────────┐
          v               v               v
   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
   │ LocationTool│ │ WeatherTool │ │  SongTools  │
   └─────────────┘ └─────────────┘ └─────────────┘
```

## 许可证

MIT License
