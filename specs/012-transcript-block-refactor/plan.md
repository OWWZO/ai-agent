# Implementation Plan: TranscriptBlock 会话记忆重写

**Branch**: `[012-transcript-block-refactor]` | **Date**: `2026-04-28` | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/012-transcript-block-refactor/spec.md`

## Summary

把当前围绕 `ai_agent_message / ai_agent_message_event / ai_agent_session_memory` 的混合账本、历史兼容投影和多层上下文拼装，重写为围绕 `ai_agent_turn + ai_agent_transcript_block + ai_agent_display_event + ai_agent_session_memory` 的单一事实模型。核心做法是：请求开始前同步判断是否压缩，压缩成功后写入新的多版本快照；流式结束时一次性把 turn、transcript blocks 和 display events 同事务落库；历史详情直接查询 display read model；旧表、旧代码和旧兼容链路在上线切换时直接删除。

## Technical Context

**Language/Version**: Java 17（后端主链路） + TypeScript 5 / React 19（`ui/` 历史消费链最小但明确的适配）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、MyBatis-Plus 3.5.14、MySQL 8、OkHttp SSE、React 19、Vite 6、Ant Design 5、现有 `ActionView / FilePreview / Dialogue` 组件链  
**Storage**: MySQL（新增 `ai_agent_turn`、`ai_agent_transcript_block`、`ai_agent_display_event`，重写 `ai_agent_session_memory`），外加现有稳定文件/产物引用能力  
**Testing**: `mvn test -pl ai-agent-station-study-domain -am -DskipTests=false`、`mvn test -pl ai-agent-station-study-app -DskipTests=false`、`cd ui && npm run build`、`cd ui && npm run lint`  
**Target Platform**: Spring Boot HTTP/SSE 服务 + Browser SPA  
**Project Type**: 以后端为主、前端联动的棕地全链路重写  
**Performance Goals**: 请求前记忆准备保持“最新快照 + 会话级批量 turn/block 查询 + O(n) 格式化”；历史详情保持“会话级批量 turn/display-event 查询 + O(n) 渲染映射”；display read model 与 transcript facts 同事务双写，不引入异步漂移  
**Constraints**: 严守 DDD 边界；不兼容旧表和旧代码；压缩失败必须直接跳过并继续当前请求；display history 必须来自同步双写读模型；只保留固定 block/display 类型集合；不改 `reactor-tool/` / `reactor-client/`  
**Scale/Scope**: 影响 `domain/reactor` 的 entity/mapper/service/support、`trigger/http/agent` 的会话详情接口、`app` 的 `schema.sql` 与 Mapper XML、`ui/src/services` 与历史渲染入口；涉及 4 张核心表、历史详情契约、会话记忆装配与压缩流程

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界：会话事实建模、上下文恢复、压缩和展示投影都留在 `domain`，`trigger` 只做接口映射，`app` 只承接 DDL/Mapper/配置
- [x] 优先复用了现有 Agent 请求入口、模式守卫、文件引用能力、ReactorConfig 和前端渲染组件链，而不是新增并行子系统
- [x] 已为关键改动点定义可执行验证方式，包括 Java 定向回归、前端 lint/build 和手工的续聊/历史重开/压缩路径验证
- [x] 已把流式异常、压缩失败、引用失效、模式冲突和旧链路删除后的切换风险纳入方案
- [x] 当前方案没有必须额外豁免的复杂度违例；复杂度来自硬切换重写本身，但这是用户明确要求，且比保留双轨兼容更简单

Phase 1 设计复核结果：仍然通过，无额外宪章例外。

## Project Structure

### Documentation (this feature)

```text
specs/012-transcript-block-refactor/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── conversation-history-api.md
│   ├── transcript-persistence.md
│   └── working-memory-compaction.md
└── tasks.md
```

### Source Code (repository root)

```text
ai-agent-station-study-domain/
├── src/main/java/org/wwz/ai/domain/agent/reactor/entity/
├── src/main/java/org/wwz/ai/domain/agent/reactor/mapper/
├── src/main/java/org/wwz/ai/domain/agent/reactor/model/
├── src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/
└── src/main/java/org/wwz/ai/domain/agent/reactor/service/support/
ai-agent-station-study-trigger/
└── src/main/java/org/wwz/ai/trigger/http/agent/
ai-agent-station-study-app/
├── src/main/resources/db/
├── src/main/resources/mybatis/mapper/
└── src/test/java/org/wwz/ai/test/
ui/
├── src/services/
├── src/components/
└── src/utils/
```

**Structure Decision**: 本期不新增独立基础设施子系统，而是在现有 `domain + trigger + app + ui` 链路内完成硬切换。新的持久化真相源放在 `turn / transcript_block / session_memory`，新的前端历史读模型放在 `display_event`，UI 删除 history-only 兼容恢复逻辑后直接消费后端读模型。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 新增 Turn / TranscriptBlock / DisplayEvent / SessionMemory 领域实体与 DAO，重写流式持久化、上下文恢复、压缩和展示查询服务 |
| `ai-agent-station-study-trigger` | modify | 输出新的会话详情读模型，删除旧 `ConversationTurnDetail / ConversationEventDetail` 兼容映射路径 |
| `ai-agent-station-study-app` | modify | 增加新表定义、替换旧 Mapper XML、补齐回归测试与测试数据 |
| `ui` | modify | 历史详情不再走 `restoreTurn -> combineData -> handleTaskData` 兼容链，改为直接消费 display read model |
| `ai-agent-station-study-infrastructure` | none | 本期仍沿用现有 DAO/Mapper 组织方式，不额外新增基础设施层实现 |
| `reactor-tool` / `reactor-client` | none | 不修改 Python 子系统，只消费既有工具结果和稳定文件引用 |

## Layer Boundary Notes

- `domain`
  - 持有 `TurnWriter`、`TranscriptBlockWriter`、`DisplayEventProjector`、`TranscriptContextBuilder`、`TranscriptPromptFormatter`、`DisplayHistoryQueryService`、`SessionMemoryCompactionService`
  - 负责会话事实写入、上下文恢复、压缩和读模型投影
- `trigger`
  - 只负责权限校验、请求参数解析、VO 映射和接口输出
  - 不再在 Controller 中做 payload 归一化、artifact 补形或历史兼容拼装
- `app`
  - 只负责 `schema.sql`、Mapper XML、配置装配和测试夹具
  - 不承载 block 类型判断、压缩决策或展示投影规则
- `ui`
  - 继续复用现有展示组件，但不再承担“把旧 history payload 修成实时协议”的职责
  - 实时 SSE 链保持现状，历史详情走新的 display read model
- 明确禁止
  - 保留 `ConversationReplayAssembler`、`ConversationEventPayloadNormalizer`、`SessionTranscriptBlockAssembler` 一类旧兼容链继续参与主路径
  - 在 `trigger` 或 `ui` 新增第二套历史专用状态恢复协议

## Data / Config / Contract Changes

- **Database**:
  - 新增 `ai_agent_turn`
  - 新增 `ai_agent_transcript_block`
  - 新增 `ai_agent_display_event`
  - 重写 `ai_agent_session_memory`：移除 `agent_type`，保留多版本快照语义，`artifact_refs_json` 收敛为快照级引用字段
  - 上线切换时删除 `ai_agent_message`、`ai_agent_message_event` 及其旧逻辑依赖
- **Config**:
  - 继续复用 `autobots.autoagent.session-memory.enabled`
  - 继续复用 `compaction-threshold-tokens`、`recent-window-turns`、`recent-window-max-tokens`、`recent-window-min-messages`、`summary-max-length`
  - 删除或停用 `hard-limit-tokens`、`max-consecutive-failures`、`circuit-open-seconds` 的主链路语义，因为压缩失败后不再拒绝请求
- **Contract**:
  - `GET /api/agent/conversation/detail` 改为返回 turn 元数据 + `displayEvents`
  - 历史详情不再暴露旧的 live-like payload 兼容格式，也不再要求 UI 执行 `combineData / handleTaskData`
  - `POST /api/agent/message/send-stream` 与 `POST /api/agent/message/stop` 对外路径不变，但内部持久化模型完全替换
- **Compatibility**:
  - 不做向后兼容
  - 旧历史数据、旧表结构、旧 Mapper、旧 history-only UI 修补逻辑全部删除
  - 切换后只服务新的事实模型和新的展示读模型

## Verification Plan

- **Java**:
  - `mvn test -pl ai-agent-station-study-domain -am -DskipTests=false`
  - `mvn test -pl ai-agent-station-study-app -DskipTests=false`
  - 新增并重点覆盖 `TranscriptBlockMapperTest`、`TranscriptContextBuilderTest`、`DisplayHistoryQueryServiceTest`、`SessionMemoryCompactionServiceTest`、`AgentStreamPersistCoordinatorRewriteTest`
- **UI**:
  - `cd ui && npm run lint`
  - `cd ui && npm run build`
- **Python**: N/A
- **Manual**:
  - 完成一条 `REACT` 会话，验证 `ai_agent_turn / ai_agent_transcript_block / ai_agent_display_event` 都有记录，且旧表不再写入
  - 在同一会话中继续追问，确认请求开始前先做压缩判断，且续聊能基于新事实链继续
  - 构造超过阈值的长会话并让压缩失败，确认当前请求继续执行且没有半成品快照写入
  - 重新打开历史会话，确认 UI 直接基于 display events 展示，不再依赖历史兼容补形

## Phase 0: Research Summary

- 已确认当前主问题是“事实写一套、历史读一套、压缩再拼一套”，而不是单纯的字段缺失
- 现有仓库里仍有可复用资产：
  - `AgentStreamPersistCoordinator` 作为统一请求入口
  - `BaseAgent` 的 `history_dialogue` 注入边界
  - `ReactorConfig` 的阈值与最近窗口配置
  - `ActionView / FilePreview / Dialogue` 的展示组件链
- 现有仓库里必须删除的遗留资产也已明确：
  - `AgentMessage / AgentMessageEvent / AgentSessionMemory` 旧实体与对应 Mapper
  - `ConversationReplayAssembler`
  - `ConversationEventPayloadNormalizer`
  - `SessionTranscriptBlockAssembler`
  - `SessionWorkingMemoryAssembler` 这类围绕旧账本和兼容 payload 的装配链
- 研究结论已固化到 [research.md](./research.md)，没有遗留 `NEEDS CLARIFICATION`

## Phase 1: Design Decisions

### 1. Persistence Model Split

- `ai_agent_turn`
  - 保存轮次顺序、请求标识、用户问题、状态和时间
- `ai_agent_transcript_block`
  - 保存固定 6 类 block 的事实语义
- `ai_agent_display_event`
  - 保存直接给 UI 查询的展示读模型
- `ai_agent_session_memory`
  - 保存带边界的多版本快照

### 2. Write Path Strategy

- 请求接收后先创建/更新 turn 占位记录
- 流式结束时把本轮 `AgentResponse` 归并为标准 `TranscriptBlock`
- 同一事务内批量写入 transcript blocks 并同步投影为 display events
- turn 终态、block 事实、display read model 只允许来自同一次落库，不接受异步补投影

### 3. Working Memory Strategy

- 请求开始前读取最新有效 snapshot
- 批量查询边界之后的已完成 turns 和 transcript blocks
- 用 `TranscriptContextBuilder` 构建 `WorkingContextWindow`
- 用 `TranscriptPromptFormatter` 直接生成 `historyDialogue`
- 同一份 working context 同时支撑结构化 messages、prompt text 和 session files

### 4. Compaction Strategy

- 压缩判断发生在每次请求真正执行前
- 超阈值时只压缩最近窗口之前的已完成 turns
- 压缩成功写入新的 snapshot version，并重新构建 working context
- 压缩失败时不写入半成品 snapshot，直接继续当前请求

### 5. History Read Strategy

- 历史详情不再读取 transcript blocks 后再在 Controller 或前端拼装旧 payload
- `DisplayHistoryQueryService` 直接按 `conversation -> turns -> displayEvents` 查询
- UI 基于 display event 类型直接渲染；历史详情不再复用 `restoreTurn -> combineData -> handleTaskData`

## Phase 2: Implementation Strategy

### User Story 1 - 同会话续聊沿用单一事实链

- 新建 turn/block/session-memory 领域模型与 DAO
- 重写 `AgentStreamPersistCoordinator` 的写入与请求前记忆准备流程
- 用新的 `TranscriptContextBuilder + TranscriptPromptFormatter` 代替旧 working-memory 装配链

### User Story 2 - 历史重开与实时续聊共享同一记忆来源

- 新建 `DisplayEvent` 读模型、投影器和查询服务
- 重构 `AgentConversationController` 及其返回 VO
- UI 直接消费 `displayEvents`，删除 history-only 兼容恢复逻辑

### User Story 3 - 长会话压缩后继续任务不丢状态

- 重写 `SessionMemoryCompactionService` 使其围绕 turn/block 事实工作
- 改为多版本 snapshot 写入与“最新有效版本”读取
- 移除压缩失败拒绝请求和熔断配置分支

## Post-Design Constitution Check

- [x] DDD 边界仍然清晰，事实建模/压缩/展示投影均留在 `domain`
- [x] 继续复用了现有请求入口、模式守卫、阈值配置和 UI 展示组件，而不是并行造新系统
- [x] 所有关键设计点都有自动化或手工验证路径
- [x] 压缩失败、引用失效、模式冲突、同步双写一致性和旧链路删除风险都纳入了方案
- [x] 复杂度来自硬切换本身，但这比继续维持兼容层更简单，不需要额外复杂度豁免

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | N/A | N/A |
