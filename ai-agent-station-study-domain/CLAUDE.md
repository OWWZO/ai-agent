[根目录](../CLAUDE.md) > **ai-agent-station-study-domain**

# ai-agent-station-study-domain 模块

## 模块职责

核心业务逻辑层，包含领域模型、领域服务、仓储接口定义。实现 Agent 执行引擎、任务调度、策略模式等核心能力。

Reactor Phase 1 之后，本模块不再承载 legacy HTTP controller，也不再承载低风险 Spring 装配类型；这些职责分别回到 `trigger` 与 `app`。`domain` 对外只保留领域模型、领域服务和仓储端口定义。

---

## 入口与启动

本模块为纯业务逻辑模块，无启动类，由 app 模块启动时扫描加载。

---

## 对外接口

### 领域服务接口
| 接口 | 职责 |
|-----|------|
| `IAgentDispatchService` | Agent 策略调度器 |
| `IArmoryService` | Agent 装配服务 |
| `ITaskService` | 任务服务 |
| `IRagService` | RAG 检索服务 |
| `IAgentConversationService` | 会话管理服务 |
| `IAgentStreamPersistService` | 流式消息持久化服务 |
| `IGptProcessService` | GPT 处理服务 |

### 执行策略工厂
| 工厂 | 职责 |
|-----|------|
| `DefaultAutoAgentExecuteStrategyFactory` | 自动 Agent 执行策略工厂 |
| `DefaultFlowAgentExecuteStrategyFactory` | 流程 Agent 执行策略工厂 |
| `DefaultArmoryStrategyFactory` | 装配策略工厂 |

---

## 关键依赖与配置

### 核心依赖
- `spring-ai-starter-model-openai`: Spring AI OpenAI 支持
- `spring-ai-starter-mcp-client-webflux`: MCP 客户端
- `spring-ai-pgvector-store`: pgvector 向量存储
- `mybatis-plus-spring-boot3-starter`: MyBatis-Plus
- `xfg-wrench-starter-design-framework`: 扳手设计模式框架
- `clickhouse-jdbc`: ClickHouse 支持
- `elasticsearch-rest-high-level-client`: ES 客户端
- `okhttp/okhttp-sse`: HTTP/SSE 客户端

---

## 数据模型

### 实体 (Entity)
- `AgentExecuteResultEntity`: Agent 执行结果
- `ArmoryCommandEntity`: 装配命令
- `ExecuteCommandEntity`: 执行命令
- `ExecutionPlanStep`: 执行计划步骤
- `JoyAgentEvent`: Agent 事件

### 值对象 (VO)
- `AiAgentVO`: Agent 值对象
- `AiClientVO`: 客户端值对象
- `AiClientApiVO`: API 值对象
- `AiClientAdvisorVO`: Advisor 值对象
- `AiClientModelVO`: 模型值对象
- `AiClientSystemPromptVO`: 系统提示词值对象
- `AiClientToolMcpVO`: MCP 工具值对象
- `AiAgentTaskScheduleVO`: 任务调度 VO
- `AiAgentClientFlowConfigVO`: 流程配置 VO
- `AiRagOrderVO`: RAG 订单 VO

### 枚举
- `AiAgentEnumVO`: Agent 类型枚举
- `AiClientTypeEnumVO`: 客户端类型枚举

### Reactor 包模型
- `AgentRequest`: Agent 请求
- `AgentContext`: Agent 上下文
- `BaseAgent`: Agent 基类
- `PlanningAgent`: 规划 Agent
- `ReactImplAgent`: ReAct 实现
- `SummaryAgent`: 总结 Agent
- `AgentState`: Agent 状态枚举
- `AgentType`: Agent 类型枚举

