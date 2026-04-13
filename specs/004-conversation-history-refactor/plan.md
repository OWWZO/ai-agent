# Implementation Plan: 对话历史持久化精简重构

**Branch**: `[004-conversation-history-refactor]` | **Date**: 2026-04-12 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-conversation-history-refactor/spec.md`

**Note**: 本文件只描述设计与实现规划，不代表代码已完成。

## Summary

本次重构把当前“会话摘要 + 单轮消息富字段 + 事件表 + render snapshot”多重并存的历史持久化模型，收敛为三层职责：

1. `ai_agent_conversation` 只承担历史列表和归属管理所需的轻量摘要。
2. `ai_agent_message` 退化为“单轮请求账本”，只保留 query、附件、状态、少量派生结果和上下文复用所需字段。
3. `ai_agent_message_event` 成为唯一历史回放权威源，承载按序事件、结构化 payload 和稳定 artifact 引用。

前端同步收敛为“服务端摘要列表 + 详情缓存 + 本地草稿/流式缓存”三块状态，不再手工合并两份持久化会话元数据。旧历史数据不做兼容，允许在切换前直接清理。

## Technical Context

**Language/Version**: Java 17（Spring Boot 多模块主链路）；TypeScript 5 + React 19（`ui/`）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、MySQL 8、React 19、Vite 6、Ant Design 5、现有 `reactor-tool` 文件上传能力  
**Storage**: MySQL（`ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`）+ 现有文件服务返回的稳定资源 URL / key  
**Testing**: Java 侧 Mapper/Service/Assembler 单测 + Maven 编译验证；UI 侧 lint/build；端到端手工验证历史回放和 artifact 缺失场景  
**Target Platform**: Spring Boot 服务 + 浏览器端 Vite SPA  
**Project Type**: 跨栈 feature（Java 后端 + React 前端）  
**Performance Goals**: 最近 50 条会话摘要查询需满足首屏可用目标（目标 1 秒内可开始操作）；详情仅在选中会话时懒加载；历史回放不引入额外的流式阻塞；大体量总结内容按引用按需加载  
**Constraints**: 不修改上游 SSE 协议；不保留旧历史数据兼容；必须保持 `CHAT` 上下文滑动窗口可用；事件扩展优先通过 `payload_json` 和 artifact 引用实现；会话查询必须统一设备/用户归属校验  
**Scale/Scope**: 影响 `ai-agent-station-study-domain`、`ai-agent-station-study-app`、`ai-agent-station-study-trigger`、`ui`；涉及 3 张历史表、Mapper XML、Controller/VO、前端 `agentConversation` 服务与 `Home/ChatView` 状态管理

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
specs/004-conversation-history-refactor/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── conversation-history-api.md
│   └── replay-event-payload.md
└── tasks.md
```

### Source Code (repository root)

```text
ai-agent-station-study-app/
├── src/main/resources/db/
└── src/main/resources/mybatis/mapper/
ai-agent-station-study-domain/
├── src/main/java/org/wwz/ai/domain/agent/reactor/entity/
├── src/main/java/org/wwz/ai/domain/agent/reactor/mapper/
└── src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/
ai-agent-station-study-trigger/
└── src/main/java/org/wwz/ai/trigger/http/agent/
ui/
├── src/services/
├── src/hooks/
├── src/pages/Home/
└── src/components/ChatView/
reactor-tool/
└── reactor_tool/api/file_manage.py
```

**Structure Decision**: 后端核心改动集中在 `domain + app + trigger` 三层：`domain` 负责 turn/event 聚合、回放装配和上下文复用规则，`app` 负责表结构与 Mapper XML，`trigger` 负责对外契约收敛；`ui` 只处理摘要列表、详情缓存和草稿缓存分层；`reactor-tool` 复用现有文件上传与稳定 URL 返回能力，不在本期新增 Python 侧接口。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 收敛 turn/event 持久化模型、移除 rich snapshot 写入、补充事件到详情的装配逻辑、保持 chat 上下文滑动窗口 |
| `ai-agent-station-study-app` | modify | 调整 `schema.sql`、`ai_agent_conversation/message/message_event` 对应 Mapper XML |
| `ai-agent-station-study-trigger` | modify | 收敛会话列表/详情接口、补齐设备归属校验、替换详情返回结构 |
| `ui` | modify | 拆分服务端摘要列表、本地草稿和详情缓存；移除对 `renderSnapshotJson/tasksJson` 的依赖 |
| `reactor-tool` | none | 复用现有文件服务上传与 `fileInfo` 返回格式即可满足稳定资源引用需求 |
| `ai-agent-station-study-infrastructure` | none | 本期不新增仓储实现或外部网关 |
| `specs/004-conversation-history-refactor` | modify | 补齐 planning 设计资产 |

