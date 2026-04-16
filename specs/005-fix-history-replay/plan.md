# Implementation Plan: 对话历史最终态重构与一致性修复

**Branch**: `[005-fix-history-replay]` | **Date**: 2026-04-16 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/005-fix-history-replay/spec.md`

## Summary

本次规划在 `004-conversation-history-refactor` 的三表模型基础上继续收敛：`ai_agent_conversation` 只保留列表摘要，`ai_agent_message` 只保留单轮最终答案/状态/指标，`ai_agent_message_event` 只保留历史详情真正需要的最终态细节项，并明确做到“一条最终可见细节对应一条事件记录”。

实现上不再把实时流式过程原样落库并在读取时二次拼装，而是在 `AgentStreamPersistServiceImpl` 流结束时把运行时事件投影为最终态细节集合，再统一写入 `ai_agent_message_event`。同时同步收敛三张表的字段和索引，删除不兼容旧数据，保持会话列表/详情接口与前端恢复链路的最小兼容改造，重点修复三类问题：同类最终细节被错误折叠、plan 完成态历史回退、历史工作区文件无法预览。

## Technical Context

**Language/Version**: Java 17（Spring Boot 多模块主链路）；TypeScript 5 + React 19（`ui/`）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、MySQL 8、React 19、Vite 6、Ant Design 5、现有文件服务/`artifactRefs` 归一化能力  
**Storage**: MySQL（`ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`）+ 稳定文件资源 URL / key  
**Testing**: `ConversationHistoryPersistenceTest`、`ConversationHistoryDetailApiTest`、`ConversationHistoryArtifactTest` + Maven 编译/回归；`ui` 的 `npm run build`/`npm run lint`；手工验证历史详情与文件预览  
**Target Platform**: Spring Boot 服务 + 浏览器端 Vite SPA  
**Project Type**: 跨栈 feature（Java 后端 + React 前端）  
**Performance Goals**: 最近 50 条会话摘要查询保持 1 秒内可开始操作；单个历史详情查询在 20 个 turn / 200 个最终细节项量级下保持 1 秒内完成组装；历史文件失效时单次点击即可返回明确不可用状态  
**Constraints**: 不修改上游 SSE 实时协议；不保留旧历史双读兼容；`ai_agent_message_event` 必须是最终态细节表且一条记录只表达一个最终可见细节；保持 `CHAT` 上下文窗口与深度研究现有正确行为；遵守 DDD 边界  
**Scale/Scope**: 影响 `ai-agent-station-study-domain`、`ai-agent-station-study-app`、`ai-agent-station-study-trigger`、`ui`；涉及 3 张历史表、3 个 Mapper XML、会话 Controller/VO、历史详情装配与前端 `restoreTurn`/文件预览链路

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更是否遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界？
- [x] 是否优先复用了现有 Agent、Tool、Prompt、RAG、DAO、配置装配能力？
- [x] 是否为每个关键改动点定义了可执行验证方式？
- [x] 是否将外部调用、流式链路、任务编排的异常与可观测性纳入方案？
- [x] 若提高了复杂度，是否在 `Complexity Tracking` 中给出合理说明？

## Project Structure

### Documentation (this feature)

```text
specs/005-fix-history-replay/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── conversation-history-api.md
│   └── final-detail-event-payload.md
└── tasks.md
```

### Source Code (repository root)

```text
ai-agent-station-study-app/
├── src/main/resources/db/schema.sql
└── src/main/resources/mybatis/mapper/
   ├── ai_agent_conversation_mapper.xml
   ├── ai_agent_message_mapper.xml
   └── ai_agent_message_event_mapper.xml
ai-agent-station-study-domain/
├── src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/
│   ├── AgentConversationServiceImpl.java
│   ├── AgentMessageEventServiceImpl.java
│   └── AgentStreamPersistServiceImpl.java
└── src/main/java/org/wwz/ai/domain/agent/reactor/service/support/
   ├── ConversationEventPayloadNormalizer.java
   └── ConversationReplayAssembler.java
ai-agent-station-study-trigger/
└── src/main/java/org/wwz/ai/trigger/http/agent/
   ├── AgentConversationController.java
   └── vo/
