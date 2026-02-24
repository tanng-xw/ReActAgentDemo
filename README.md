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
│   │   │   ├── config/                             # 配置类
│   │   │   │   ├── ToolDescriptionProperties.java  # 工具描述配置属性
│   │   │   │   ├── ToolDescriptionConfig.java      # 工具描述配置加载器
│   │   │   │   └── DynamicToolFactory.java         # 动态工具工厂
│   │   │   ├── tools/                              # 工具实现
│   │   │   │   ├── AgentToolFunctions.java         # 工具函数集合
│   │   │   │   ├── LocationTool.java               # 地点查询工具
│   │   │   │   ├── WeatherTool.java                # 天气查询工具
│   │   │   │   └── ...
│   │   │   └── model/                              # 数据模型
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

3. **配置描述**：在 `tool-descriptions.json` 中添加配置

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

## 许可证

MIT License
