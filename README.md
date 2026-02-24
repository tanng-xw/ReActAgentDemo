# 智能音乐助手 (Kimi Agent)

基于 Java 21 和 Spring AI 实现的智能助手，支持多轮对话和工具调用。

## 功能特性

- **多轮对话支持**：支持多轮自然语言对话
- **工具调用**：支持调用多种工具完成复杂任务
  - 用户地点查询：根据会话ID返回用户所在城市
  - 天气查询：查询指定城市的天气信息
  - 歌曲搜索：根据关键词搜索歌曲
- **上下文记忆**：同一会话中保持上下文
- **流式响应**：使用 SSE 实现流式响应，实时显示思考过程和最终答案

## 技术栈

- **后端**：Java 21、Spring Boot 3.2.0、Spring AI 1.1.2
- **前端**：HTML5、CSS3、JavaScript (原生)
- **构建工具**：Maven

## 快速开始

### 环境要求

- JDK 21 或更高版本
- Maven 3.8+
- OpenAI API Key

### 配置 API Key

在开始之前，需要设置 OpenAI API Key：

```cmd
set OPENAI_API_KEY=your-api-key-here
```

或者修改 `src/main/resources/application.yml` 文件中的配置。

### 启动服务

在 Windows 环境下，使用提供的脚本启动：

```cmd
# 启动服务
start.bat

# 停止服务
stop.bat

# 重启服务
restart.bat
```

启动成功后，访问 http://localhost:8080 即可使用。

### 手动构建和运行

```cmd
# 编译项目
mvn clean package -DskipTests

# 运行
java -jar target/react-agent-demo-1.0.0.jar
```

## 使用说明

1. 打开浏览器访问 http://localhost:8080
2. 在输入框中输入您的问题，例如：
   - "今天天气怎么样？"
   - "搜索周杰伦的歌曲"
   - "我在哪个城市？"
3. 助手会展示思考过程，并在需要时调用工具
4. 最终答案会显示在对话中

## 项目结构

```
KimiMusicAgent/
├── src/
│   ├── main/
│   │   ├── java/com/kimi/agent/
│   │   │   ├── ReActAgentDemoApplication.java    # 应用入口
│   │   │   ├── controller/ChatController.java # 聊天控制器
│   │   │   ├── service/ChatService.java     # 聊天服务
│   │   │   ├── service/ToolService.java     # 工具服务
│   │   │   ├── model/                       # 数据模型
│   │   │   └── tools/                       # 工具实现
│   │   └── resources/
│   │       ├── application.yml              # 配置文件
│   │       ├── static/index.html            # 前端页面
│   │       └── prompts/system-prompt.st     # 系统提示词
│   └── test/                                # 测试代码
├── start.bat                                # 启动脚本
├── stop.bat                                 # 停止脚本
├── restart.bat                              # 重启脚本
├── pom.xml                                  # Maven配置
└── README.md                                # 项目说明
```

## 扩展工具

要添加新的工具，需要：

1. 实现 `Tool<T>` 接口
2. 在 `ToolService` 中注册新工具
3. 更新系统提示词说明新工具

示例：

```java
@Component
public class MyTool implements Tool<String> {
    @Override
    public String getName() {
        return "我的工具";
    }
    
    @Override
    public String getDescription() {
        return "工具描述";
    }
    
    @Override
    public Map<String, String> getParameters() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("param1", "参数1描述");
        return params;
    }
    
    @Override
    public String execute(Map<String, Object> params, String sessionId) {
        // 工具逻辑
        return "执行结果";
    }
}
```

## 测试

运行测试：

```cmd
mvn test
```

项目包含以下测试：
- 工具类单元测试
- 模型类单元测试
- 服务类单元测试
- 控制器单元测试

## 配置说明

主要配置项在 `application.yml` 中：

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
```

## 许可证

MIT License
