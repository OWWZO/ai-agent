# Agent Service Reactor DDD Convergence Remaining Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在已完成 `Trigger -> Case -> Domain` 第一阶段收敛的基础上，继续清理 `domain/agent/service` 与 `domain/agent/reactor` 历史残留，把 SSE / HTTP / JDBC / Spring 运行时耦合彻底迁出 `domain`，最终锁定真实可执行的 DDD 六边形边界。

**Architecture:** 剩余工作分为五段顺序推进：先清掉 `domain/service` 应用编排残留，再把 `reactor` 总包拆成 `runtime / ledger / memory / rag / role` 五个子域，然后切断 `SseEmitter` 协议泄漏，下沉 OkHttp / JDBC / Data Engine 等技术实现，最后删除兼容桥并补齐边界文档。整个过程保持“先层次、再包结构、最后技术实现”的收敛顺序，避免循环依赖和大爆炸改名。

**Tech Stack:** Java 17, Spring Boot 3.4.3, Maven multi-module, MyBatis / MyBatis-Plus, Spring AI 1.1.4, OkHttp, JUnit 4

**Execution Constraint:** 用户已明确要求“不用 TDD 测试驱动开发”。本计划按“先改边界与职责，再做聚焦回归验证”的方式执行，不采用“先写失败测试再写实现”的流程。

---

## 前置状态