ui/
├── src/services/agentConversation.ts
├── src/utils/chatHistory.ts
├── src/hooks/useAgentConversation.ts
├── src/pages/Home/index.tsx
└── src/components/ActionView/FilePreview.tsx
```

**Structure Decision**: 继续沿用当前棕地项目的分层。`domain` 负责从流式运行态投影最终细节、装配 turn/event 历史详情以及 payload 归一化；`app` 负责三张表的 DDL 与 Mapper XML；`trigger` 负责稳定的列表/详情契约与兼容字段映射；`ui` 继续复用 `restoreTurn`/`combineData` 路径，只把输入从“过程事件”切到“最终细节事件”。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 将运行时过程投影为最终态细节、修改事件持久化语义、调整历史详情装配规则 |
| `ai-agent-station-study-app` | modify | 收敛 `schema.sql` 与 3 个 Mapper XML，删除冗余字段和重复索引 |
| `ai-agent-station-study-trigger` | modify | 收敛列表/详情 VO 与 Controller 映射，明确 `events[]` 只表示最终细节 |
| `ui` | modify | 调整历史详情类型、恢复逻辑与文件预览兼容桥接，确保 plan 完成态与 workspace 预览正确 |
| `ai-agent-station-study-infrastructure` | none | 当前历史 DAO/实体和装配链路实际在 `domain + app` 组合中完成，本期不单独扩展 infrastructure 模块 |
| `reactor-tool` | none | 继续复用现有文件服务产出稳定 URL / key，不新增 Python 侧接口 |

## Layer Boundary Notes

- `domain` 负责定义“什么是最终态细节”“哪些流式事件应被丢弃”“哪些最终细节应生成单条 event 记录”，并保证历史读取只组装最终态。
- `app` 只负责三张历史表的结构、索引和 Mapper XML，不承载最终态选择逻辑。
- `trigger` 只负责 HTTP 契约与兼容输出，不直接处理事件过滤、去重或最终态推理。
- `ui` 不重新实现后端聚合逻辑，只消费列表摘要和最终细节事件，并在必要处把 `artifactRefs` 兼容映射为现有 `fileInfo` 视图模型。
- 现有 `ConversationEventPayloadNormalizer` 继续作为 canonical payload 收口点，避免写入和读取侧再次维护两套 artifact 兜底规则。

## Data / Config / Contract Changes

- **Database**:
  - `ai_agent_conversation` 保留会话归属、标题、模式、角色快照、消息计数、预览、置顶和时间戳；不新增任何详情快照字段。
  - `ai_agent_message` 保留单轮请求账本字段：`conversation_id`、`request_id`、`sort_order`、`query`、`files_json`、`agent_type`、`response`、`metrics_json`、`status`、`force_stop`、时间戳和软删除；不承担最终细节明细。
  - `ai_agent_message_event` 收敛为最终态细节表：保留 `message_id`、`seq_no`、`event_type`、`event_sub_type`、`display_area`、`task_id`、`task_order`、`title`、`content_text`、`payload_json`、`status`、`create_time`、`deleted`；删除仅服务于过程回放的 `message_id_ext`、`is_final`、`started_at`、`ended_at`。
  - 索引同步收敛：删除 `ai_agent_message` 上与 `uk_conversation_sort` 重复的 `idx_conversation_sort`；删除 `ai_agent_message_event` 上与 `uk_message_seq` 重复的 `idx_message_id` 与当前读取链路未消费的 `idx_task_id`。
  - 旧历史数据在切换前直接清理，不设计双读迁移。
- **Config**: 无新增业务配置；继续沿用 `X-Device-Id`、现有文件服务 URL/key 和现有应用配置。
- **Contract**:
  - 列表接口继续返回轻量摘要。
  - 详情接口继续返回 `conversation + turns + events`，但 `turns[].events[]` 的语义改为“最终细节事件”，不再表示全过程回放事件。
  - `events[].isFinal` 可以作为兼容字段在响应层固定输出 `1`，但不再作为数据库持久化字段。
  - `payload_json` 以 canonical `artifactRefs[]` 为准；若前端旧组件仍依赖 `fileInfo`，则在服务端/前端兼容层派生，不再回写数据库。
- **Compatibility**:
  - 不兼容旧历史数据，切换前删除即可。
  - 不改变 SSE 在线协议、不改变列表/详情 URL。
  - `CHAT` 仍通过 `ai_agent_message.response` 保留上下文所需的单轮答案文本。

## Verification Plan

- **Java**:
  - `mvn -pl ai-agent-station-study-app -DskipTests=false -Dtest=ConversationHistoryPersistenceTest,ConversationHistoryDetailApiTest,ConversationHistoryArtifactTest test`
  - `mvn -pl ai-agent-station-study-domain,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests=false test`
- **UI**:
  - `cd ui && npm run lint`
  - `cd ui && npm run build`
- **Python**: N/A，本期不改 `reactor-tool`/`reactor-client`
- **Manual**:
  - 清理旧历史数据后执行新 `schema.sql`
  - 发送一条 `PLAN_SOLVE` 会话，确认结束时展示多个最终细节项；刷新后重新打开历史，确认数量与最终状态一致
  - 确认 plan 步骤在历史详情中仍显示完成态，不回退为初始计划组件
  - 发送一条包含工作区文件的会话，刷新后重新打开并点击文件，确认可预览；再模拟引用失效，确认显示明确缺失状态
  - 回归验证 `REACT` 历史详情，确认最终结果和细节未缺失

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| 维持 `turns[].events[]` 外部契约并在响应层保留兼容字段 | 可以让后端模型重构与前端改造分离，优先把最终态语义收敛正确 | 直接把对外契约一次性改成全新 `details[]` 会扩大 UI 改造范围，拉高 005 风险 |
| 保留 `ai_agent_message.response` | `CHAT` 上下文窗口和列表摘要仍需要单轮最终答案文本 | 若把最终答案也完全下沉到事件表，会迫使 chat 链路在每次请求前重建上下文，超出本期目标 |
