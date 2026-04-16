# Implementation Plan: 对话细节统一 UI 与最终态历史重构

**Branch**: `[005-fix-history-replay]` | **Date**: 2026-04-16 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/005-fix-history-replay/spec.md`

## Summary

把 `PLAN_SOLVE` 与 `REACT` 的历史详情收敛到当前进行中对话使用的同一套 UI 基线。技术路径是：保持 `ai_agent_conversation` / `ai_agent_message` / `ai_agent_message_event` 三层结构，但将 `ai_agent_message_event` 明确定义为“最终可见细节块快照表”；在流结束时投影最终块并逐块持久化；历史详情接口继续返回 `turns[].events[]`，但每个 `event.payload` 必须直接对齐进行中链路消费的 canonical `MESSAGE.EventData` 语义，从而复用 `combineData`、`handleTaskData`、`ChatView`、`Dialogue`、`ActionView`、`FilePreview`，不再保留历史专用 UI 和历史专用推导分支。

## Technical Context

**Language/Version**: Java 17（Spring Boot 多模块主链路） + TypeScript 5 / React 19（`ui/`）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis DAO + Mapper XML、MySQL 8、React 19、Vite 6、Ant Design 5、ahooks、现有 `ActionView/FilePreview/Dialogue` 组件链  
**Storage**: MySQL（`ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`）+ 文件服务稳定 `artifactRefs` 引用  
**Testing**: `ConversationHistoryPersistenceTest`、`ConversationHistoryDetailApiTest`、`ConversationHistoryArtifactTest`；`ui` 的 `npm run lint` / `npm run build`；手工对照“结束时进行中界面 vs 历史重开界面”  
**Target Platform**: Spring Boot HTTP API + Browser SPA  
**Project Type**: 跨后端与前端的棕地增量改造  
**Performance Goals**: 历史详情接口对典型结构化会话保持“message 账本查询 + event 批量查询 + O(n) 装配”，不再在读取期做二次推导；前端历史重开除详情接口外不新增块级补拉请求  
**Constraints**: 严守 DDD 分层；不新增第三套历史专用 UI；历史与进行中必须共享同一 canonical detail contract 和同一前端处理路径；`CHAT` 保持轻量模式；旧错误历史数据允许清理，不做双路径兼容  
**Scale/Scope**: 影响 `schema.sql`、message/event Mapper、持久化服务、历史详情装配、详情 VO/Controller、`ui` 的历史详情恢复入口与现有进行中渲染链；范围覆盖 `PLAN_SOLVE` 和 `REACT`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界；业务投影与装配留在 `domain`，VO 映射留在 `trigger`，表结构与 Mapper 留在 `app`
- [x] 优先复用了现有 Agent、Tool、Prompt、DAO、payload normalizer、`combineData/handleTaskData`、`ChatView/Dialogue/ActionView` 能力
- [x] 已为关键改动点定义可执行验证方式，包括 Java 定向测试、UI 构建检查和手工 1:1 对照验收
- [x] 已将流式链路、历史详情装配、artifact 缺失态和终态异常语义纳入方案
- [x] 本方案不引入超出需求的新复杂度，无需填写 `Complexity Tracking`

Phase 1 设计复核结果：仍然通过，无额外宪章例外。

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
ai-agent-station-study-domain/
├── src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/
├── src/main/java/org/wwz/ai/domain/agent/reactor/service/support/
├── src/main/java/org/wwz/ai/domain/agent/reactor/entity/
└── src/main/java/org/wwz/ai/domain/agent/reactor/model/history/
ai-agent-station-study-trigger/
└── src/main/java/org/wwz/ai/trigger/http/agent/
ai-agent-station-study-app/
├── src/main/resources/db/
├── src/main/resources/mybatis/mapper/
└── src/test/java/org/wwz/ai/test/domain/
ui/
├── src/services/
├── src/utils/
├── src/hooks/
└── src/components/
```

