[根目录](../CLAUDE.md) > **ai-agent-station-study-case**

# ai-agent-station-study-case 模块

## 模块职责

应用编排层（Application Layer）。`019-agent-ddd-convergence` 起，本模块作为 Trigger 进入主链路的**唯一稳定 seam**：负责按 agent 类型 dispatch 执行策略、装配（armory）、查询（gpt query）、知识库（rag）、数据问答（dataquery）、角色库（role）、任务调度（task）、匿名访客与会话归属（visitor）等跨领域应用编排，不承载底层协议细节，也不再回流到已被 `020-prune-agent-bridges` 删除的 legacy reactor bridge。

边界要点：

- Trigger 入口必须依赖本层接口（`IAgentDispatchService` / `IExecuteStrategy` / `IGptQueryApplicationService` / `IDataAgentApplicationService` / `IFixRoleQueryService` / `IRagApplicationService` / `IArmoryService` / `ITaskService` 等），不允许直连 `domain/service` 根接口
- `AgentSessionStream` / `AgentSessionPrinter` 是 case 暴露给 trigger 的**协议无关流式输出端口**；SSE / WebSocket 协议适配只能停在 `trigger` 侧
- 本层不直接 `new OkHttpClient`、不持有 `SseEmitter`、不调用 `ApplicationContext.getBean(...)`；远端能力通过 domain 暴露的 port → infrastructure 适配落地
- `domain` 中遗留的 `domain/agent/service` / `domain/agent/reactor` 仅作为延期契约或策略节点供本层消费，禁止把新主链路逻辑下沉回去

---

## 入口与启动

本模块为应用编排模块，无独立启动类，由 `ai-agent-station-study-app` 启动时扫描 `org.wwz.ai.application.agent.**` 包加载。

---

## 对外接口

### Dispatch / Execute（核心调度链）
| 接口 / 实现 | 职责 |
|------------|------|
| `IAgentDispatchService` / `AgentDispatchService` | 按 `AgentType`（WORKFLOW / PLAN_SOLVE / REACT）查 `executeStrategyMap` 命中策略；缺省回退 ReAct |
| `IExecuteStrategy` | 应用层执行策略契约，输入 `AgentRequest` + `AgentSessionStream` |
| `FlowAgentExecuteStrategy` | Workflow 流程 Agent 执行策略 |
| `PlanSolveAgentExecuteStrategy` | Plan-Solve（规划+执行）双模型策略 |
| `ReactAgentExecuteStrategy` | ReAct 思考-行动循环策略（默认） |

### Stream（协议无关输出端口）
| 类型 | 职责 |
|------|------|
| `AgentSessionStream` | 应用层会话输出端口（继承 `AgentMessageStream`），由 trigger 侧用 SSE / WebSocket 协议适配实现 |
| `AgentSessionPrinter` | 实现 domain `Printer`，把 `AgentResponse` 协议消息（tool_call / tool_thought / plan / task / result / agent_stream / browser / file 等）统一写入 `AgentSessionStream` |

### Armory（Agent 装配）
| 接口 / 实现 | 职责 |
|------------|------|
| `IArmoryService` / `AgentArmoryApplicationService` | 装配可用 Agent 列表、按 agentId 装配、按 apiId 装配模型 API |

### Query / RAG / DataQuery / Role
| 接口 / 实现 | 职责 |
|------------|------|
| `IGptQueryApplicationService` / `GptQueryApplicationService` | GPT 流式查询的唯一 case seam，替代已删除的 `IGptProcessService` bridge |
| `IRagApplicationService` / `RagApplicationService` | 知识库文件入库 seam，给 admin 入口用 |
| `IDataAgentApplicationService` / `DataAgentApplicationService` | 数据问答 chat / NL2SQL / vector recall / es recall / preview，替代已删除的 `DataAgentService` + `Nl2SqlService` bridge |
| `IFixRoleQueryService` / `FixRoleQueryApplicationService` | 角色库查询 seam（可用角色、默认角色、按 agentId 查角色） |

### Task（定时任务编排）
| 接口 / 实现 | 职责 |
|------------|------|
| `ITaskService` / `AgentTaskApplicationService` | 查询有效/失效的任务调度，供 trigger 层 `AgentTaskJob` 消费 |

### Visitor（匿名访客 + 会话归属）
| 类型 | 职责 |
|------|------|
| `AnonymousVisitorApplicationService` | 解析或创建匿名访客身份，hash token，活跃心跳 |
| `AnonymousVisitorBootstrapApplicationService` | 访客首登 bootstrap |
| `AnonymousVisitorNamingApplicationService` | 访客命名 |
| `ConversationSessionOwnershipApplicationService` | 会话首访绑定归属，二次访问校验归属（依赖 `IExecutionLedgerRead/WriteRepository`） |
| `SessionOwnershipDeniedException` | 归属校验失败异常 |
| `model/AnonymousVisitorIdentity`、`model/AnonymousVisitorProfile` | 应用层访客模型 |

---

## 关键依赖与配置

### 模块依赖（见 `pom.xml`）
- `ai-agent-station-study-api` — DTO / 接口契约
- `ai-agent-station-study-domain` — 领域模型与 port 定义
- `ai-agent-station-study-types` — 常量、异常、枚举

> 注意：本模块**不直接依赖** `infrastructure` 与 `trigger`；外部能力通过 domain 暴露的 port（`RemoteHttpPort` / `RemoteStreamPort` / `FileArtifactPort` / `DataQuery*Port` / `IExecutionLedgerRead/WriteRepository` 等）访问，由 `infrastructure` 在运行时注入。

