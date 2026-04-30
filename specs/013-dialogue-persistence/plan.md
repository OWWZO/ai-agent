# Implementation Plan: 对话执行持久化账本

**Branch**: `[013-dialogue-persistence]` | **Date**: `2026-04-30` | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/013-dialogue-persistence/spec.md`

## Summary

为当前 Reactor Java 主链路补一套以 `run` 为聚合根的执行账本，在不改变现有 SSE 对外交互协议的前提下，记录单次执行的总账、每次 LLM 调用、每次工具调用以及输入/输出文件产物。技术上落在 `IAgentDispatchService -> React/PlanSolve ExecuteStrategy -> RootNode/Step1 -> AgentContext/BaseAgent/LLM` 这条真实执行链路，通过新增领域级 `AgentExecutionRecorder`、运行态 `AgentRunState`、4 张 MySQL 表和最小内部查询服务完成闭环；持久化采用同步实时“前插后更”，失败时 fail-open，并补齐日志和指标。

## Technical Context

**Language/Version**: Java 17（仅后端主链路；本期不改 `ui/`、`reactor-tool/`、`reactor-client/`）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis-Plus 3.5.14、MyBatis Mapper XML、MySQL 8、OkHttp SSE、现有 Reactor `AgentContext / BaseAgent / LLM / SSEPrinter / ToolArtifactRegistry` 运行时抽象  
**Storage**: MySQL（新增 `ai_agent_dialogue_run`、`ai_agent_llm_invocation`、`ai_agent_tool_invocation`、`ai_agent_artifact` 四张表）  
**Testing**: `mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=ToolArtifactBindingRuntimeTest,AgentExecutionLedgerRepositoryTest,ExecutionLedgerQueryServiceTest,ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest`  
**Target Platform**: Spring Boot HTTP/SSE 服务，内部 `/AutoAgent` 执行链路  
**Project Type**: Maven multi-module backend brownfield feature  
**Performance Goals**: 不做逐 chunk 落库；仅在 run/LLM/tool 生命周期关键节点写库；同一批 tool call 允许主线程顺序预登记、工作线程并发执行；账本写入失败不得直接阻断主对话结果  
**Constraints**: 严守 DDD 边界；首期仅覆盖 ReAct 与 PlanSolve；保留模型/工具原文用于内部排障；不新增正式查询 API；不做自动清理；不得重新依赖旧消息账本  
**Scale/Scope**: 主要影响 `ai-agent-station-study-domain` 的执行策略树、`AgentContext`、`BaseAgent`、`LLM`、新实体/Mapper/服务；`ai-agent-station-study-app` 的 `schema.sql`、Mapper XML 和回归测试；`trigger`、`ui`、`reactor-tool` 本期不改

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界：账本语义、运行态上下文和记录接口留在 `domain`，SQL/Mapper XML/装配留在 `app`，本期不把业务判断推到 `trigger`
- [x] 优先复用了现有 Agent、Tool、Prompt、DAO、配置装配能力：沿用 `IAgentDispatchService`、`AgentContext`、`BaseAgent`、`LLM`、`ToolArtifactRegistry`、`SSEPrinter` 和现有 MyBatis 组织方式
- [x] 已为关键改动点定义可执行验证方式：覆盖 DAO/Mapper、LLM/Tool 记录链路、ReAct/PlanSolve 集成链路和手工 SQL 冒烟
- [x] 已把工具并发、SSE 主流程 fail-open、文件去重、异常日志与指标纳入方案
- [x] 当前方案没有必须额外豁免的复杂度违例；复杂度来自新增账本模型，但比在 SSE 展示链或旧消息账本上反推事实更简单

## Project Structure

### Documentation (this feature)

```text
specs/013-dialogue-persistence/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── execution-ledger-query.md
│   └── execution-ledger-recorder.md
└── tasks.md
```

### Source Code (repository root)

```text
ai-agent-station-study-domain/
├── src/main/java/org/wwz/ai/domain/agent/service/dispatch/
├── src/main/java/org/wwz/ai/domain/agent/service/execute/react/
├── src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/
├── src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/
├── src/main/java/org/wwz/ai/domain/agent/reactor/agent/llm/
├── src/main/java/org/wwz/ai/domain/agent/reactor/agent/artifact/
├── src/main/java/org/wwz/ai/domain/agent/reactor/entity/
├── src/main/java/org/wwz/ai/domain/agent/reactor/mapper/
└── src/main/java/org/wwz/ai/domain/agent/reactor/service/
ai-agent-station-study-app/
├── src/main/resources/db/
├── src/main/resources/mybatis/mapper/
└── src/test/java/org/wwz/ai/test/domain/
ai-agent-station-study-trigger/
└── src/main/java/org/wwz/ai/trigger/http/
```

**Structure Decision**: 不新建并行子系统。执行账本的运行态入口挂在现有 `dispatch -> execute strategy -> root node -> AgentContext/BaseAgent/LLM` 链路，领域实体/Mapper 接口放在 `ai-agent-station-study-domain`，DDL 与 Mapper XML 放在 `ai-agent-station-study-app`。本期不在 `trigger` 暴露正式查询接口，内部查询能力通过领域服务与 DAO 直连验证。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 新增账本实体、DAO 接口、记录器、查询服务、运行态上下文，并在 ReAct/PlanSolve 执行链路、`AgentContext`、`BaseAgent`、`LLM` 上挂接写入点 |
| `ai-agent-station-study-app` | modify | 增加 4 张表到 `schema.sql`，新增 Mapper XML，补持久化与集成测试 |
| `ai-agent-station-study-trigger` | none | 本期不提供正式查询入口，`/AutoAgent` 入口保持现状 |
| `ai-agent-station-study-infrastructure` | none | 当前 Reactor DAO/实体已落在 `domain + app` 组合，不额外下沉到 infrastructure |
| `ui` | none | 没有用户可见执行账本界面变更 |
| `reactor-tool` / `reactor-client` | none | 不改 Python 子系统；工具产物仍通过现有稳定引用能力接入 |

## Layer Boundary Notes

- `domain`
  - 新增 `AgentRunState`、`AgentExecutionRecorder`、`ExecutionLedgerQueryService` 以及 run/llm/tool/artifact 领域实体
  - 在 `RootNode / Step1SopRecallAndPrepareNode` 创建 run 上下文，在 `LLM`、`BaseAgent.executeTools()`、最终 summary 节点收口账本
  - 继续把 `ToolArtifactRegistry` 作为工具产物事实源，不在 `Printer` 或 `ResponseHandler` 里反推工具产物
- `app`
  - 负责四张新表的 DDL、索引、Mapper XML、MyBatis 装配和测试夹具
  - 不承载 run 生命周期判断、LLM/tool 顺序语义或 fail-open 策略
- `trigger`
  - `AiAgentController`、`queryAgentStreamIncr`、`/AutoAgent` 对外行为保持不变
  - 本期不追加内部查询 HTTP 接口，避免把排障能力产品化
- 明确禁止
  - 在 `SSEPrinter`、`BaseAgentResponseHandler` 或前端消费层通过展示事件反推执行账本
  - 重新引入旧消息账本或异步补投影作为执行事实来源

## Data / Config / Contract Changes

- **Database**:
  - 新增 `ai_agent_dialogue_run`
  - 新增 `ai_agent_llm_invocation`
  - 新增 `ai_agent_tool_invocation`
  - 新增 `ai_agent_artifact`
  - 为 `request_id`、`run_id + invocation_seq`、`run_id + tool_call_id`、`llm_invocation_id + dispatch_index`、`run_id + artifact_role` 等查询路径加唯一键或二级索引
- **Config**:
  - 不新增用户可配功能开关
  - 仅新增内部指标名与日志埋点约定；长期保留策略由现有数据库运维能力兜底
- **Contract**:
  - 新增内部领域接口 `AgentExecutionRecorder`
  - 新增内部查询服务契约：按 `requestId` 查 run 明细、按 `toolName` 查近期调用、按 `sessionId` 查最近 run
  - `AgentContext` 新增运行态账本字段，但 `AgentRequest`、SSE 对外协议保持不变
- **Compatibility**:
  - 不改现有 `/AutoAgent` 与 `/web/api/v1/gpt/queryAgentStreamIncr` 对外协议
  - 首期仅为 ReAct/PlanSolve 写入执行账本；Workflow 继续沿用现状
  - 执行账本不回写旧消息账本，也不从旧消息账本恢复事实

## Verification Plan

- **Java**:
  - `mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=ToolArtifactBindingRuntimeTest,AgentExecutionLedgerRepositoryTest,ExecutionLedgerQueryServiceTest,ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest`
  - 需要新增重点测试：
    - `AgentExecutionLedgerRepositoryTest`：校验 4 张表写入、唯一键、批量查询与去重
    - `ExecutionLedgerQueryServiceTest`：校验 run/tool/session 三类内部查询
    - `ReactExecutionLedgerIntegrationTest`：校验 ReAct 链路的 run -> llm -> tool -> artifact 完整账本
    - `PlanSolveExecutionLedgerIntegrationTest`：校验 PlanSolve 多任务并发场景的 tool `dispatch_index` 与执行时间线
- **UI**: N/A
- **Python**: N/A
- **Manual**:
  - 走一条 ReAct 请求，确认 `/AutoAgent` 正常返回，且 MySQL 四张新表形成同一 `request_id` 的完整链路
  - 走一条 PlanSolve 多工具请求，确认同一次 `askTool()` 返回的多个 tool call 先按原顺序登记，再由并发线程分别更新结果
  - 构造账本写入异常，确认用户结果仍返回，同时日志和失败计数/成功率指标可见
  - 用 SQL 验证同一输出文件不会在同一 `run + tool_call_id + storage_key` 下重复登记

## Phase 0: Research Summary

- 真实主链路已确认：`AiAgentController#/AutoAgent -> IAgentDispatchService -> ReactAgentExecuteStrategy / PlanSolveAgentExecuteStrategy -> RootNode / Step1SopRecallAndPrepareNode -> AgentContext -> BaseAgent / LLM`
- `MultiAgentServiceImpl` 与 `BaseAgentResponseHandler` 只处理外层转发和展示结果，不适合作为执行事实源；账本应挂在实际执行路径，而不是 SSE 投影路径
- 现有可直接复用资产：
  - `AgentContext.requestId / sessionId / historyDialogue / sessionFiles`
  - `BaseAgent.executeTool()` 与 `executeTools()` 的工具调度边界
  - `LLM.ask()` 与 `askTool()` 的统一门面
  - `ToolArtifactRegistry` 的工具产物事实与可见文件去重能力
  - `ToolArtifactBindingRuntimeTest` 作为工具产物链路回归基线