### Reactor Phase 1 边界
- legacy `/1/**`、`/data/**` controller 已迁到 `ai-agent-station-study-trigger`
- `ReplayProjectorAutoConfiguration`、`DataAgentInitRunner`、`Es7HighLevelClientConfig` 已迁到 `ai-agent-station-study-app`
- execution ledger 只在本模块定义 `IExecutionLedgerReadRepository`、`IExecutionLedgerWriteRepository` 端口，由 `infrastructure` 提供生产实现
- `ReactorConfig` 仍是过渡态共享配置契约，本期仅允许通过测试和文档锁边界，不做物理迁移
- `SessionContextMemoryServiceImpl`、tool-output 读写、workspace-image 与 ledger 持久化类型物理迁移仍属于后续阶段

---

## 测试与质量

### 核心测试
- `AgentTest`: Agent 领域测试
- `AutoAgentTest`: 自动 Agent 测试
- `FlowAgentExecuteTest`: 流程执行测试
- `FixedAgentExecuteStrategyTest`: 固定策略测试
- `StepReactNodeRoutingTest`: ReAct 路由测试

---

## 常见问题 (FAQ)

**Q: 执行策略工厂的作用是什么？**
A: 工厂模式封装不同 Agent 类型的执行逻辑，支持 AutoAgent、FlowAgent 等不同执行策略的切换。

**Q: Reactor 包是什么？**
A: Reactor 是 Agent 执行引擎的核心实现，包含 Agent 生命周期管理、工具调用、消息处理等。

---

## 相关文件清单

### 领域服务
| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/domain/agent/service/IAgentDispatchService.java` | Agent 调度接口 |
| `src/main/java/org/wwz/ai/domain/agent/service/IArmoryService.java` | 装配服务接口 |
| `src/main/java/org/wwz/ai/domain/agent/service/ITaskService.java` | 任务服务接口 |
| `src/main/java/org/wwz/ai/domain/agent/service/IRagService.java` | RAG 服务接口 |
| `src/main/java/org/wwz/ai/domain/agent/service/armory/ArmoryService.java` | 装配服务实现 |
| `src/main/java/org/wwz/ai/domain/agent/service/task/AiAgentTaskService.java` | 任务服务实现 |
| `src/main/java/org/wwz/ai/domain/agent/service/rag/RagService.java` | RAG 服务实现 |

### 执行策略
| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/domain/agent/service/execute/auto/step/factory/DefaultAutoAgentExecuteStrategyFactory.java` | 自动 Agent 策略工厂 |
| `src/main/java/org/wwz/ai/domain/agent/service/execute/flow/step/factory/DefaultFlowAgentExecuteStrategyFactory.java` | 流程 Agent 策略工厂 |
| `src/main/java/org/wwz/ai/domain/agent/service/armory/node/factory/DefaultArmoryStrategyFactory.java` | 装配策略工厂 |

### Reactor 核心
| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/domain/agent/reactor/model/req/AgentRequest.java` | Agent 请求 |
| `src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java` | Agent 基类 |
| `src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/PlanningAgent.java` | 规划 Agent |
| `src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/ReactImplAgent.java` | ReAct Agent |
| `src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/SummaryAgent.java` | 总结 Agent |
| `src/main/java/org/wwz/ai/domain/agent/reactor/agent/context/AgentContext.java` | Agent 上下文 |
| `src/main/java/org/wwz/ai/domain/agent/reactor/agent/enums/AgentState.java` | Agent 状态 |
| `src/main/java/org/wwz/ai/domain/agent/reactor/agent/enums/AgentType.java` | Agent 类型 |
| `src/main/java/org/wwz/ai/domain/agent/reactor/service/IAgentConversationService.java` | 会话服务接口 |
| `src/main/java/org/wwz/ai/domain/agent/reactor/service/IAgentStreamPersistService.java` | 流式持久化接口 |
| `src/main/java/org/wwz/ai/domain/agent/reactor/service/IGptProcessService.java` | GPT 处理接口 |

---

## 变更记录 (Changelog)

### 2026-04-07
- 初始化模块文档
