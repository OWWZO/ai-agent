# Tasks: 对话细节统一 UI 与最终态历史重构

**Input**: Design documents from `/specs/005-fix-history-replay/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: 本特性的规格、计划和 quickstart 已明确要求历史持久化、详情接口、artifact 缺失态和手工 1:1 回看验收，因此本任务清单包含针对性的后端回归任务与用户验收任务。  

**Organization**: 任务严格按用户故事组织，确保每个故事都能独立落地、独立验证，并优先围绕 MVP 的统一 UI 主链路推进。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（不同文件、无未完成前置依赖）
- **[Story]**: 对应用户故事标签（`[US1]`、`[US2]`、`[US3]`）
- 所有任务都显式列出真实文件路径

## Phase 1: Setup (Shared Fixtures & Acceptance Inputs)

**Purpose**: 固化统一 UI 方案所需的共享夹具和验收输入

- [ ] T001 [P] 更新 `ai-agent-station-study-app/src/test/resources/data/conversation-history/plan-solve-events.json` 和 `ai-agent-station-study-app/src/test/resources/data/conversation-history/react-events-with-artifact.json`，准备符合 canonical detail contract 的 `PLAN_SOLVE` / `REACT` 最终态事件夹具
- [ ] T002 [P] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryArtifactTest.java` 整理共享断言和夹具装配入口，作为后续故事回归基础

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 建立统一 detail contract、最终块快照存储和历史详情装配基础能力

**⚠️ CRITICAL**: 本阶段完成前，禁止开始任何用户故事任务

- [X] T003 调整 `ai-agent-station-study-app/src/main/resources/db/schema.sql`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentMessage.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentMessageEvent.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentMessageEventDao.java`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_mapper.xml`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_event_mapper.xml`，收敛 turn/event 的终态快照字段职责并支持 event 批量读取
- [ ] T004 [P] 调整 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/history/ConversationTurnDetail.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/history/ConversationEventDetail.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationDetailRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationTurnRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationEventRespVO.java`，统一历史详情输出模型与兼容字段
- [ ] T005 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageEventServiceImpl.java` 收口 canonical payload、`artifactRefs[]`、`presentation`、`messageIdExt/isFinal` 派生语义
- [X] T006 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationController.java` 建立按 turn 批量加载 event 并装配统一历史详情 contract 的主链路

**Checkpoint**: 统一 detail contract、终态 event 快照表和详情装配基础能力已完成，用户故事可以开始实现

---

## Phase 3: User Story 1 - 历史重开与进行中使用同一套细节界面 (Priority: P1) 🎯 MVP

**Goal**: `PLAN_SOLVE` 和 `REACT` 的历史详情进入前端后，必须复用与进行中对话相同的 `ChatView + Dialogue + ActionView` 处理与展示链路

**Independent Test**: 完成一条 `PLAN_SOLVE` 或 `REACT` 会话，记录结束时左侧细节块和主交互入口；刷新并重开历史后，仍通过同一套 UI 结构和同一处理路径恢复，不再出现 history-only 布局

### Tests for User Story 1

- [ ] T007 [P] [US1] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java`，覆盖“每个最终可见块独立持久化一条 event”“同一轮多条 `deep_search/search` 不合并”“最终顺序按 `seq_no` 恢复”的断言
- [ ] T008 [P] [US1] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java`，覆盖详情接口返回 live-compatible payload、兼容 `messageIdExt/isFinal` 字段且不再依赖历史专用摘要补丁的断言

### Implementation for User Story 1

