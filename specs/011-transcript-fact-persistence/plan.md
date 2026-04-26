# Implementation Plan: 后端事实对话账本重构

**Branch**: `[011-transcript-fact-persistence]` | **Date**: `2026-04-26` | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/011-transcript-fact-persistence/spec.md`

## Summary

把当前围绕“最终可见 UI 快照”落库的历史持久化，重构为围绕“后端事实账本”落库。核心做法是继续复用 `ai_agent_conversation / ai_agent_message / ai_agent_message_event` 三层结构，但重新定义职责：`ai_agent_message` 只保存单轮请求摘要与上传/生成文件摘要；`ai_agent_message_event` 改为保存有序语义事实块；历史详情与会话续聊都从这份事实账本恢复，再由后端投影成与实时对话一致的渲染契约，前端展示效果保持不变。

## Technical Context

**Language/Version**: Java 17（后端主链路） + TypeScript 5 / React 19（`ui/` 仅做最小适配）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、MySQL 8、React 19、Vite 6、Ant Design 5、现有 `combineData / handleTaskData / FilePreview` 渲染链  
**Storage**: MySQL（`ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`、`ai_agent_session_memory`）+ 现有稳定文件引用能力  
**Testing**: `ConversationHistoryPersistenceTest`、`ConversationHistoryDetailApiTest`、`ConversationHistoryArtifactTest`、`SessionTranscriptBlockAssemblerTest`、`SessionMemoryReopenResumeTest`、`AgentStreamPersistServiceSessionGuardTest`；`ui` 的 `npm run build` / `npm run lint`  
**Target Platform**: Spring Boot HTTP/SSE 服务 + Browser SPA  
**Project Type**: 以后端为主、前端最小联动的棕地增量改造  
**Performance Goals**: 历史详情和会话记忆恢复保持“消息批量查询 + 事件批量查询 + O(n) 装配”，不引入按事件逐条补查；历史列表仍只读取轻量摘要  
**Constraints**: 严守 DDD 分层；不新增独立文件表；旧历史数据不做兼容；大体量文件正文不再内联进事件载荷；`PLAN_SOLVE / REACT` 历史与续聊必须共用同一份事实账本  
**Scale/Scope**: 影响 `schema.sql`、message/event Mapper、流式持久化、历史详情装配、会话记忆装配、详情 VO 与 `ui/src/services/agentConversation.ts` 的历史恢复入口

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界：业务事实建模、历史投影和记忆恢复留在 `domain`，HTTP 映射留在 `trigger`，DDL/Mapper 留在 `app`
- [x] 优先复用了现有会话账本、事件 Mapper、文件恢复、历史详情、session memory 和前端统一渲染链，而不是新增并行子系统
- [x] 已为关键改动点定义可执行验证方式，包括 Java 定向回归、前端构建检查和手工“实时结束态 vs 历史重开态”对照
- [x] 已将流式持久化、文件引用失效、错误/强停终态、旧数据清理切换等异常路径纳入方案
- [x] 当前方案没有必须额外豁免的复杂度违例，无需填写 `Complexity Tracking`

Phase 1 设计复核结果：仍然通过，无额外宪章例外。

## Project Structure

### Documentation (this feature)

```text
specs/011-transcript-fact-persistence/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── conversation-history-api.md
│   ├── fact-event-storage.md
│   └── session-memory-rebuild.md
└── tasks.md
```

### Source Code (repository root)

```text
ai-agent-station-study-domain/
├── src/main/java/org/wwz/ai/domain/agent/reactor/entity/
├── src/main/java/org/wwz/ai/domain/agent/reactor/model/history/
├── src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/
├── src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/
└── src/main/java/org/wwz/ai/domain/agent/reactor/service/support/
ai-agent-station-study-trigger/
└── src/main/java/org/wwz/ai/trigger/http/agent/
ai-agent-station-study-app/
├── src/main/resources/db/
├── src/main/resources/mybatis/mapper/
└── src/test/java/org/wwz/ai/test/domain/
ui/
└── src/services/
```

**Structure Decision**: 本期不新增独立 history/file 子系统，而是在现有 `domain + trigger + app + ui` 链路内完成重构。历史详情继续经由现有 `conversation/detail -> restoreTurns -> ChatView/Dialogue/ActionView` 路径进入前端，但历史载荷改为由后端从事实账本投影生成。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 重构流式事件落库语义、生成文件摘要提取、事实块到历史详情的投影、会话记忆从事实账本恢复 |
| `ai-agent-station-study-trigger` | modify | 对外输出精简后的 turn/event 详情契约，保持前端消费方式稳定 |
| `ai-agent-station-study-app` | modify | 更新 `schema.sql`、`ai_agent_message_mapper.xml`、`ai_agent_message_event_mapper.xml` 与相关测试夹具 |
| `ui` | modify | 减少 history-only 兼容推导，优先消费后端直接投影好的 canonical payload |
| `ai-agent-station-study-infrastructure` | none | 本期不新增外部网关或独立仓储实现 |
| `reactor-tool` / `reactor-client` | none | 不修改 Python 子系统，只消费既有工具结果与稳定资源引用 |

## Layer Boundary Notes

- `domain`
  - 负责把流式响应整理为“后端事实块”并持久化。
  - 负责从 `message + event` 恢复 transcript/context blocks。
  - 负责把事实块投影成历史详情所需的 canonical payload。
- `trigger`
  - 只负责设备权限校验、VO 映射和详情接口输出。
  - 不在 Controller 中拼装历史 UI 语义或补做事件归一化。
- `app`
  - 负责 `schema.sql`、Mapper XML、测试数据和回归测试。
  - 不承载业务型事实映射判断。
- `ui`
  - 继续复用现有实时对话渲染链。
  - 对历史详情只做消费，不再承担“把数据库快照强行修成实时协议”的主逻辑。
- 明确禁止
  - 在 `trigger` 新增历史专用渲染规则
  - 在 `ui` 新增第二套 history-only 组件树或 history-only 状态机

## Data / Config / Contract Changes

- **Database**:
  - `ai_agent_conversation` 继续只承担会话摘要职责
  - `ai_agent_message` 保留 `files_json`（上传文件）并新增/保留 `generated_files_json`（本轮生成文件摘要）
  - `ai_agent_message_event` 不新增表，但语义从“最终可见 UI 快照”调整为“单轮有序事实块账本”
  - `payload_json` 只保存事实数据、资源引用和必要渲染元数据，不再长期保存可由稳定引用获取的大体量正文
- **Config**: 无新增运行时配置；继续复用现有稳定文件引用能力
- **Contract**:
  - 历史详情接口继续返回 `conversation + turns[] + events[]`
  - `turns[].events[].payload` 改为由后端投影生成的 canonical live-like payload，而不是数据库原始快照直出
  - `turns[].generatedFiles` 直接来自消息账本字段，不再从 `event.payload` 二次提取
- **Compatibility**:
  - 旧历史数据不兼容，允许上线前清理
  - `PLAN_SOLVE / REACT` 必须切换到新语义
  - `CHAT` 仅在共享抽象层面做必要对齐，不重做轻量模式

## Verification Plan

- **Java**:
  - `ConversationHistoryPersistenceTest`
  - `ConversationHistoryDetailApiTest`
  - `ConversationHistoryArtifactTest`
  - `SessionTranscriptBlockAssemblerTest`
  - `SessionMemoryReopenResumeTest`
  - `AgentStreamPersistServiceSessionGuardTest`
- **UI**:
  - `cd ui && npm run lint`
  - `cd ui && npm run build`
- **Python**: N/A
- **Manual**:
  - 完成一条 `PLAN_SOLVE` 会话，记录结束时左侧细节块和右侧文件入口，刷新后重开历史确认一致
  - 完成一条 `REACT` 会话并生成 HTML/Markdown/PPT 或文件，验证历史详情和生成文件列表都可查看
  - 在同一会话继续追问“基于刚才生成的文件继续”，确认 session memory 能感知上一轮事实账本
  - 构造错误/强停场景，确认历史详情保留已产生事实且终态正确

## Phase 0: Research Summary

- 已确认当前主要问题不是“没有持久化数据”，而是“当前持久化的数据形状偏向前端最终态快照，不适合作为长期真相源”
- 现有代码里已经具备三项可复用资产：
  - `generated_files_json` 的消息级摘要能力
  - `ConversationEventPayloadNormalizer` 的 artifact 引用归一化能力
  - `SessionTranscriptBlockAssembler / SessionWorkingMemoryAssembler` 的 transcript 恢复骨架
- 本期不需要新增独立文件表；更优解是把 `ai_agent_message_event` 收口为语义事实块，并让 `generated_files_json` 作为消息级派生摘要
- 研究结论已固化到 [research.md](./research.md)，没有遗留 `NEEDS CLARIFICATION`

## Phase 1: Design Decisions

### 1. Storage Responsibility Split

- `ai_agent_message`
  - 只保存单轮请求头、上传文件、本轮生成文件摘要、最终回答、状态、指标、时间信息
- `ai_agent_message_event`
  - 保存单轮内按顺序出现的事实块
  - 每条记录表达一个后端语义事实，而不是一个前端最终可见 UI 快照

### 2. Fact Block Strategy

- 事件表围绕事实块建模，默认块类型收敛为：
  - `assistant_thought`
  - `plan_snapshot`
  - `tool_use`
  - `tool_result`
  - `artifact_reference`
- `event_sub_type` 只承载来源细分，例如 `deep_search.search`、`deep_search.report`、`html`、`markdown`、`ppt`、`browser`
- `title` / `content_text` 保留为人类可读摘要和回退展示文案，不再作为主要真相源

### 3. Generated File Summary Strategy

- 本轮生成文件不再依赖从历史详情载荷中临时扫描
- 在持久化完成时，从事实块中的 `artifact_reference` / 结果型 payload 提取稳定文件摘要，写入 `ai_agent_message.generated_files_json`
- 上传文件与生成文件始终分开恢复、分开查询

### 4. History Projection Strategy

- 历史详情接口不再把数据库原始 `payload_json` 直接暴露给前端
- `ConversationReplayAssembler` 改为：
  - 读取消息账本与事实块账本
  - 按事实块类型投影生成 canonical live-like payload
  - 让前端继续复用实时对话的 `combineData / handleTaskData` 渲染链

### 5. Working Memory Strategy

- `SessionTranscriptBlockAssembler` 不再依赖“从 UI 快照 payload 猜测 transcript 语义”
- 改为直接读取消息账本字段和事实块账本字段恢复 `TranscriptContextBlock`
- `message.response` 继续承担最终回答来源；事件账本不重复内联最终回答全文

## Phase 2: Implementation Strategy

### User Story 1 - 重开历史时复现与实时一致的最终结果

- 重构 `AgentStreamPersistServiceImpl` 的事件累积逻辑，把当前 `projectFinalDetailEvents()` 改成“生成事实块集合”
- 改造 `ConversationReplayAssembler`，从事实块投影历史详情 payload
- 调整 `AgentConversationController` / VO 输出，保持前端消费结构稳定

### User Story 2 - 同会话续聊时恢复后端事实记忆

- 改造 `SessionTranscriptBlockAssembler`，直接消费事实块账本
- 让 `SessionWorkingMemoryAssembler` 与历史详情共享同一份事实语义
- 覆盖工具调用、工具结果、生成文件、引用失效等恢复场景测试

### User Story 3 - 开发者能按后端模型演进历史账本

- 收口 `ConversationEventPayloadNormalizer` 的职责：只做 artifact 归一化和重内容裁剪，不再暗含前端快照协议
- 确保新增一种历史块类型时，只需扩展事实块映射与历史投影规则
- 清理不再需要的 history-only 兼容代码和测试夹具

## Post-Design Constitution Check

- [x] DDD 边界仍然清晰，业务判断未下沉到 `trigger/app`
- [x] 继续复用了既有消息账本、事件账本、文件恢复、history replay 和 session memory 能力
- [x] 所有关键设计点都有自动化或手工验证路径
- [x] 文件引用失效、错误/强停、旧数据清理、历史/续聊共用账本等异常路径都有明确处理策略
- [x] 未引入额外复杂度违例，无需填写 `Complexity Tracking`

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | N/A | N/A |