**Structure Decision**: 只在现有 `domain + trigger + app + ui` 链路内增量修改，不新增并行 history-only 子系统。历史详情继续经由现有 `conversation/detail -> buildConversationFromDetail -> restoreTurn -> ChatView/Dialogue/ActionView` 路径进入 UI。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 调整终态快照投影、事件批量读取、历史详情装配、终态语义收口 |
| `ai-agent-station-study-trigger` | modify | 对外输出统一 detail contract，兼容 `messageIdExt/isFinal`，保持详情接口稳定 |
| `ai-agent-station-study-app` | modify | 更新 `schema.sql`、Mapper XML 和领域测试夹具 |
| `ui` | modify | 让历史详情直接复用进行中处理路径，削减 history-only 形状修复，统一工作区预览行为 |
| `ai-agent-station-study-infrastructure` | none | 本次无需新增外部网关或新的持久化实现层 |
| `reactor-tool` | none | 不涉及 Python 工具和 MCP 服务 |

## Layer Boundary Notes

- `domain`
  - 负责把流式结果投影为最终可见细节块。
  - 负责历史详情按 turn/event 装配 canonical detail contract。
  - 负责把 `completed/error/force_stop` 统一映射为可回放的终态语义。
- `trigger`
  - 只负责 HTTP 入参、作用域校验委托、VO 映射和兼容字段输出。
  - 不在 Controller 中拼装历史细节或补 artifact 兼容字段。
- `app`
  - 负责 `schema.sql`、Mapper XML、测试资源和回归测试。
- `ui`
  - 继续把 detail API 转成 `CHAT.ConversationHistory -> CHAT.ChatItem`。
  - 优先删除历史专用语义修复，让历史 payload 直接进入现有进行中处理链。
- 明确禁止
  - 在 `trigger` 新增“历史专用 UI 组装逻辑”
  - 在 `ui` 新增与 `ChatView` 并行的第二套结构化历史组件树

## Data / Config / Contract Changes

- **Database**:
  - `ai_agent_conversation` 保持会话摘要职责
  - `ai_agent_message` 保持单轮账本职责
  - `ai_agent_message_event` 收敛为“最终可见细节块快照表”
  - 删除或停止使用只服务于实时回放的字段，例如 `message_id_ext`、`is_final`、`started_at`、`ended_at`
  - 明确 `seq_no` 为最终展示顺序，`payload_json.messageId` 为稳定 block identity，`status` 为终态
  - 增加按 messageId 集合批量读取 event 的 Mapper 能力，避免详情接口 N+1
- **Config**: 无新增运行时配置；继续复用现有文件服务稳定引用能力
- **Contract**:
  - 详情接口保持 `conversation + turns[] + events[]`
  - `events[].payload` 必须直接兼容前端进行中使用的 `MESSAGE.EventData` 语义
  - `messageIdExt`、`isFinal` 仅作为兼容输出，由 payload 派生
  - `artifactRefs[]` 成为历史与工作区预览的 canonical 引用表达
- **Compatibility**:
  - 旧错误历史数据允许清空，不做兼容
  - `PLAN_SOLVE` 与 `REACT` 迁移到统一 detail contract
  - 普通 `CHAT` 保持轻量历史，不强制拥有复杂 `events[]`

## Verification Plan

- **Java**:
  - `ConversationHistoryPersistenceTest`
  - `ConversationHistoryDetailApiTest`
  - `ConversationHistoryArtifactTest`
  - 如实现中新增 `force_stop` / 批量加载 / 多搜索块顺序用例，同步补对应测试
- **UI**:
  - `cd ui && npm run lint`
  - `cd ui && npm run build`
- **Python**: N/A
- **Manual**:
  - `PLAN_SOLVE` 完成后重开历史，对照左侧时间线和右侧工作区是否与结束时 1:1 一致
  - `REACT` 完成后重开历史，确认多条 `deep_search`、总结块、工作区结果仍在
  - `error` / `force_stop` 终态重开历史，确认最后可见块仍存在且终态正确
  - 点击历史工作区文件，确认能正常预览或显示明确缺失原因

## Complexity Tracking

无宪章违规项，本节留空。
