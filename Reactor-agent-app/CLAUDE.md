[根目录](../CLAUDE.md) > **ai-agent-station-study-app**

# ai-agent-station-study-app 模块

## 模块职责

应用启动模块，负责 Spring Boot 应用启动、全局配置、依赖装配。是系统的入口模块。
在 `019-agent-ddd-convergence` 与 `020-prune-agent-bridges` 中，`app` 额外负责把 runtime port adapter、handlerMap、DataAgent 初始化器和边界守卫测试装配成最终收敛入口。

---

## 入口与启动

### 启动类
```java
org.wwz.ai.Application
```

### 启动方式
```bash
mvn spring-boot:run
```

或

```bash
java -jar target/ai-agent-station-study-app.jar
```

---

## 对外接口

本模块主要提供启动能力，业务接口由 trigger 模块提供。

---

## 关键依赖与配置

### 核心依赖
- `spring-boot-starter-web`: Web 支持
- `spring-boot-starter-test`: 测试支持
- `spring-ai-ollama`: Ollama 本地模型支持
- `spring-ai-starter-model-openai`: OpenAI 模型支持
- `spring-ai-starter-mcp-client-webflux`: MCP 客户端
- `spring-ai-tika-document-reader`: 文档解析
- `mybatis-spring-boot-starter`: MyBatis 集成
- `mysql-connector-java`: MySQL 驱动
- `h2`: H2 数据库（测试用）

### 模块依赖
- `ai-agent-station-study-trigger`: 触发器层
- `ai-agent-station-study-infrastructure`: 基础设施层

### Agent DDD 最终边界（019）
- `ReactorRuntimeAutoConfiguration` 负责把 `RemoteHttpPort`、`RemoteStreamPort`、`FileArtifactPort` 注入 `ReactorRuntimeDependencies`
- `AgentHandlerAutoConfiguration` 负责 runtime handlerMap 拓扑，不允许把这类装配回流到 `domain`
- `DataAgentInitRunner`、`Es7HighLevelClientConfig` 等技术初始化留在 `app`
- `src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java` 负责锁定 GPT query / dataagent bridge 删除结果
- `src/test/java/org/wwz/ai/test/domain/**` 里的边界守卫测试是最终边界验收门槛

---

## 配置说明

### 配置文件
| 文件 | 说明 |
|-----|------|
| `application.yml` | 主配置，指定激活的 profile |
| `application-dev.yml` | 开发环境配置 |
| `application-test.yml` | 测试环境配置 |
| `application-prod.yml` | 生产环境配置 |
| `logback-spring.xml` | 日志配置 |

### 核心配置项

#### 服务器端口
```yaml
server:
  port: 8080
```

#### 数据源配置
```yaml
spring:
  datasource:
    mysql:
      url: jdbc:mysql://127.0.0.1:3306/ai-agent-station
      username: root
      password: 123456
```

#### Spring AI 配置
```yaml
spring:
  ai:
    openai:
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      api-key: ${DASHSCOPE_API_KEY}
    ollama:
      base-url: http://192.168.1.109:11434
```

#### Agent 配置
```yaml
autobots:
  autoagent:
    planner:
      system_prompt: "..."
      max_steps: 40
      model_name: gpt-5.2
    executor:
      system_prompt: "..."
      max_steps: 40
    react:
      system_prompt: "..."
      max_steps: 40
    tool:
      plan_tool: {...}
      code_agent: {...}
      report_tool: {...}
      file_tool: {...}
      deep_search_tool: {...}
```

---

## 数据模型

### 数据库脚本
| 文件 | 说明 |
|-----|------|
| `db/schema.sql` | 数据库表结构 |
| `db/data.sql` | 初始数据 |

### 核心表
- `ai_agent_conversation`: 会话表
- `ai_agent_message`: 消息表
- `ai_agent_message_event`: 消息事件表
- `chat_model_info`: 模型信息表
- `chat_model_schema`: 模型字段表
- `sales_data`: 示例销售数据表

---

## 测试与质量

### 测试目录
`src/test/java/org/wwz/ai/test/`

### 测试分类
| 测试类 | 说明 |
|-------|------|
| `AiAgentTest` | Agent 功能测试 |
| `AutoAgentTest` | 自动 Agent 测试 |
| `FlowAgentTest` | 流程 Agent 测试 |
| `FlowAgentMCPTest` | MCP 功能测试 |
| `AiAgentStepTest` | 步骤执行测试 |
| `AiAgentMCPESTest` | MCP ES 测试 |
| `AiSearchMCPTest` | 搜索 MCP 测试 |
| `OpenAiTest` | OpenAI 测试 |
| `DynamicRateLimitQueryTest` | 动态限流测试 |
| `DynamicAutoAgentTest` | 动态 Agent 测试 |
| `TraePromptTest` | 提示词测试 |
| `ApiTest` | API 测试 |
| `ElkBlacklistDataTest` | ELK 测试 |
| `PrometheusMetricsGeneratorTest` | 监控指标测试 |
| `AgentTest` | 领域测试 |
| `FlowAgentExecuteTest` | 流程执行测试 |
| `FixedAgentExecuteStrategyTest` | 固定策略测试 |
| `StepReactNodeRoutingTest` | ReAct 路由测试 |
| `*DaoTest` | DAO 层测试 |

---

## 常见问题 (FAQ)

**Q: 如何修改启动端口？**
A: 在 `application-dev.yml` 中修改 `server.port`。

**Q: 如何配置数据库？**
A: 在 `application-dev.yml` 中配置 `spring.datasource.mysql`；如果启用问数或向量能力，再按当前实现补充对应 Qdrant / ES / query 数据源配置。

**Q: 如何配置 AI 模型？**
A: 在 `application-dev.yml` 中配置 `spring.ai.openai` 或 `spring.ai.ollama`。

---

## 相关文件清单

### 启动与配置
| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/Application.java` | 启动类 |
| `src/main/java/org/wwz/ai/config/AiAgentConfig.java` | Agent 配置 |
| `src/main/java/org/wwz/ai/config/AiAgentAutoConfiguration.java` | 自动配置 |
| `src/main/java/org/wwz/ai/config/AiAgentAutoConfigProperties.java` | 自动配置属性 |
| `src/main/java/org/wwz/ai/config/DataSourceConfig.java` | 数据源配置 |
| `src/main/java/org/wwz/ai/config/GuavaConfig.java` | Guava 配置 |
| `src/main/java/org/wwz/ai/config/OllamaConfig.java` | Ollama 配置 |
| `src/main/java/org/wwz/ai/config/ThreadPoolConfig.java` | 线程池配置 |
| `src/main/java/org/wwz/ai/config/ThreadPoolConfigProperties.java` | 线程池配置属性 |
| `src/main/java/org/wwz/ai/config/TaskJobAutoConfigBean.java` | 任务调度配置 |

### 资源文件
| 文件路径 | 说明 |
|---------|------|
| `src/main/resources/application.yml` | 主配置 |
| `src/main/resources/application-dev.yml` | 开发配置 |
| `src/main/resources/application-test.yml` | 测试配置 |
| `src/main/resources/application-prod.yml` | 生产配置 |
| `src/main/resources/logback-spring.xml` | 日志配置 |
| `src/main/resources/db/schema.sql` | 数据库结构 |
| `src/main/resources/db/data.sql` | 初始数据 |
| `src/main/resources/mybatis/config/mybatis-config.xml` | MyBatis 配置 |
| `src/main/resources/mybatis/mapper/*.xml` | Mapper XML |

---

## 变更记录 (Changelog)

### 2026-04-07
- 初始化模块文档