- 关键研究结论已固化在 [research.md](./research.md)，没有遗留 `NEEDS CLARIFICATION`

## Phase 1: Design Decisions

### 1. Runtime Hook Placement

- 在 ReAct `RootNode` 与 PlanSolve `Step1SopRecallAndPrepareNode` 创建 run 上下文并登记输入文件
- 在 `LLM.ask()` / `LLM.askTool()` 前后记录 LLM invocation
- 在 `BaseAgent.executeTools()` 主线程预登记 tool invocations，并在 `executeTool()` 完成后更新结果
- 在 React `SummaryResultNode` 和 PlanSolve `Step2PlanExecuteNode` 的最终收口位置回写 run 汇总状态

### 2. Persistence Contract Split

- run、llm invocation、tool invocation、artifact 分成四张表
- `AgentExecutionRecorder` 只暴露领域化 start/finish/query 契约，不把 Mapper XML、SQL、主键生成细节泄漏到执行策略
- `AgentRunState` 作为 `AgentContext` 的运行态扩展，负责贯穿 `runId`、当前 agent 名称、LLM 序号和 `toolCallId -> toolInvocationId` 映射

### 3. Tool Ordering Strategy

- LLM 仍保留 run 级 `invocation_seq`
- tool 不再维护 run 级全局序号，改为 `llm_invocation_id + dispatch_index`
- 真实执行时间线由 `started_at + id` 还原，兼顾并发执行与模型原始顺序

