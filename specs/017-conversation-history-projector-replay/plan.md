# Implementation Plan: Conversation History Projector Replay

**Branch**: `[017-conversation-history-projector-replay]` | **Date**: `2026-05-02` | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/017-conversation-history-projector-replay/spec.md`

## Summary

在现有 `run / llm / tool / artifact / tool_output_*` 执行账本之上新增 `ai_agent_dialogue_session` 会话主表，补齐“按 `sessionId` 恢复完整多轮历史”的查询与回放链路。后端以 `ExecutionLedgerQueryService -> ConversationHistoryReplayService -> ReplayProjector` 统一会话摘要、run 明细和 replay frames；前端继续复用现有 `combineData / handleTaskData` 渲染链，将历史 replay frames hydrate 回 `ConversationHistory`，保证刷新重进与实时结束态看到同一套细节结构。

## Technical Context

**Language/Version**: Java 17（后端主链路） + TypeScript 5 / React 19（`ui/`）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、MyBatis-Plus 3.5.14、MySQL 8、OkHttp SSE、React 19、Vite 6、Ant Design 5、现有 `ToolInvocationProjectorRegistry` 与 `combineData / handleTaskData` 前端恢复链  
**Storage**: MySQL（新增 `ai_agent_dialogue_session`，复用 `ai_agent_dialogue_run`、`ai_agent_llm_invocation`、`ai_agent_tool_invocation`、`ai_agent_artifact`、`ai_agent_tool_output_*`）  
**Testing**: `mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ExecutionLedgerQueryServiceTest,ReplayProjectorTest,ConversationHistoryControllerTest -Dsurefire.failIfNoSpecifiedTests=false`、`cd ui && npm run test -- conversationHistory.test.ts chat.test.ts`、`cd ui && npm run build`  
**Target Platform**: Spring Boot HTTP/SSE 服务 + Browser SPA  
**Project Type**: 以后端账本与查询为主、前端最小联动恢复的棕地全链路特性  
**Performance Goals**: 默认最近会话列表只查 20 条轻量摘要；会话详情按 `sessionId` 只加载该会话 runs 与明细；验收样本中 95% 会话在 3 秒内看到首个有意义历史内容  
**Constraints**: 严守 DDD 边界；不得重新接回旧 `ai_agent_message*` 主路径；历史与实时必须共用同一套语义映射；当前页面自动恢复只允许使用当前 `sessionId`；系统范围列表首版按“受控内部环境”假设开放，不在本期引入租户/归属隔离  
**Scale/Scope**: 影响 `ai-agent-station-study-domain`、`ai-agent-station-study-trigger`、`ai-agent-station-study-app`、`ui`；新增 1 张会话表、扩展历史查询服务、补 2 个会话历史接口、1 条前端 hydrate 链与对应测试

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界：会话聚合、回放投影与历史查询都留在 `domain`，`trigger` 只做 HTTP 契约输出，`app` 只承接 DDL / Mapper XML / 测试装配，`ui` 只消费契约并恢复展示态
- [x] 优先复用了现有 Agent、Tool、Prompt、RAG、DAO、配置装配能力：执行事实继续来自 `run / llm / tool / artifact / tool_output_*`，工具回放继续复用 `ToolInvocationProjectorRegistry`
- [x] 已为关键改动点定义可执行验证方式：后端账本查询、projector 同构测试、controller 回归、前端 hydrate 测试与构建都已纳入验证计划
- [x] 已将流式链路、失败态、缺失最终答案、引用失效与系统范围列表的安全边界纳入方案
- [x] 当前方案没有必须额外豁免的复杂度违例；复杂度来自新增会话主表与实时/历史语义收敛，但比继续堆积并行查询模型更简单

Phase 1 设计复核结果：仍然通过，无额外宪章例外。

## Project Structure

### Documentation (this feature)

```text
specs/017-conversation-history-projector-replay/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── conversation-history-api.md
│   └── replay-hydration-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
ai-agent-station-study-domain/
├── src/main/java/org/wwz/ai/domain/agent/reactor/entity/
├── src/main/java/org/wwz/ai/domain/agent/reactor/mapper/
├── src/main/java/org/wwz/ai/domain/agent/reactor/model/
├── src/main/java/org/wwz/ai/domain/agent/reactor/service/
├── src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/
└── src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/
ai-agent-station-study-trigger/
└── src/main/java/org/wwz/ai/trigger/http/agent/
ai-agent-station-study-app/
├── src/main/resources/db/
├── src/main/resources/mybatis/mapper/
└── src/test/java/org/wwz/ai/test/
ui/
├── src/pages/Home/
├── src/services/
├── src/types/
└── src/utils/
```

**Structure Decision**: 不新增并行历史子系统，而是在现有 `domain + trigger + app + ui` 链路内补齐会话头、查询聚合与前端恢复。执行事实源保持在既有账本；新增会话表只承接会话级摘要和排序信息；历史展示继续复用现有实时 UI 结构，不创建 history-only 页面模型。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 新增会话主表实体/DAO/读写模型，扩展会话级查询服务，统一历史与实时回放语义 |
| `ai-agent-station-study-trigger` | modify | 对外提供近期会话列表与会话详情接口，输出轻量摘要契约与完整重放契约 |
| `ai-agent-station-study-app` | modify | 增加 `ai_agent_dialogue_session` DDL、Mapper XML 和对应回归测试 |
| `ui` | modify | 增加历史接口类型、按当前 `sessionId` 自动恢复、replay frames hydrate 能力 |
| `ai-agent-station-study-infrastructure` | none | 继续沿用现有 MyBatis DAO 组织方式，不额外拆基础设施层实现 |
| `reactor-tool` / `reactor-client` | none | 本期不改 Python 子系统 |

## Layer Boundary Notes

- `domain`
  - 负责会话主表建模、run/llm/tool/artifact 明细聚合、历史回放事件投影、最终答案兜底与状态归一
  - `ReplayProjector` 作为实时与历史的共享语义入口，集中维护 `agent_name -> messageType` 规则
- `trigger`
  - 只负责参数校验、Response 包装与 VO 映射
  - 不允许在 Controller 内写历史补形、事件重排或状态推导逻辑
- `app`
  - 只负责 `schema.sql`、Mapper XML、Bean 装配与测试夹具
  - 不承载会话汇总统计规则和回放语义
- `ui`
  - 继续复用 `combineData / handleTaskData / FilePreview / Dialogue`
  - 只负责把后端 replay frames 还原成现有 `ConversationHistory`，不重新解释后端账本字段
- 特性边界
  - 自动恢复范围只限当前 `sessionId`
  - 系统范围近期会话列表仅供手动选择与调试扩展，不驱动自动切换

## Data / Config / Contract Changes

- **Database**:
  - 新增 `ai_agent_dialogue_session`
  - 扩展 `dialogue_run_ledger_mapper.xml`，补按 `sessionId` 顺序查询全部 runs
  - 复用 `ai_agent_dialogue_run`、`ai_agent_llm_invocation`、`ai_agent_tool_invocation`、`ai_agent_artifact` 和 `ai_agent_tool_output_*`
- **Config**:
  - 无新增应用配置
  - 系统范围列表的安全边界通过文档假设明确为“当前内部受控环境可见”，本期不新增 owner/device/tenant 配置
- **Contract**:
  - 新增 `GET /api/agent/conversation/sessions?limit=20`
  - 新增 `GET /api/agent/conversation/sessions/{sessionId}`
  - 会话摘要与会话详情契约显式分离；详情返回 replay frames，供前端直接 hydrate
- **Compatibility**:
  - 现有实时 SSE 契约保持不变
  - 当前前端空白初始态保持不变；若当前 `sessionId` 无历史，页面保留现状并允许用户手动选择会话
  - 本期不兼容旧 `ai_agent_message*` 历史账本进入新主路径

## Verification Plan

- **Java**:
  - `mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ExecutionLedgerQueryServiceTest,ReplayProjectorTest,ConversationHistoryControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
  - 如需补链路回归，再执行 `ReactExecutionLedgerIntegrationTest`、`PlanSolveExecutionLedgerIntegrationTest`