- [x] `ai-agent-station-study-case` 已新增，并承接 `dispatch / execute / armory / task` 的第一批应用编排入口。
- [x] `AiAgentController`、`ReactorController`、`AgentTaskJob`、`AiAgentAutoConfiguration` 已切到 case 层接口。
- [x] `SseEmitterAgentSessionStream` 已建立，`Trigger` 可以通过适配器把 `SseEmitter` 包装为应用层流接口。
- [x] 已跑通第一阶段聚焦编译与回归：
  - `mvn compile -pl ai-agent-station-study-case,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests`
  - `mvn test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,AgentHandlerAutoConfigurationTest,SpringRuntimeBoundaryTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
- [ ] `domain/agent/service` 历史树仍保留兼容接口与实现。
- [ ] `domain/agent/reactor` 仍是 catch-all 总包，SSE / OkHttp / JDBC / Spring runtime lookup 仍残留在领域层。

## 输入文档

- `docs/superpowers/plans/2026-05-04-agent-service-reactor-ddd-convergence-plan.md`
- `docs/superpowers/plans/2026-05-04-reactor-ddd-phase2-domain-convergence-plan.md`

## 收敛边界

1. 依赖方向固定为 `Trigger -> Case -> Domain <- Infrastructure`。
2. `SseEmitter` 只允许停留在 `trigger` 适配器，不允许再进入 `case` 和 `domain`。
3. `domain` 只保留领域模型、领域服务、仓储接口、外部端口接口，不保留 `OkHttpClient`、`JdbcDataProvider`、`ApplicationContext.getBean(...)` 一类技术实现。
4. `infrastructure` 负责 HTTP / JDBC / MCP / 文件服务 / 数据查询执行器等技术细节。
5. 迁移过程中允许保留少量兼容桥，但必须显式标注为过渡代码，并在最后一阶段删除。

## 非目标

- 本计划不改数据库表结构。
- 本计划不主动改 `ui/`、`reactor-tool/`、`reactor-client/`，除非为了修复编译链路必须做最小适配。
- 本计划不顺手重命名现有领域模型语义名，优先先收敛包边界与职责边界。

## 文件责任图

- `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/**`
  - 只负责应用编排、策略选择、跨能力协作和运行链路组织。
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/**`
  - 只负责领域模型、领域规则、领域服务、port / repository contract。
- `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/agent/**`
  - 负责 Repository Adapter、HTTP Gateway、JDBC / SQL 执行器、MCP / Tool Runtime 技术实现。
- `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/**`
  - 负责 HTTP / SSE / Job / Listener 入口适配。
- `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/**`
  - 负责 Spring Bean 装配与运行时注册，不承载业务判断。

---

## Task 1: 清理 `domain/agent/service` 剩余兼容树

**Files:**
- Delete or Migrate:
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/IAgentDispatchService.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/IExecuteStrategy.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/IArmoryService.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/ITaskService.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/dispatch/**`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/**`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/task/**`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/**`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/**`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/workflow/**`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/auto/**`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/auto1/**`
- Modify:
  - `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java`
  - `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryIntegrationTest.java`
  - `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/FixedAgentExecuteStrategyTest.java`
  - `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/AiAgentAutoConfiguration.java`

- [ ] 盘点 `domain/agent/service` 下的残留类型，按“直接删除 / 迁移到 case / 下沉为领域运行时契约”三类分组，先不要跨任务顺手改 `reactor` 目录。
- [ ] 删除或迁移四个根接口 `IAgentDispatchService`、`IExecuteStrategy`、`IArmoryService`、`ITaskService`，确保主链路只依赖 case 层同名接口。
- [ ] 删除 `dispatch`、`armory`、`task` 三个历史实现目录，确认没有任何 Trigger / App / Test 还依赖这些旧实现。
- [ ] 对 `execute/auto/**` 与 `execute/auto1/**` 单独做一次引用分析：只保留主链路仍真实依赖的运行时拼装能力，其他无入口的兼容类直接删除；仍需保留的动态上下文字段把 `SseEmitter` 统一改成 `Printer` 或 case 层流抽象。
- [ ] 更新测试和装配，使应用链路只经由 `ai-agent-station-study-case` 暴露的接口工作。

Run:

```bash
mvn compile -pl ai-agent-station-study-case,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests
mvn test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,SpringRuntimeBoundaryTest" "-Dsurefire.failIfNoSpecifiedTests=false"
rg -n "org\\.wwz\\.ai\\.domain\\.agent\\.service\\.(IAgentDispatchService|IExecuteStrategy|IArmoryService|ITaskService)" ai-agent-station-study-case ai-agent-station-study-trigger ai-agent-station-study-app ai-agent-station-study-domain
```

Exit Criteria:

- `ai-agent-station-study-case` 成为 `dispatch / execute / armory / task` 的唯一应用编排归属。
- `domain/agent/service/dispatch`、`domain/agent/service/armory`、`domain/agent/service/task` 已删除。
- 代码主链路不再 import `domain.agent.service` 下四个根接口。

---

## Task 2: 将 `domain/agent/reactor` 拆成 `runtime / ledger / memory / rag / role`

**Files:**
- Create:
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/package-info.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/package-info.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/memory/package-info.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/rag/package-info.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/role/package-info.java`
- Move:
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/**`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/**`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/**`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/**`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/**`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/rag/**`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/role/**`

- [ ] 先创建五个子域目录和 `package-info.java`，把职责说明写清楚，避免后续文件迁移又回到“总包兜底”。
- [ ] 按五个批次迁移，不允许跨批次顺手混改：
  - 批次 1: `reactor/agent/**`、运行时实体、运行时工具链主循环迁到 `domain/agent/runtime/**`
  - 批次 2: `reactor/entity/**`、`reactor/model/ledger/**`、`reactor/service/replay/**` 迁到 `domain/agent/ledger/**`
  - 批次 3: `reactor/model/memory/**` 与 session memory 相关领域接口迁到 `domain/agent/memory/**`
  - 批次 4: `service/rag/**` 及与表召回、schema recall、SOP recall 直接相关的领域能力迁到 `domain/agent/rag/**`
  - 批次 5: `service/role/**` 与角色修复相关领域能力迁到 `domain/agent/role/**`
- [ ] 每个批次结束后只做 import 修复、包路径修复和最小 bean name 兼容，不做额外行为重构。
- [ ] 对确实无法同批次迁走的类型，允许留下桥接类，但文件顶部必须加中文注释说明“过渡桥接原因、依赖方、删除时机”。

Run:

```bash
mvn compile -pl ai-agent-station-study-domain -am -DskipTests
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/rag"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/role"
```

Exit Criteria:

- `domain/agent/runtime`、`ledger`、`memory`、`rag`、`role` 五个子域已成型。
- `domain/agent/service/rag` 与 `domain/agent/service/role` 已清空。
- `domain/agent/reactor/service` 不再作为总包继续承载新逻辑。

---

## Task 3: 切断 Domain 中的 SSE 协议泄漏

**Files:**
- Move or Refactor:
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/util/SseUtil.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/util/SseEmitterUTF8.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/printer/SSEPrinter.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IGptProcessService.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/GptProcessServiceImpl.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IMultiAgentService.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/MultiAgentServiceImpl.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/DataAgentService.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/Nl2SqlService.java`
- Modify:
  - `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/stream/AgentSessionPrinter.java`
  - `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/dataagent/DataAgentController.java`
  - `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/reactor/ReactorController.java`
  - `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/AiAgentController.java`

- [ ] 统一约束：领域层输出只能依赖 `Printer` 或其他领域打印契约；应用层输出只能依赖 `AgentSessionStream`；`SseEmitter` 生命周期统一交给 trigger。
- [ ] 将 `SseUtil`、`SseEmitterUTF8`、`SSEPrinter` 迁到 `trigger`、`app` 或 `infrastructure` 中最贴近协议的一侧；如果 `Printer` 必须保留在领域层，则只保留接口，不保留 `SseEmitter` 实现。
- [ ] 改造 `IGptProcessService`、`IMultiAgentService`、`DataAgentService`、`Nl2SqlService` 的方法签名，移除 `SseEmitter` 参数或返回值，改由 case / trigger 负责建立流并把打印桥接进去。
- [ ] 对数据问答、多代理入口、历史重放等仍直接返回 `SseEmitter` 的路径逐个收口，避免在领域服务内部自行 new emitter 或 complete emitter。
- [ ] 回看 `AgentContextConvergenceBoundaryTest`，把“domain 内不允许出现 `SseEmitter`”恢复为强约束，而不是只对部分目录检查。

Run:

```bash
rg -n "SseEmitter" ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent
mvn test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,SessionContextMemoryIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Exit Criteria:

- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/**` 中 `SseEmitter` 命中数降为 0。
- 领域服务不再返回或接收 `SseEmitter`。
- SSE 建立、心跳、关闭、错误收口全部留在 `trigger`。

---

## Task 4: 下沉 HTTP / JDBC / Data Engine / Tool Runtime 技术实现

**Files:**
- Create:
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/adapter/port/**`
  - `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/agent/adapter/port/**`
  - `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/agent/dataquery/**`
  - `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/agent/gateway/**`
- Move or Refactor:
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/llm/LLM.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/util/OkHttpUtil.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/*.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/data/jdbc/**`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/data/provider/jdbc/JdbcDataProvider.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/util/HttpUtils.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ChatModelInfoService.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ColumnValueSyncService.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/DataAgentService.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/Nl2SqlService.java`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/MultiAgentServiceImpl.java`
- Modify:
  - `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/**`

- [ ] 先按业务能力定义端口，不要按技术工具定义端口，优先收敛为“领域可读的能力接口”，例如：
  - `IDataQueryExecutionPort`
  - `IMultiAgentGateway`
  - `IFileArtifactPort`
  - `IMcpRuntimePort`
  - `IModelInvokeGateway`
- [ ] 把 `OkHttpClient` 构建、URL 拼接、Header 处理、超时、SSE 调用、文件上传、远程工具调用全部迁到 `infrastructure.agent.adapter.port` 或 `infrastructure.agent.gateway`。
- [ ] 把 `JdbcDataProvider`、连接管理、catalog / dialect / SQL 执行器迁到 `infrastructure.agent.dataquery`，领域层只保留请求模型、结果模型和查询语义约束。
- [ ] 把 `app` 中必须保留的 `@Configuration` / `@Bean` 装配集中管理，避免领域层再通过 `ApplicationContext.getBean(...)` 或静态 holder 获取运行时对象。
- [ ] 对 `LLM`、工具类、数据问答链路、列值同步链路逐个替换为端口依赖，避免出现“领域代码内部直接 new HTTP / JDBC 客户端”的情况。

Run:

```bash
rg -n "new OkHttpClient|JdbcDataProvider|ApplicationContext.getBean|SpringContextHolder" ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent
mvn compile -pl ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-app -am -DskipTests
mvn test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorPersistenceBoundaryTest,SpringRuntimeBoundaryTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Exit Criteria:

- `domain` 内不再出现 `new OkHttpClient`。
- `domain` 内不再直接引用 `JdbcDataProvider`。
- `domain` 内不再依赖 `ApplicationContext.getBean(...)` 或 `SpringContextHolder`。
- HTTP / JDBC / Data Engine / Tool Runtime 技术实现已明确落到 `infrastructure` 或 `app`。

---

## Task 5: 删除兼容桥并锁定最终边界

**Files:**
- Delete:
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/**`
  - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/**`
- Modify:
  - `CLAUDE.md`
  - `ai-agent-station-study-domain/CLAUDE.md`
  - `ai-agent-station-study-infrastructure/CLAUDE.md`
  - `ai-agent-station-study-trigger/CLAUDE.md`
  - `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java`
  - `docs/superpowers/plans/2026-05-04-agent-service-reactor-ddd-convergence-plan.md`

- [ ] 删除所有剩余兼容桥；如果有任何文件因为主链路风险暂时不能删，必须在本任务开始前先单独列清单，不允许“默认长期保留”。
- [ ] 扩充 `AgentContextConvergenceBoundaryTest`，至少覆盖四类最终约束：
  - 旧目录 `domain/agent/service` 不存在
  - 旧目录 `domain/agent/reactor` 不存在
  - `domain` 不出现 `SseEmitter`
  - `domain` 不出现 `new OkHttpClient` / `JdbcDataProvider`
- [ ] 更新根级和模块级 `CLAUDE.md`，把新的依赖方向、目录职责和技术边界写清楚。
- [ ] 回填主计划文档中已完成项与剩余项状态，避免后续继续按旧边界推进。
- [ ] 做一次最终目录扫描、全量编译和聚焦边界回归。

Run:

```bash
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor"
mvn compile -pl ai-agent-station-study-types,ai-agent-station-study-api,ai-agent-station-study-case,ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests
mvn test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,AgentHandlerAutoConfigurationTest,ReplayProjectorBeanTopologyTest,ReactorPersistenceBoundaryTest,SpringRuntimeBoundaryTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Exit Criteria:

- `domain/agent/service` 与 `domain/agent/reactor` 扫描无输出。
- 所有边界回归通过。
- 文档已和最终边界一致。

---

## 执行顺序

1. 先做 Task 1，切掉 `domain/service` 的应用编排残留。
2. 再做 Task 2，重组领域内部子域边界。
3. 第三步做 Task 3，把 SSE 协议污染赶出领域层。
4. 第四步做 Task 4，下沉远程调用、JDBC、Data Engine 与工具运行时。
5. 最后做 Task 5，删兼容桥、锁文档、跑最终边界回归。

这个顺序不能反过来。先删技术实现或先删 `reactor` 总包，会让 import 修复和 Bean 装配同时爆炸；必须先把层次和职责摆正，再做包迁移和技术下沉。

## 风险与守卫

### 1. 工作区已有在途改动

- 当前仓库是 dirty worktree。
- 任何迁移前先确认目标文件是否存在用户未提交改动。
- 如果同一文件存在用户在途改动，优先做兼容桥或最小迁移，不要强行覆盖。

### 2. 不要把“目录移动”当成收敛完成

只有以下条件同时成立，才算真正完成：

- `domain` 不再依赖 `SseEmitter`
- `domain` 不再依赖 `OkHttpClient` / JDBC 执行器 / Spring runtime lookup
- `case` 成为唯一应用编排入口
- `infrastructure` 成为唯一技术实现承接层
- 文档和边界回归都已经锁定

### 3. 兼容桥必须有删除时机

- 允许短期兼容桥，不允许无注释长期挂着。
- 每个兼容桥都必须写明依赖方、删除前提、预计删除任务。

## 完成定义

当以下条件全部满足时，本轮剩余收敛才算完成：

1. `ai-agent-station-study-domain` 中不再存在 `service` 与 `reactor` 两棵历史总树。
2. `domain` 中不再出现 `SseEmitter`、`new OkHttpClient`、`JdbcDataProvider`、`ApplicationContext.getBean(...)`、`SpringContextHolder`。
3. `case`、`domain`、`infrastructure`、`trigger`、`app` 的职责边界都能用目录结构和文档直接解释清楚。
4. 主链路聚焦回归与最终边界回归全部通过。