### 4. Artifact Capture Strategy

- 输入文件在 run 创建后按 `artifact_role=input` 立即登记
- 输出文件在工具执行完成后，通过 `ToolArtifactRegistry` 按 `toolCallId` 收口并写 `artifact_role=output`
- 同一 `run + tool_call_id + storage_key` 下去重，避免重复登记稳定文件

### 5. Internal Query Strategy

- 本期不做 HTTP 查询接口
- 通过领域查询服务和 DAO 支持三类内部诉求：
  - 按 `requestId` 查询单次 run 全链路
  - 按 `toolName` 查询近期工具调用
  - 按 `sessionId` 查询会话最近 runs 与产物

## Phase 2: Implementation Strategy

### User Story 1 - 排查单次执行全链路

- 新增 run / llm invocation / tool invocation / artifact 领域实体、状态枚举、DAO 与 Mapper XML
- 在 ReAct/PlanSolve 根节点创建 `AgentRunState`，把 `runId`、`runUid`、输入文件事实放进 `AgentContext`
- 改造 `LLM` 与 `BaseAgent` 写入 LLM/tool 生命周期账本

### User Story 2 - 分析工具近期行为与稳定性

- 新增内部查询服务与查询 DTO，支持按 `toolName`、`requestId`、`sessionId` 查询
- 在 `BaseAgent.executeTools()` 保证 `dispatch_index` 与 `toolCallId` 映射稳定
- 为账本失败补日志和指标，形成可运维的工具治理信号

### User Story 3 - 追踪输入文件与工具产物归属

- 在 run 创建时把 `AgentRequest.sessionFiles` 转换为输入产物记录
- 在工具完成时把 `ToolArtifactRegistry` 中同一 `toolCallId` 的绑定写成输出产物
- 复用现有内部/可见文件区分，保留 `visibility` 与 `source_type/source_name` 语义

## Post-Design Constitution Check

- [x] DDD 边界仍然清晰：执行账本语义和生命周期留在 `domain`，SQL/Mapper 在 `app`
- [x] 继续复用了现有执行策略树、`AgentContext`、`LLM`、`BaseAgent`、`ToolArtifactRegistry`，没有平行造新运行框架
- [x] 所有关键设计点都绑定了可执行验证路径，包括 DAO、运行态、并发工具和手工 SQL 验证
- [x] fail-open、并发顺序、去重、日志与指标都纳入方案
- [x] 没有额外复杂度违例需要豁免

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | N/A | N/A |
