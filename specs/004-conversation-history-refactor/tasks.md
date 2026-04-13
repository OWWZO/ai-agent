# Tasks: 对话历史持久化精简重构

**Input**: Design documents from `/specs/004-conversation-history-refactor/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: 本特性明确要求保留后端回归验证、UI lint/build 校验和 quickstart 手工验收，因此任务中包含测试与验证项。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- 所有任务都显式写出真实文件路径，避免“修改后端逻辑”这类泛化描述

## Path Conventions

- **Backend contracts**: `ai-agent-station-study-trigger/src/main/java/...`
- **Backend domain logic**: `ai-agent-station-study-domain/src/main/java/...`
- **Application config / mapper / tests**: `ai-agent-station-study-app/src/...`
- **Frontend**: `ui/src/...`
- **Feature docs**: `specs/004-conversation-history-refactor/...`

## Phase 1: Setup (Shared Validation Assets)

**Purpose**: 为本次重构建立可复用的测试入口和事件样例，避免实现过程中失去验证抓手

- [X] T001 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java` 建立 turn/event 持久化回归测试入口
- [X] T002 [P] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java` 建立会话列表/详情契约回归测试入口
- [X] T003 [P] 在 `ai-agent-station-study-app/src/test/resources/data/conversation-history/plan-solve-events.json` 和 `ai-agent-station-study-app/src/test/resources/data/conversation-history/react-events-with-artifact.json` 准备 `PLAN_SOLVE/REACT` 历史回放夹具

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 完成三张表职责收敛、后端/前端基础契约统一，这些任务完成前不进入用户故事实现

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 在 `ai-agent-station-study-app/src/main/resources/db/schema.sql` 收敛 `ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event` 表结构，移除旧 rich replay 冗余字段并补齐必要索引/唯一约束
- [X] T005 [P] 在 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_conversation_mapper.xml`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_mapper.xml`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_event_mapper.xml` 同步字段映射、查询列和排序规则
- [X] T006 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentConversation.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentMessage.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentMessageEvent.java` 以及对应 `IAgentConversationDao.java`、`IAgentMessageDao.java`、`IAgentMessageEventDao.java` 收敛领域/DAO 字段模型
- [X] T007 [P] 在 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationDetailRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationListRespVO.java` 基础上新增 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationTurnRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationEventRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ArtifactReferenceRespVO.java`
- [X] T008 在 `ui/src/services/agentConversation.ts`、`ui/src/types/chat.ts`、`ui/src/types/message.ts` 定义摘要/turn/event/artifact 新契约，并清理对 `renderSnapshotJson`、`tasksJson`、`planJson` 等旧字段的类型依赖

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - 稳定回放历史对话 (Priority: P1) 🎯 MVP

**Goal**: 让 `PLAN_SOLVE` 和 `REACT` 历史在刷新后始终以事件流为唯一权威进行稳定回放

**Independent Test**: 发送一条 `PLAN_SOLVE` 和一条 `REACT` 请求，请求完成后刷新页面并重新打开历史会话，详情接口只返回 `turns[] + events[]`，前端按正确顺序展示关键节点与最终结论

### Tests for User Story 1

- [X] T009 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java` 覆盖 `PLAN_SOLVE/REACT` turn-event 落库、`response` 保留和旧 rich 字段不再写入的断言
- [X] T010 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java` 覆盖列表摘要返回、详情 `turns[] + events[]` 装配以及 `X-Device-Id` 归属校验

### Implementation for User Story 1

- [X] T011 [P] [US1] 重构 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java`，改为仅向 `AgentMessage` 写 request 账本字段并按顺序写入 `AgentMessageEvent`
- [X] T012 [P] [US1] 重构 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageServiceImpl.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageEventServiceImpl.java`，完成单轮 `response/metrics` 聚合与事件持久化
- [X] T013 [US1] 重构 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java`，让列表只返回摘要，让详情只从 `ai_agent_message_event` 装配 `turns[] + events[]`
- [X] T014 [US1] 修改 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationController.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationDetailRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationListRespVO.java` 以及新增 turn/event/artifact VO，输出新历史契约
- [X] T015 [P] [US1] 修改 `ui/src/services/agentConversation.ts` 和 `ui/src/utils/chatHistory.ts`，让历史详情读取直接消费 `turns[] + events[]` 而不是旧 `messages[] + rich JSON`
- [X] T016 [US1] 修改 `ui/src/utils/chat.ts`、`ui/src/components/ChatView/index.tsx`、`ui/src/components/ActionView/FilePreview.tsx`，按事件顺序和 `artifactRefs` 完成 `PLAN_SOLVE/REACT` 历史回放渲染
- [X] T017 [US1] 运行 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java` 并按照 `specs/004-conversation-history-refactor/quickstart.md` 完成一次 `PLAN_SOLVE/REACT` 刷新回放手工验证