## Layer Boundary Notes

- `domain` 负责定义新的 turn/event 读取与持久化边界、事件 payload 规范、artifact 引用抽取规则，以及 chat 上下文复用所需的派生字段规则。
- `app` 只负责 `schema.sql`、Mapper XML 和装配性配置，不承载历史装配业务逻辑。
- `trigger` 只负责 HTTP 入参/出参、设备/用户归属校验和 VO 映射，不直接拼接事件流业务语义。
- `ui` 负责把“摘要列表”“详情缓存”“本地草稿/流式态”拆开，避免在页面层重复实现持久化合并规则。
- 现有文件服务和 `fileInfo` 结构是稳定资源引用的首选承载方式，不新造 artifact 后端服务；如实现期发现稳定性不足，再作为单独复杂度升级记录。

## Data / Config / Contract Changes

- **Database**:
  - `ai_agent_conversation` 保留摘要字段：归属、标题、模式、角色快照、消息计数、预览、置顶和时间戳。
  - `ai_agent_message` 保留单轮请求账本字段：`conversation_id`、`request_id`、`sort_order`、`query`、`files_json`、`agent_type`、`response`（仅作为单轮最终回答/上下文文本）、`status`、`force_stop`、`metrics_json`、时间戳；移除 `thought/plan_json/tasks_json/multi_agent_json/conclusion_json/plan_list_json/render_snapshot_json` 等会话细节字段。
  - `ai_agent_message_event` 成为唯一回放源，保留 `message_id + seq_no` 有序事件关系、事件类型/区域/任务信息、`content_text`、`payload_json`、状态与时间戳；移除 `conversation_id/session_id/request_id` 等父级冗余标识。
  - 旧历史数据允许在切换前清理，不设计双读迁移。
- **Config**: 无新增业务配置；沿用现有文件上传和 `X-Device-Id` 透传机制。
- **Contract**:
  - 会话列表接口只返回摘要数据。
  - 会话详情接口由 `messages[]` 调整为 `turns[]`，每个 turn 下挂 `events[]`。
  - `ReplayEvent.payload` 统一承载结构化扩展数据与 `artifactRefs[]`。
  - `ui/src/services/agentConversation.ts` 与相关类型需要同步升级。
- **Compatibility**:
  - 不提供旧历史数据兼容读取。
  - 保持现有 SSE 实时链路不变，只调整完成态持久化和历史详情读取。
  - `CHAT` 实时交互不改，但单轮上下文文本仍需在 turn 级保留，避免破坏上下文窗口。

## Verification Plan

- **Java**:
  - `mvn -pl ai-agent-station-study-domain,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests=false test`
  - 至少补/跑 turn-event 持久化、详情装配、device scope 校验、旧字段移除后的上下文复用测试
- **UI**:
  - `cd ui && npm run lint`
  - `cd ui && npm run build`
- **Python**: N/A，本期复用现有文件服务与上传链路，不新增 Python 接口
- **Manual**:
  - 清理旧历史数据并执行新 schema
  - 新建一条 `PLAN_SOLVE` 消息，确认刷新后历史详情按 events 回放
  - 新建一条 `REACT` 消息，确认工具事件和 artifact 引用按需展示
  - 删除/失效一个被引用的资源，确认前端展示明确缺失状态
  - 验证历史列表、详情接口都按 `X-Device-Id` / 用户归属返回对应数据

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| 跨后端与前端同步改动 | 持久化模型和历史详情契约必须同时收敛，否则前端仍会依赖旧 rich 字段 | 只改后端或只改前端都会留下双模型并存，达不到“去重”和“单一真相源”目标 |
| 保留 turn 级 `response` 派生文本 | `CHAT` 上下文窗口与列表摘要仍需要单轮结果文本，不能完全依赖事件回放即时反查 | 完全移除 turn 级结果文本会迫使 chat 上下文每次从事件重建，影响现有窗口逻辑与实现复杂度 |