- [X] T009 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 将 `plan_thought`、`plan`、`task`、`tool_thought`、`deep_search`、`task_summary` 等最终仍可见块投影为 one-block-per-event 的 canonical 快照
- [X] T010 [US1] 在 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationController.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationTurnRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationEventRespVO.java` 保持 `turns[].events[]` 外层结构稳定，同时输出可直接喂给 live 渲染链的 payload
- [X] T011 [US1] 在 `ui/src/services/agentConversation.ts` 和 `ui/src/utils/chatHistory.ts` 取消对历史专用 shape 修复的依赖，让历史 `payload` 直接进入 `restoreTurn -> combineData -> handleTaskData` 这条共享处理链
- [X] T012 [US1] 在 `ui/src/hooks/useAgentConversation.ts`、`ui/src/pages/Home/index.tsx`、`ui/src/components/ChatView/index.tsx` 统一 live 草稿会话与历史详情会话的详情入口，确保两者都进入同一个 `ChatView` 细节体验
- [X] T013 [US1] 在 `ui/src/utils/chat.ts` 和 `ui/src/components/Dialogue/index.tsx` 清理会破坏 canonical final blocks 的 history-only 去重/补丁逻辑，保证多条同类最终细节块按结束顺序显示
- [ ] T014 [US1] 按 `specs/005-fix-history-replay/quickstart.md` 的场景 A 和场景 D 执行手工验收，确认 `PLAN_SOLVE` / `REACT` 历史重开与结束时使用同一套细节界面

**Checkpoint**: US1 完成后，历史详情与进行中对话已经共用同一套细节 UI，是可交付的 MVP

---

## Phase 4: User Story 2 - 最终状态通过统一界面稳定复现 (Priority: P2)

**Goal**: 历史重开后必须稳定看到计划最终状态、多个同类最终块以及 `completed/error/force_stop` 的最后可见界面

**Independent Test**: 完成一条多步骤 `PLAN_SOLVE` 会话和一条异常/手动停止的结构化会话；刷新并重开历史后，计划完成态不回退，非成功终态仍显示最后可见块

### Tests for User Story 2

- [ ] T015 [US2] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java` 和 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java`，覆盖 `completed`、`error`、`force_stop` 三类终态都保留最后快照，以及计划最终完成态优先于初始计划片段的断言

### Implementation for User Story 2

- [ ] T016 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageServiceImpl.java` 收口 turn 终态与 final block status 的写入规则，确保 `completed/error/force_stop` 都能持久化最后可见界面
- [ ] T017 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/history/ConversationTurnDetail.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/history/ConversationEventDetail.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationController.java` 保留终态标识与计划最终状态，避免历史读取时回退为初始 plan 组件
- [X] T018 [US2] 在 `ui/src/services/agentConversation.ts`、`ui/src/components/Dialogue/index.tsx`、`ui/src/components/PlanView/PlanView.tsx`、`ui/src/components/PlanView/PlanItem.tsx` 按 final block status 渲染计划完成态、异常态和停止态，移除“历史统一退回初始计划视图”的行为
- [ ] T019 [US2] 按 `specs/005-fix-history-replay/quickstart.md` 的场景 B 和场景 E 执行手工验收，确认计划完成态与非成功终态都能稳定复现

**Checkpoint**: US2 完成后，历史详情可以稳定复现计划最终状态和各种终态的最后可见界面

---

## Phase 5: User Story 3 - 工作区与详情入口在统一界面中保持一致 (Priority: P3)

**Goal**: 历史中的工作区文件、HTML/Markdown 报告和其他产物继续通过与进行中相同的入口预览，并在资源失效时给出明确原因

**Independent Test**: 完成一条会生成工作区产物的结构化会话；重开历史后通过相同入口预览成功，若资源失效则看到显式 `missingReason`

### Tests for User Story 3

- [ ] T020 [US3] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryArtifactTest.java`，覆盖 canonical `artifactRefs[]`、`presentation`、资源缺失时仍保留 event 且返回 `missingReason` 的断言

### Implementation for User Story 3

- [ ] T021 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 统一 workspace 类最终块的 `artifactRefs[]` 与 `presentation` 持久化/回放语义
- [X] T022 [US3] 在 `ui/src/utils/historyArtifacts.ts` 和 `ui/src/services/agentConversation.ts` 只基于 canonical `artifactRefs[]` 恢复历史预览数据、下载地址和显式缺失态，不再依赖临时 `fileInfo/fileList` 作为数据库真相
- [X] T023 [US3] 在 `ui/src/components/ActionView/FilePreview.tsx`、`ui/src/components/ActionPanel/FileRenderer.tsx`、`ui/src/components/ActionPanel/HTMLRenderer.tsx`、`ui/src/components/ActionPanel/TableRenderer.tsx` 统一历史与进行中的文件/HTML/表格预览体验，并将资源失效提示替换为明确的缺失原因
- [X] T024 [US3] 在 `ui/src/components/ChatView/index.tsx` 和 `ui/src/components/Dialogue/index.tsx` 统一历史点击与进行中点击的工作区打开方式，确保同一时间线/工作区入口指向同一个 canonical artifact block
- [ ] T025 [US3] 按 `specs/005-fix-history-replay/quickstart.md` 的场景 C 和场景 F 执行手工验收，确认历史工作区入口与预览结果和进行中保持一致