**Checkpoint**: User Story 1 should now provide a stable, event-authoritative replay path for completed history

---

## Phase 4: User Story 2 - 简化前端历史状态 (Priority: P2)

**Goal**: 前端只把服务端会话摘要列表当成持久化真相源，本地只保留详情缓存和草稿/流式状态

**Independent Test**: 保留“新建草稿、切换历史会话、懒加载详情、删除当前会话、刷新页面”能力时，不再手工合并两套持久化会话元数据

### Tests for User Story 2

- [X] T018 [P] [US2] 使用 `ui/package.json` 中的 `lint` 和 `build` 脚本为 `ui/src/hooks/useAgentConversation.ts`、`ui/src/pages/Home/index.tsx`、`ui/src/components/ChatView/index.tsx` 的状态重构建立回归门槛

### Implementation for User Story 2

- [X] T019 [P] [US2] 重构 `ui/src/hooks/useAgentConversation.ts`，拆分服务端摘要列表、会话详情缓存和本地草稿/流式缓存三类状态
- [X] T020 [P] [US2] 修改 `ui/src/services/agentConversation.ts` 和 `ui/src/utils/chatHistory.ts`，移除 `remoteConversations` 与 `conversations` 的重复合并逻辑
- [X] T021 [US2] 修改 `ui/src/pages/Home/index.tsx` 和 `ui/src/components/ChatView/index.tsx`，让会话切换、新建和删除流程只依赖摘要列表与当前详情缓存
- [X] T022 [US2] 修改 `ui/src/types/chat.ts`、`ui/src/types/message.ts`、`ui/src/utils/chat.ts`，清理旧 rich 字段派生状态并保留草稿/流式运行态所需最小结构
- [X] T023 [US2] 执行 `ui/package.json` 中的 `lint` 和 `build`，并按 `specs/004-conversation-history-refactor/quickstart.md` 验证“新建草稿、切换会话、懒加载详情、删除当前会话、刷新页面”五类核心交互

**Checkpoint**: User Story 2 should leave the frontend with a clear split between persisted summaries, detail cache, and draft state

---

## Phase 5: User Story 3 - 优雅演进持久化模型 (Priority: P3)

**Goal**: 让新增展示节点优先通过事件 `payload + artifactRefs` 扩展，不再依赖新增表字段或工作区临时路径

**Independent Test**: 为新写入的 `REACT/PLAN_SOLVE` 事件返回结构化 `payload` 和稳定 `artifactRefs`；当引用失效时，前端仍能展示主时间线并明确提示缺失原因

### Tests for User Story 3

- [X] T024 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryArtifactTest.java` 覆盖 artifact 缺失、`payloadJson` 前向扩展和事件扩展示例的断言

### Implementation for User Story 3

- [X] T025 [P] [US3] 修改 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/PlanSolveAgentResponseHandler.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/ReactAgentResponseHandler.java`，统一生成 `payload.messageType + artifactRefs` 结构
- [X] T026 [P] [US3] 修改 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageEventServiceImpl.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java`，保证 `payloadJson` 可扩展且 artifact 缺失时返回 `missing/missingReason`
- [X] T027 [US3] 修改 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationEventRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ArtifactReferenceRespVO.java` 和 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationController.java`，把引用式内容和缺失态稳定暴露给前端
- [X] T028 [US3] 修改 `ui/src/components/ActionView/FileList.tsx`、`ui/src/components/ActionView/FilePreview.tsx`、`ui/src/utils/chat.ts`，基于 `downloadUrl/previewUrl/missing` 渲染预览、下载和缺失态
- [X] T029 [US3] 运行 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryArtifactTest.java` 并按 `specs/004-conversation-history-refactor/quickstart.md` 验证 artifact 可访问与缺失两条路径

**Checkpoint**: All user stories should now work independently and the history model should be extensible without new table columns

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 清理残余旧模型引用、同步文档并完成最终回归