### 第三方依赖
- `lombok`（编译期）
- `junit`（仅 test scope）

---

## 数据模型

本模块**不持久化数据**，仅消费/转发 domain 模型；少量本地模型集中在 `application/agent/visitor/model/`：

- `AnonymousVisitorIdentity` — 应用层访客身份（visitorId + rawToken + 状态）
- `AnonymousVisitorProfile` — 应用层访客画像

---

## 测试与质量

本模块当前不带独立测试；回归测试由 `ai-agent-station-study-app` 模块的测试集中承担：

- `*BoundaryTest` — 019/020 边界守卫测试，验证 trigger 不再绕过 case 直连 domain
- `AgentContextConvergenceBoundaryTest` 等 — 锁定 GPT query / dataagent bridge 已删除

跑法：

```bash
mvn -pl ai-agent-station-study-app test -Dtest='*BoundaryTest' -DskipTests=false
```

---

## 常见问题 (FAQ)

**Q: 为什么 dispatch 用 `Map<String, IExecuteStrategy>` 注入而不是 if-else？**
A: Spring 自动按 bean name 注入策略表（`flowAgentExecuteStrategy` / `planSolveAgentExecuteStrategy` / `reactAgentExecuteStrategy`），新增策略只需加 `@Service` 类即可，符合开闭原则。

**Q: `AgentSessionStream` 与 domain 的 `AgentMessageStream` 是什么关系？**
A: `AgentSessionStream` 直接继承 `AgentMessageStream`。这层继承的目的是**让应用层有自己的输出语义命名**，并允许后续按 case 子域演进，而 domain 不必感知协议层（SSE / WebSocket）。

**Q: 为什么 `IDataAgentApplicationService` 接口这么大（chat / NL2SQL / recall / preview 全在一起）？**
A: 这是 `020-prune-agent-bridges` 把原先 `DataAgentService` + `Nl2SqlService` 两条 bridge 合并落地到 case 后的过渡形态。后续可按读/写、按子能力进一步拆分，但当前必须保持单一 seam，避免 trigger 重新分散依赖。

**Q: 本模块会持有 `SseEmitter` 吗？**
A: 永远不会。`SseEmitter` 只允许在 `ai-agent-station-study-trigger` 中存活，由 trigger 实现 `AgentSessionStream` 完成协议适配。

---

## 相关文件清单

### 包结构
```
org.wwz.ai.application.agent
├── armory/        # Agent 装配
├── dataquery/     # 数据问答（chat / NL2SQL / recall）
├── dispatch/      # 按 AgentType 分发执行策略
├── execute/       # ReAct / PlanSolve / Workflow 三类策略
│   ├── planexecute/
│   ├── react/
│   └── workflow/
├── query/         # GPT 流式查询
├── rag/           # 知识库入库
├── role/          # 角色库查询
├── stream/        # 协议无关流式输出端口与 Printer 适配
├── task/          # 任务调度查询
├── visitor/       # 匿名访客 + 会话归属
│   └── model/
└── package-info.java
```

### 关键文件
| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/application/agent/package-info.java` | 应用编排层包注释（dispatch / execute / armory / task seam 声明） |
| `src/main/java/org/wwz/ai/application/agent/dispatch/AgentDispatchService.java` | Dispatch 实现（按 AgentType 选择策略） |
| `src/main/java/org/wwz/ai/application/agent/execute/IExecuteStrategy.java` | 应用层执行策略契约 |
| `src/main/java/org/wwz/ai/application/agent/execute/react/ReactAgentExecuteStrategy.java` | ReAct 策略 |
| `src/main/java/org/wwz/ai/application/agent/execute/planexecute/PlanSolveAgentExecuteStrategy.java` | Plan-Solve 策略 |
| `src/main/java/org/wwz/ai/application/agent/execute/workflow/FlowAgentExecuteStrategy.java` | Workflow 策略 |
| `src/main/java/org/wwz/ai/application/agent/stream/AgentSessionStream.java` | 协议无关流端口 |
| `src/main/java/org/wwz/ai/application/agent/stream/AgentSessionPrinter.java` | `Printer` 适配器，封装 `AgentResponse` 协议消息 |
| `src/main/java/org/wwz/ai/application/agent/query/GptQueryApplicationService.java` | GPT 查询 seam |
| `src/main/java/org/wwz/ai/application/agent/dataquery/DataAgentApplicationService.java` | 数据问答 seam（替代已删除的 bridge） |
| `src/main/java/org/wwz/ai/application/agent/rag/RagApplicationService.java` | RAG 入库 seam |
| `src/main/java/org/wwz/ai/application/agent/role/FixRoleQueryApplicationService.java` | 角色库查询 seam |
| `src/main/java/org/wwz/ai/application/agent/armory/AgentArmoryApplicationService.java` | 装配实现 |
| `src/main/java/org/wwz/ai/application/agent/task/AgentTaskApplicationService.java` | 任务调度查询 |
| `src/main/java/org/wwz/ai/application/agent/visitor/AnonymousVisitorApplicationService.java` | 匿名访客身份解析 / 创建 |
| `src/main/java/org/wwz/ai/application/agent/visitor/ConversationSessionOwnershipApplicationService.java` | 会话归属应用服务（依赖 ledger port） |

---

## 变更记录 (Changelog)

### 2026-05-09
- 初始化模块文档；记录 019/020 后稳定 seam、协议端口与 visitor 子域归属。