- **UI**:
  - `cd ui && npm run test -- conversationHistory.test.ts chat.test.ts`
  - `cd ui && npm run build`
- **Python**: N/A
- **Manual**:
  - 构造包含多轮、成功/失败/停止、带文件产物与缺少显式最终回答的会话
  - 刷新页面验证当前 `sessionId` 自动恢复
  - 列表验证默认 20 条、按最近活动倒序、只显示标题与最近查询预览
  - 详情验证 runs 顺序稳定、replay 细节与结束瞬间一致、引用失效有明确状态

## Phase 0: Research Summary

- 已确认当前事实源已经覆盖 run、LLM、tool、artifact 与 rich tool structured output，缺口是会话头、会话级查询与历史/实时语义收敛
- `ExecutionLedgerQueryService` 目前只支持单 run 明细、近期 tool 调用和单 session 的倒序 run 摘要，尚未提供：
  - 会话主表查询
  - 系统范围近期会话摘要列表
  - 会话详情按时间顺序恢复全部 runs
- `ReplayProjector` 目前只会投影 `toolInvocations + artifacts`，`BaseAgentResponseHandler` 仍单独 hardcode `eventData`
- 前端当前没有历史接口、没有历史详情类型，也没有 `replayFrames -> ConversationHistory` 的 hydrate 入口
- 研究结论已固化到 [research.md](./research.md)，没有遗留 `NEEDS CLARIFICATION`