**Checkpoint**: US3 完成后，历史工作区与进行中的入口和预览行为保持一致，资源缺失时也可解释

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 清理遗留分支、同步文档并完成全链路回归

- [ ] T026 在 `ui/src/services/agentConversation.ts`、`ui/src/utils/chatHistory.ts`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java` 清理残留 history-only fallback 分支并补齐必要中文注释
- [ ] T027 在 `specs/005-fix-history-replay/contracts/conversation-history-api.md`、`specs/005-fix-history-replay/contracts/final-detail-event-payload.md`、`specs/005-fix-history-replay/quickstart.md` 回填实现后的最终契约样例与验收说明，避免设计文档和代码漂移
- [ ] T028 按 `specs/005-fix-history-replay/quickstart.md` 完成场景 A-G 的最终手工回归，并使用 `ui/package.json` 中的 `lint/build` 命令校验前端构建链路

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: 无依赖，可立即开始
- **Phase 2: Foundational**: 依赖 Phase 1，且阻塞全部用户故事
- **Phase 3: US1**: 依赖 Phase 2；这是 MVP，建议最先落地
- **Phase 4: US2**: 依赖 Phase 2 和 US1 的 canonical event 主链路
- **Phase 5: US3**: 依赖 Phase 2 和 US1 的统一历史详情入口
- **Phase 6: Polish**: 依赖所有目标用户故事完成

### User Story Dependencies

- **US1 (P1)**: 基于 foundational 能力即可独立完成，是本次 MVP
- **US2 (P2)**: 复用 US1 的统一 detail contract 和 event 投影链路
- **US3 (P3)**: 复用 US1 的统一入口和 US2 的终态快照语义，但可以独立实现工作区预览链路

### Within Each User Story

- 回归任务先于实现任务，至少先把失败断言或夹具补齐
- 后端 canonical event 写入/读取先完成，再接前端统一恢复链路
- 历史 UI 统一优先，局部 polish 与 dead code 清理后置
- 每个故事完成后都要按 `quickstart.md` 的对应场景独立验收

---

## Parallel Opportunities

- `T001` 和 `T002` 可并行：分别准备 JSON 夹具和测试类基础结构
- `T004`、`T005`、`T006` 可在 `T003` 完成后并行：分别处理 VO/模型、payload normalizer、详情装配链路
- `T007` 和 `T008` 可并行：分别覆盖持久化与详情接口断言
- `T021`、`T022`、`T023` 在 canonical artifact contract 稳定后可拆给不同开发者并行推进

### Parallel Example: User Story 1

```bash
Task: T007 扩展 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java
Task: T008 扩展 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java
```

### Parallel Example: User Story 3

```bash
Task: T022 调整 ui/src/utils/historyArtifacts.ts 和 ui/src/services/agentConversation.ts
Task: T023 调整 ui/src/components/ActionView/FilePreview.tsx 和 ui/src/components/ActionPanel/*.tsx
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational
3. 完成 Phase 3: US1
4. 按 `quickstart.md` 场景 A/D 验证历史与进行中已共用同一套细节 UI
5. 通过后再继续 US2 和 US3

### Incremental Delivery

1. 先交付 US1，解决“历史与进行中两套 UI”的核心问题
2. 再交付 US2，解决计划完成态和非成功终态回看错乱
3. 最后交付 US3，解决工作区预览和缺失态提示
4. Phase 6 再统一清理遗留分支与文档漂移

### Parallel Team Strategy

1. 一人先完成 `T003-T006` 的 foundational 主链路
2. 一人推进 US1 的后端 event 投影与 API 契约
3. 一人推进 US1/US3 的前端统一入口与工作区预览
4. 每个故事结束后按 quickstart 独立验收，再合并进入下一阶段

---

## Notes

- 历史详情与进行中对话必须共享同一 canonical detail contract，不能再引入新的 history-only renderer
- `ai_agent_message_event` 必须保持“一条最终可见块一条记录”
- `payload.messageId` 是稳定 block identity，`seq_no` 是最终展示顺序，`status` 是终态
- `artifactRefs[]` 是唯一 canonical 文件引用表达，`fileInfo/fileList` 只能在兼容层派生
- 旧错误历史数据允许清空，不做双路径兼容
