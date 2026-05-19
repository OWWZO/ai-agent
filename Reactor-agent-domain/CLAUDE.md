[根目录](../CLAUDE.md) > **ai-agent-station-study-domain**

# ai-agent-station-study-domain 模块

## 模块职责

核心业务逻辑层，包含领域模型、领域服务、仓储接口定义。当前 Agent 有界上下文已按 `runtime / ledger / memory / rag / role` 收敛核心能力，并仅保留少量 `service` / `reactor` 延期契约与策略节点。

Reactor Phase 1 之后，本模块不再承载 legacy HTTP controller，也不再承载低风险 Spring 装配类型；这些职责分别回到 `trigger` 与 `app`。当前收敛阶段新增 `ai-agent-station-study-case` 作为应用编排层，`domain` 对外优先保留领域模型、领域服务、仓储端口与外部能力 port 定义，不再新增 HTTP / SSE 协议适配职责。

---

## 入口与启动

本模块为纯业务逻辑模块，无启动类，由 app 模块启动时扫描加载。

---

## 对外接口

### 领域服务接口
| 接口 | 职责 |
|-----|------|
| `IRagService` | RAG 检索服务 |
| `AgentQueryService` | GPT 查询与多智能体运行时查询服务 |
| `DataAgentQueryService` | 数据问答运行时查询服务 |
| `ExecutionLedgerQueryService` | 执行账本查询服务 |

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

### Reactor Phase 2A 边界
- `domain` 不再承载 Reactor `@Mapper`、`BaseMapper` 或 `org.wwz.ai.domain.agent.reactor.mapper.*`
- chat-model 元数据通过 `IChatModelMetadataRepository` 端口访问持久化，`ChatModelInfoService`、`ChatModelSchemaService` 不再继承 `ServiceImpl`
- `SessionContextMemoryService`、`IWorkspaceImageGenerationService` 仍在 `domain` 定义接口，但其基于 DAO 的技术实现已下沉到 `infrastructure`
- tool-output 读写与 execution ledger 持久化协作统一依赖 `infrastructure.dao.reactor.*`

### Agent DDD 最终边界（019）
- `trigger` 只能依赖 `case` 暴露的 dispatch / role / rag / query / dataagent seam
- `domain` 中禁止新增 `SseEmitter`、`new OkHttpClient`、`JdbcDataProvider`、`SpringContextHolder`、`applicationContext.getBean(...)`
- `RemoteHttpPort`、`RemoteStreamPort`、`FileArtifactPort`、`DataQueryExecutionPort`、`DataQueryMetadataPort` 是当前稳定 seam
- GPT query / dataagent bridge 已删除，新增逻辑必须优先进入 `runtime / ledger / memory / rag / role`
- `domain/agent/service`、`domain/agent/reactor` 中剩余内容仅允许作为延期契约或策略节点，禁止继续新增 catch-all 主逻辑
- 已删除的旧 handler bridge：`AgentHandlerService`、`AgentHandlerFactory`、`PlanSolveHandlerImpl`、`ReactHandlerImpl`
- 明确延期的 legacy contract 包：`reactor/config/data/**`、`reactor/model/{req,response,multi,dto,imagegeneration}/**`、`reactor/service/**`、`service/{execute,armory,runtime}/**`
- 延期目录必须带中文职责说明，且只允许承载已登记语义，禁止继续扩张

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
| `src/main/java/org/wwz/ai/domain/agent/rag/IRagService.java` | RAG 服务接口 |
| `src/main/java/org/wwz/ai/domain/agent/rag/RagService.java` | RAG 服务实现 |
| `src/main/java/org/wwz/ai/domain/agent/runtime/AgentQueryService.java` | GPT 查询稳定运行时 seam |
| `src/main/java/org/wwz/ai/domain/agent/rag/DataAgentQueryService.java` | 数据问答稳定查询 seam |

### 执行策略
| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/domain/agent/service/execute/auto/step/factory/DefaultAutoAgentExecuteStrategyFactory.java` | 自动 Agent 策略工厂 |
| `src/main/java/org/wwz/ai/domain/agent/service/execute/flow/step/factory/DefaultFlowAgentExecuteStrategyFactory.java` | 流程 Agent 策略工厂 |
| `src/main/java/org/wwz/ai/domain/agent/service/armory/node/factory/DefaultArmoryStrategyFactory.java` | 装配策略工厂 |

### Reactor 核心
| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/domain/agent/reactor/model/req/AgentRequest.java` | 延期保留的 legacy Agent 请求契约 |
| `src/main/java/org/wwz/ai/domain/agent/runtime/agent/BaseAgent.java` | Agent 基类 |
| `src/main/java/org/wwz/ai/domain/agent/runtime/agent/PlanningAgent.java` | 规划 Agent |
| `src/main/java/org/wwz/ai/domain/agent/runtime/agent/ReactImplAgent.java` | ReAct Agent |
| `src/main/java/org/wwz/ai/domain/agent/runtime/agent/SummaryAgent.java` | 总结 Agent |
| `src/main/java/org/wwz/ai/domain/agent/runtime/agent/AgentContext.java` | Agent 上下文 |
| `src/main/java/org/wwz/ai/domain/agent/runtime/enums/AgentState.java` | Agent 状态 |
| `src/main/java/org/wwz/ai/domain/agent/runtime/enums/AgentType.java` | Agent 类型 |
| `src/main/java/org/wwz/ai/domain/agent/ledger/ExecutionLedgerQueryService.java` | 执行账本查询 |
| `src/main/java/org/wwz/ai/domain/agent/memory/SessionContextMemoryService.java` | 会话记忆服务 |
| `src/main/java/org/wwz/ai/domain/agent/role/IFixRoleService.java` | 角色库领域服务 |
| `src/main/java/org/wwz/ai/domain/agent/reactor/service/IAgentConversationService.java` | 会话服务接口 |
| `src/main/java/org/wwz/ai/domain/agent/reactor/service/IAgentStreamPersistService.java` | 流式持久化接口 |

---

## 变更记录 (Changelog)

### 2026-04-07
- 初始化模块文档