## Phase 1: Design Decisions

### 1. 会话头与 run 的职责切分

- 新增 `ai_agent_dialogue_session`，一行代表一个会话
- `ai_agent_dialogue_run` 明确保持“会话中的一次请求”语义
- 会话头只保存低成本摘要与排序字段，不反向复制 LLM/tool 细节

### 2. 历史查询与回放聚合

- `ExecutionLedgerQueryService` 扩展为同时支持：
  - 查询单个会话头
  - 查询系统范围近期会话头
  - 查询会话下全部 runs（按时间升序）
  - 查询 run 明细
- `ConversationHistoryReplayService` 以 session 为外层聚合，再逐 run 构造 `ReplayFactBundle`

### 3. 实时与历史共用语义

- `ReplayProjector` 统一承接：
  - `agent_name -> tool_thought / plan_thought / result`
  - tool structured output + artifact 的历史投影
  - 缺失显式最终答案时使用 `run.finalSummaryText` 生成兜底结果事件
- `BaseAgentResponseHandler` 从业务语义 hardcode 切换为共享 projector 输出

### 4. 前端 hydrate 策略

- 后端详情接口返回 replay frames，shape 尽量贴近现有 SSE `eventData`
- 前端新增 `conversationHistory.ts`，循环调用 `combineData` 与 `buildTaskFromEventData`
- 历史恢复只覆写当前 `sessionId` 对应会话，不在失败时跳转到其他会话

### 5. 系统范围列表的安全边界

- 根据澄清结果，近期会话列表默认允许终端用户查看系统内所有近期会话
- 由于当前仓库无稳定 owner/tenant/device 会话归属模型，本期将此能力明确限定为“受控内部环境的系统范围可见”
- 若后续引入多租户或用户归属，优先在 `ai_agent_dialogue_session` 上补 owner 维度并下推到列表查询，而不是重写回放链路

## Phase 2: Implementation Strategy

### User Story 1 - 刷新后恢复当前会话

- 新增会话主表与写侧 upsert / finish 统计维护
- 扩展会话级查询服务，按 `sessionId` 顺序恢复所有 runs
- `ReplayProjector` 补 LLM 语义映射与最终答案 fallback

### User Story 2 - 历史与进行中保持同一套细节体验

- 让 `BaseAgentResponseHandler` 与历史回放共用 projector
- 细化 replay frames 合同，保证与现有 `combineData` 消费习惯一致
- 前端用 hydrate helper 恢复到 `ConversationHistory`

### User Story 3 - 会话摘要与会话详情保持一致

- 提供系统范围近期会话列表接口，默认 20 条、按最近活动倒序
- 会话头字段与详情聚合字段使用同一会话主表统计结果
- 对标题、最近查询预览、最终状态、最近活动时间与 run 统计做一致性回归

## Post-Design Constitution Check

- [x] DDD 边界仍然清晰：会话聚合、回放与状态推导都在 `domain`，`trigger` 只做契约暴露
- [x] 已优先复用既有账本、structured output、projector registry 与前端渲染链，而非新建并行事实源
- [x] 关键改动点均有自动化或手工验证路径
- [x] 已纳入失败/停止态、缺失最终答案、引用失效、系统范围可见的安全假设与前端恢复失败处理
- [x] 当前复杂度是必要的最小闭环，不需要额外豁免

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | N/A | N/A |
