# Implementation Plan: 工具输出独立表重构

**Branch**: `[014-tool-output-refactor]` | **Date**: `2026-05-01` | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/014-tool-output-refactor/spec.md`

## Summary

对当前 Reactor Java 主链路做一次纯重构：移除 `ai_agent_tool_invocation.output_json`，把 8 类 rich tool 的结构化结果改为 `ToolStructuredOutput` 强类型对象，经 `BaseAgent -> ToolInvocationFinishRecord -> ToolOutputWriter` 直接写入各自独立输出表；历史回放与执行详情查询只读新表，不保留 dual-read、JSON converter 或旧历史兼容分支。主账本仅保留调用事实、终态、`llmObservation` 和 `errorMsg`，`deep_search` 继续用 `query + answer_summary + stages_json` 维持阶段级回放，直接工具调用通过 `requestId + toolCallId` 在新表体系内检索。

## Technical Context

**Language/Version**: Java 17（仅后端主链路；本期不改 `ui/`、`reactor-tool/`、`reactor-client/`）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis-Plus 3.5.14、MyBatis Mapper XML、MySQL 8、FastJSON 1.2.83、现有 Reactor `AgentContext / BaseAgent / AgentExecutionRecorderImpl / ToolArtifactRegistry / ToolInvocationProjectorRegistry` 抽象  
**Storage**: MySQL（删除 `ai_agent_tool_invocation.output_json`，新增 8 张 `ai_agent_tool_output_*` 工具输出表；继续复用 `ai_agent_artifact`）  
**Testing**: `mvn clean compile -pl ai-agent-station-study-domain,ai-agent-station-study-infrastructure -am`；`mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=AgentExecutionLedgerRepositoryTest,ExecutionLedgerQueryServiceTest,ToolInvocationProjectorTest,ReplayProjectorTest,DeepSearchLlmObservationTest,MultiModalAgentToolTest,ImageGenerationToolTest,ScriptRunnerToolTest,ToolStructuredOutputWriterTest,ToolStructuredOutputReaderTest`  
**Target Platform**: Spring Boot 服务内部执行账本、历史回放与执行详情查询链路  
**Project Type**: Maven multi-module backend brownfield feature  
**Performance Goals**: 不增加逐 token 或逐阶段细粒度写库；每次 rich tool 只在终态落 1 次主账本更新 + 1 次输出表写入；projector 与详情查询按 `tool_invocation_id` 或 `requestId + toolCallId` 做 O(1) / 固定 8 表扇出读取  
**Constraints**: 严守 DDD 边界；不保留 `output_json`、不做 dual-read、不做 converter、不兼容旧历史；`deep_search` 只保存已实际完成阶段；重复终态写入采用首次写入生效、后续忽略并记录冲突；直接工具调用不能依赖主账本存在  
**Scale/Scope**: 主要影响 `ai-agent-station-study-domain` 的 tool payload、执行账本 finish 链路、详情查询与 9 个 projector，`ai-agent-station-study-infrastructure` 的输出表 PO/读写实现，`ai-agent-station-study-app` 的 `schema.sql`、Mapper XML 与回归测试；`trigger`、`ui`、`reactor-tool`、`reactor-client` 本期不改

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界：领域层定义强类型输出模型、写入命令、读取快照与 projector 组装语义；基础设施层承接输出表 PO 与读写实现；`app` 负责 DDL、Mapper XML 与测试装配
- [x] 优先复用了现有 Agent、Tool、执行账本、Artifact、MyBatis 组织方式：沿用 `BaseAgent`、`AgentExecutionRecorderImpl`、`ExecutionLedgerQueryServiceImpl`、`ToolArtifactRegistry`、现有 rich projector 注册链，不平行造新运行框架
- [x] 已为关键改动点定义可执行验证方式：覆盖 schema/Mapper、Writer/Reader、详情查询、projector 回放、rich tool 单测与重复终态写入冲突验证
- [x] 已把失败语义、重复终态写入、direct tool call 检索、文件引用回放与冲突日志纳入方案
- [x] 当前方案没有必须额外豁免的复杂度违例；新增复杂度集中在 8 张输出表与 typed output 模型，但比继续依赖账本 JSON 与兼容分支更简单

## Project Structure

### Documentation (this feature)

```text
specs/014-tool-output-refactor/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── structured-tool-output-persistence.md
│   └── structured-tool-output-query-and-replay.md
└── tasks.md
```

### Source Code (repository root)

```text
ai-agent-station-study-domain/
├── src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/
├── src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/
├── src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/
├── src/main/java/org/wwz/ai/domain/agent/reactor/entity/
├── src/main/java/org/wwz/ai/domain/agent/reactor/mapper/
├── src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/
├── src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/
└── src/main/java/org/wwz/ai/domain/agent/reactor/service/
ai-agent-station-study-infrastructure/
└── src/main/java/org/wwz/ai/infrastructure/
ai-agent-station-study-app/
├── src/main/resources/db/
├── src/main/resources/mybatis/mapper/
└── src/test/java/org/wwz/ai/test/domain/
```

**Structure Decision**: 保持现有 Reactor 执行链与账本入口不变，不新建并行子系统。`domain` 负责 typed output、finish record、query view 和 projector 语义；`infrastructure` 负责 8 张输出表的 PO 与读写实现；`app` 负责 DDL、Mapper XML 和测试夹具。现有工具调用主账本 DAO 组织方式保持一致，不把 JSON 解析逻辑继续留在 `projector` 或 `query service`。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 改造 `ToolResultPayload`、`BaseAgent`、`ToolInvocationFinishRecord`、`ToolInvocationView`、`ExecutionLedgerQueryServiceImpl`、`Default/DeepSearch/...Projector`，新增 typed output 模型和 `ToolOutputWriter/Reader` 契约 |
| `ai-agent-station-study-infrastructure` | modify | 新增 8 张输出表 PO、MyBatis 驱动的 `ToolOutputWriterImpl / ToolOutputReaderImpl`、重复终态写入冲突处理 |
| `ai-agent-station-study-app` | modify | 删除主账本 `output_json` 列，新增 8 张输出表及索引，补 Mapper XML、测试夹具和回归用例 |
| `ai-agent-station-study-trigger` | none | 不新增 HTTP / SSE 对外接口；继续复用现有执行详情与回放入口 |
| `ui` | none | 本期不改前端展示协议或交互 |
| `reactor-tool` / `reactor-client` | none | 不改 Python 子系统；rich tool 仍复用现有文件上传与返回结构 |

## Layer Boundary Notes

- `domain`
  - 定义 `ToolStructuredOutput` 及 8 个子类型、`ToolFileRef`、`DeepSearchStage` 等领域快照
  - `BaseAgent` 负责把工具返回值规范化为 `ToolExecutionOutcome(toolResult, llmObservation, structuredOutput, errorMsg)`
  - `ToolInvocationFinishRecord`、`ToolInvocationView`、`ExecutionRunDetail` 等查询/回放模型只暴露 typed output，不再暴露 `outputJson`
  - projector 只依赖 `ToolOutputReader` 与 `ArtifactView`，不再做 JSON 解析
- `infrastructure`
  - 负责 `ToolOutput*PO`、JSON 列序列化/反序列化、first-write-wins 冲突处理、direct lookup 扇出查询
  - 返回领域快照，不把 PO 暴露到 `domain`
- `app`
  - 负责 `schema.sql` 与 `mybatis/mapper/*.xml`
  - 补充 Writer/Reader、详情查询与 replay 回归测试
- 明确禁止
  - 在 `BaseAgent` 里继续拼装 `output_json`
  - 在 `ExecutionLedgerQueryServiceImpl` 或 projector 里继续读主账本 JSON
  - 为兼容旧历史增加 dual-read、迁移脚本或 converter

## Data / Config / Contract Changes

- **Database**:
  - 删除 `ai_agent_tool_invocation.output_json`
  - 新增 `ai_agent_tool_output_deep_search`
  - 新增 `ai_agent_tool_output_file_tool`
  - 新增 `ai_agent_tool_output_code_interpreter`
  - 新增 `ai_agent_tool_output_report_tool`
  - 新增 `ai_agent_tool_output_data_analysis`
  - 新增 `ai_agent_tool_output_multimodal_agent`
  - 新增 `ai_agent_tool_output_image_generation`
  - 新增 `ai_agent_tool_output_script_runner`
  - 8 张表统一保留 `tool_invocation_id / run_id / request_id / session_id / tool_call_id / status / error_msg / created_at / updated_at`
  - 统一索引：`uk_tool_invocation(tool_invocation_id)`、`uk_request_tool_call(request_id, tool_call_id)`、`idx_run_created(run_id, created_at DESC)`、`idx_status_created(status, created_at DESC)`
- **Config**:
  - 不新增用户可配开关
  - 复用现有 JSON 序列化能力；冲突日志沿用 `AgentExecutionRecorderImpl`/基础设施 logger
- **Contract**:
  - `ToolResultPayload` 删除 `outputJson`，新增 `ToolStructuredOutput structuredOutput` 与 `failed`
  - `ToolInvocationFinishRecord` 删除 `outputJson`，新增 `runId / sessionId / toolName / structuredOutput`
  - `ToolInvocationView` 删除 `outputJson`，新增 `ToolStructuredOutput structuredOutput`
  - 新增 `ToolOutputWriter` 与 `ToolOutputReader`
  - 详情查询改为返回主账本事实 + typed output；projector 改为消费 `ToolOutputReader`
- **Compatibility**:
  - 本次为主路径硬重构，不兼容旧 `output_json` 历史
  - 不保留 fallback 读取旧账本字段
  - direct tool call 检索只在新表内完成，不回查主账本

## Verification Plan

- **Java**:
  - `mvn clean compile -pl ai-agent-station-study-domain,ai-agent-station-study-infrastructure -am`
  - `mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=AgentExecutionLedgerRepositoryTest,ExecutionLedgerQueryServiceTest,ToolInvocationProjectorTest,ReplayProjectorTest`
  - `mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=DeepSearchLlmObservationTest,MultiModalAgentToolTest,ImageGenerationToolTest,ScriptRunnerToolTest`
  - `mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=ToolStructuredOutputWriterTest,ToolStructuredOutputReaderTest`
- **UI**: N/A
- **Python**: N/A
- **Manual**:
  - 触发一个 rich tool 成功场景，确认 `ai_agent_tool_invocation` 不再有 `output_json`，对应 `ai_agent_tool_output_*` 生成 1 条终态记录
  - 触发 `deep_search` 部分阶段失败场景，确认 `stages_json` 只保留已实际完成阶段，history replay 仍能恢复阶段事件
  - 触发无文件产出场景，确认 `file_refs_json` 归一化为空数组而不是歧义性 `null`
  - 模拟重复终态写入，确认首次写入保留、后续忽略且有冲突日志
  - 模拟 direct tool call，无主账本关联时仍可通过 `requestId + toolCallId` 从新表体系检索结果

## Phase 0: Research Summary

- 当前 `output_json` 真实贯穿链路已确认：`ToolResultPayload -> BaseAgent.normalizeToolResultPayload() -> ToolExecutionOutcome -> ToolInvocationFinishRecord -> AgentExecutionRecorderImpl -> IToolInvocationLedgerDao/Mapper XML -> ExecutionLedgerQueryServiceImpl -> ToolInvocationView -> Default/DeepSearch/...Projector`
- rich tool 当前原生结果字段已确认：
  - `deep_search`: `query + stages + answerSummary`
  - `file_tool`: `command + contentStorageMode + fileInfo`
  - `code_interpreter`: `codeOutput + content + code + explain + fileInfo`
  - `report_tool`: `fileType + summary + data + fileInfo`
  - `data_analysis`: `task + summary + data + fileInfo`
  - `multimodal_agent`: `summary + markdown + fileInfo`
  - `image_generation_tool`: `prompt + mode + summary + fileInfo`
  - `script_runner_tool`: `skillName + scriptName + runtime + success + exitCode + stdout + stderr + summary + fileInfo`
- 现有可直接复用资产：
  - `AgentExecutionRecorderImpl` 的 fail-open、日志与成功率统计框架
  - `ToolArtifactRegistry` 与 `ArtifactView` 的稳定文件链接事实源
  - rich projector 注册与 `ExecutionLedgerQueryServiceImpl` 组装链路
  - 现有 `schema.sql` / Mapper XML / DAO 组织方式
- 关键研究结论已固化在 [research.md](./research.md)，没有遗留 `NEEDS CLARIFICATION`

## Phase 1: Design Decisions

### 1. Main Ledger Shrinks to Facts Only

- `ai_agent_tool_invocation` 只保存调用身份、输入、`llmObservation`、终态状态、错误信息和时间线
- 主账本不再承载结构化业务结果；所有 rich output 都转入独立表

### 2. Typed Output Flows Through Runtime

- `ToolResultPayload` 改为 `toolResult + llmObservation + structuredOutput + failed + errorMsg`
- `BaseAgent` 统一归一化 `String`、`ToolResultPayload` 和异常场景，但不再构造 JSON wrapper
- `ToolInvocationFinishRecord` 带上 `toolName` 与 `structuredOutput`，供 `ToolOutputWriter` 直接落表

### 3. Eight Dedicated Output Tables with Shared Keys

- 每个 rich tool 一张表，公共列一致、业务列按 tool 子类型展开
- 输出表只保存终态快照，不再重复保存 `started_at/finished_at`；时间线仍由主账本提供
- `tool_invocation_id` 解决 agent 主链路查询；`request_id + tool_call_id` 解决 direct tool call

### 4. Replay and Detail Query Read Only New Tables

- `ToolInvocationView` 删除 `outputJson`，改为 `structuredOutput`
- `ExecutionLedgerQueryServiceImpl` 在组装 `ToolInvocationView` 时按 `toolName + toolInvocationId` 读取 typed output
- rich projector 注入 `ToolOutputReader`；default projector 只读 `llmObservation / errorMsg`

### 5. Edge Semantics Are Normalized in Reader/Writer

- `deep_search.stages_json` 只保存已实际完成且有内容的阶段
- 所有 `file_refs_json` 字段在“无文件”场景下归一化为 `[]`
- 重复终态写入采用 first-write-wins；writer 记录冲突并忽略覆盖
- 失败/超时也必须写终态输出行；当业务字段缺失时，`status + error_msg + llmObservation fallback` 共同保证可解释性

## Phase 2: Implementation Strategy

### User Story 1 - 独立记录工具输出

- 重写 `ToolResultPayload`、`BaseAgent.ToolExecutionOutcome`、`ToolInvocationFinishRecord`
- 新增 `ToolStructuredOutput`、`ToolFileRef` 和 8 个 tool 子类型
- 新增 8 张输出表、PO、Writer/Reader、Mapper XML
- 在 `AgentExecutionRecorderImpl.finishToolInvocation()` 中先更新主账本公共列，再调用 `ToolOutputWriter`

### User Story 2 - 仅基于新输出记录回放历史

- `ToolInvocationView` 删除 `outputJson`，改为 `structuredOutput`
- `ExecutionLedgerQueryServiceImpl` 负责详情查询 enrich
- 8 个 rich projector 统一通过 `ToolOutputReader` 读取 typed output
- `DeepSearchToolInvocationProjector` 基于 `stages_json` 反序列化结果投影阶段事件

### User Story 3 - 支持直接工具调用检索

- `ToolOutputReader` 提供 direct lookup：仅基于 `requestId + toolCallId` 在 8 张输出表内固定扇出查询
- 对于无主账本关联的行，允许 `tool_invocation_id / run_id / session_id` 为空，但 `request_id + tool_call_id` 必填
- 保证 direct lookup 返回唯一命中；若命中多表，视为数据冲突并记录错误

## Post-Design Constitution Check

- [x] DDD 边界仍然清晰：领域层定义 typed output 与消费契约，基础设施层承接输出表读写，`app` 落 DDL/Mapper/test
- [x] 继续复用了现有执行账本、Artifact、projector registry 与 MyBatis 组织方式，没有平行造第二套 replay/runtime
- [x] 所有关键设计点都绑定了可执行验证路径，包括 schema、writer/reader、projector、详情查询和 direct lookup
- [x] 重复写入、失败终态、无文件结果、部分阶段 deep search、冲突日志都纳入方案
- [x] 没有额外复杂度违例需要豁免

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | N/A | N/A |