- [X] T030 [P] 更新 `specs/004-conversation-history-refactor/quickstart.md`、`specs/004-conversation-history-refactor/contracts/conversation-history-api.md`、`specs/004-conversation-history-refactor/contracts/replay-event-payload.md`，使实现后的字段名、错误语义和验收步骤保持一致
- [X] T031 清理 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java`、`ui/src/utils/chat.ts`、`ui/src/hooks/useAgentConversation.ts` 中残留的旧 rich 字段分支并补中文注释
- [X] T032 [P] 执行 `mvn -pl ai-agent-station-study-domain,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests=false test`，修复 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryArtifactTest.java` 暴露的问题
- [X] T033 [P] 执行 `ui/package.json` 中的 `lint` 与 `build`，修复 `ui/src/pages/Home/index.tsx`、`ui/src/components/ChatView/index.tsx`、`ui/src/components/ActionView/FilePreview.tsx` 暴露的问题
- [X] T034 按 `specs/004-conversation-history-refactor/quickstart.md` 完成旧历史清理、新 schema 切换、列表/详情/artifact 缺失态的最终手工验收

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational completion - MVP path
- **User Story 2 (Phase 4)**: Depends on Foundational completion and should integrate US1 contract output
- **User Story 3 (Phase 5)**: Depends on Foundational completion and should align with US1 detail contract
- **Polish (Phase 6)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: No dependency on other stories after foundation; delivers the first usable increment
- **User Story 2 (P2)**: Reuses US1 的摘要/详情契约，但可以在 US1 主干稳定后独立完成前端状态收敛
- **User Story 3 (P3)**: Reuses US1 的 turn/event detail model and extends it with payload/artifact behavior

### Within Each User Story

- 测试与验证任务优先完成，至少先建立失败或缺口可见的回归入口
- 先完成模型与契约，再推进服务装配、控制器和 UI 渲染
- 每个故事在进入下一优先级前都应达到可独立演示和回归的状态

### Parallel Opportunities

- T002 and T003 can run in parallel after T001 starts the test baseline
- T005, T006, and T007 can run in parallel after T004 settles the schema direction
- T011, T012, and T015 can run in parallel once foundational contract/type work is finished
- T019 and T020 can run in parallel inside US2 because they operate on different state layers
- T025 and T026 can run in parallel inside US3 because handler mapping and persistence/detail resolution touch different files
- T032 and T033 can run in parallel during final regression

---

## Parallel Example: User Story 1

```bash
# Backend replay persistence path
Task: "T011 重构 ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java"
Task: "T012 重构 ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageServiceImpl.java 和 ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageEventServiceImpl.java"

# Frontend detail consumption path
Task: "T015 修改 ui/src/services/agentConversation.ts 和 ui/src/utils/chatHistory.ts"
```

---

## Parallel Example: User Story 2

```bash
# State split and API adapter can proceed together
Task: "T019 重构 ui/src/hooks/useAgentConversation.ts"
Task: "T020 修改 ui/src/services/agentConversation.ts 和 ui/src/utils/chatHistory.ts"
```

---

## Parallel Example: User Story 3

```bash
# Payload generation and payload consumption can proceed together
Task: "T025 修改 ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/PlanSolveAgentResponseHandler.java 和 ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/ReactAgentResponseHandler.java"
Task: "T028 修改 ui/src/components/ActionView/FileList.tsx、ui/src/components/ActionView/FilePreview.tsx、ui/src/utils/chat.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. Validate `PLAN_SOLVE/REACT` history replay independently
5. Stop for review before touching frontend state simplification or artifact extension

### Incremental Delivery

1. Finish Setup + Foundational and stabilize new schema/contract
2. Deliver User Story 1 as the first usable replay refactor
3. Deliver User Story 2 to simplify frontend state without changing the persisted truth source
4. Deliver User Story 3 to make payload/artifact expansion elegant and durable
5. Finish Polish phase and perform full regression plus rollout verification

### Parallel Team Strategy

1. One developer handles schema/mapper/entity foundation in Phase 2
2. One developer handles US1 backend replay persistence and detail assembly
3. One developer handles US2 frontend state split after contract freeze
4. One developer handles US3 artifact rendering after payload contract freeze

---

## Notes

- [P] tasks = different files, no unresolved dependency conflicts
- `US1` is the suggested MVP scope because it removes the largest replay correctness risk first
- 所有数据库、Mapper、控制器、VO、前端状态和渲染任务都已经绑定到明确文件路径
- 旧历史数据不做兼容读取；最终验收前需要完成清理和切换
- artifact 引用必须复用稳定资源地址，不允许回退到工作区临时路径
