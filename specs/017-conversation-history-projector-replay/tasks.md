# Tasks: Conversation History Projector Replay

**Input**: Design documents from `/specs/017-conversation-history-projector-replay/`  
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: 本特性在 [spec.md](./spec.md) 的用户故事独立验收、[plan.md](./plan.md) 的 Verification Plan 和 [quickstart.md](./quickstart.md) 中明确要求后端回归、前端测试/构建与手工验收，因此任务清单包含对应测试任务。  
**Organization**: 任务按用户故事组织，先建立会话主表、共享查询与回放基础，再按 P1 → P2 → P3 逐步交付，保证每个故事都能独立实现、独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可以并行执行（不同文件、没有未完成前置依赖）
- **[Story]**: 仅用于用户故事阶段，表示任务归属的故事
- 每个任务都显式列出真实文件路径，便于直接执行

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 为会话主表、历史查询接口和前端 hydrate 建立文件骨架

- [ ] T001 在 `ai-agent-station-study-app/src/main/resources/db/schema.sql` 与 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_session_ledger_mapper.xml` 建立 `ai_agent_dialogue_session` DDL 与 Mapper XML 骨架
- [ ] T002 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueSession.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/DialogueSessionUpsertRecord.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/DialogueSessionView.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueSessionLedgerDao.java` 建立会话主表领域模型与 DAO 骨架
- [ ] T003 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ConversationHistoryReplayService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/HistoryReplayPrinter.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationHistoryController.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationHistoryDetailRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationSessionRespVO.java` 建立历史查询服务与接口骨架
- [ ] T004 [P] 在 `ui/src/services/agentConversation.ts`、`ui/src/types/chat.ts`、`ui/src/utils/conversationHistory.ts`、`ui/src/utils/conversationHistory.test.ts`、`ui/src/pages/Home/RecentSessionList.tsx` 建立前端历史接口、hydrate helper 和轻量近期会话组件骨架

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 完成所有用户故事都会复用的会话头、共享查询与回放基础能力

**⚠️ CRITICAL**: 本阶段完成前，不开始任何用户故事实现

- [ ] T005 在 `ai-agent-station-study-app/src/main/resources/db/schema.sql`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueSession.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueSessionLedgerDao.java`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_session_ledger_mapper.xml` 完成 `ai_agent_dialogue_session` 字段、索引、查询与 upsert 映射
- [ ] T006 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerRunSupport.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java` 接入会话主表写侧维护，统一更新 `latest_request_id / latest_query_text / latest_summary_text / run_count / finished_run_count / failed_run_count / last_active_at`
- [ ] T007 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerQueryService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueRunLedgerDao.java`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml` 补齐 `querySession`、`queryRecentSessions`、`querySessionRuns` 与会话内顺序查询能力
- [ ] T008 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/replay/ReplayFactBundle.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/ToolInvocationProjectorRegistry.java` 建立 run + llm + tool + artifact 的统一历史回放输入与共享投影入口
- [ ] T009 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorTest.java` 建立会话主表、ordered runs、历史回放的共享测试夹具和基础回归

**Checkpoint**: 会话主表、共享查询契约和基础回放入口都已就绪，用户故事可以继续推进

---

## Phase 3: User Story 1 - 刷新后恢复当前会话 (Priority: P1) 🎯 MVP

**Goal**: 让页面在只拿到当前 `sessionId` 时恢复完整多轮历史，并在无历史时保持空白/初始态且提供手动选择近期会话的入口  
**Independent Test**: 准备一个包含多轮请求的已完成会话；刷新页面后只传原始 `sessionId`，确认历史完整恢复；若无历史，则页面保持空白并允许手动选择近期会话

### Tests for User Story 1 ⚠️

- [ ] T010 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java` 与 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java` 先补写会话详情按时间顺序恢复、`finalSummaryText` 兜底最终结论、无历史返回空结果的失败用例
- [ ] T011 [P] [US1] 在 `ui/src/utils/conversationHistory.test.ts`、`ui/src/utils/chat.test.ts` 与 `ui/src/pages/Home/RecentSessionList.test.tsx` 先补写 `replayFrames -> ConversationHistory`、无历史保持空白、手动选择近期会话入口可见的失败用例

### Implementation for User Story 1

- [ ] T012 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ConversationHistoryReplayService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/HistoryReplayPrinter.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java` 实现按 `sessionId` 聚合 runs 并输出详情 replay frames 的读服务
- [ ] T013 [US1] 在 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationHistoryController.java` 与 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationHistoryDetailRespVO.java` 实现 `GET /api/agent/conversation/sessions/{sessionId}` 详情接口与 VO 映射
- [ ] T014 [P] [US1] 在 `ui/src/services/agentConversation.ts`、`ui/src/types/chat.ts`、`ui/src/utils/conversationHistory.ts` 实现历史详情 API、详情类型定义和 `hydrateConversationFromReplayFrames(detail)` 恢复逻辑
- [ ] T015 [US1] 在 `ui/src/pages/Home/index.tsx` 与 `ui/src/pages/Home/RecentSessionList.tsx` 接入当前 `sessionId` 自动恢复逻辑，在恢复失败时保持空白/初始态并展示可点击的近期会话选择入口
- [ ] T016 [US1] 在 `specs/017-conversation-history-projector-replay/quickstart.md` 指定的场景 A、B、E 下执行 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java`、`ui/src/utils/conversationHistory.test.ts`、`ui/src/pages/Home/RecentSessionList.test.tsx` 的独立回归并记录结果

**Checkpoint**: User Story 1 完成后，当前 `sessionId` 的历史恢复已可独立演示，是本特性的 MVP

---

## Phase 4: User Story 2 - 历史与进行中保持同一套细节体验 (Priority: P2)

**Goal**: 让历史详情与实时对话共用同一套事件语义、失败/停止展示和产物引用展示逻辑  
**Independent Test**: 选取一条已结束的结构化会话，对比结束瞬间界面与历史重开界面，确认思考、任务、工具结果、最终答案和失败/缺失产物态保持一致

### Tests for User Story 2 ⚠️

- [ ] T017 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java` 先补写 realtime/history `eventData` 同构、`agent_name` 语义映射和强制停止终态展示的失败用例
- [ ] T018 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java`、`ui/src/utils/chat.test.ts`、`ui/src/utils/conversationHistory.test.ts` 先补写缺失 artifact 明确提示、失败 run 保留最后可见细节的失败用例

### Implementation for User Story 2

- [ ] T019 [P] [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/replay/ReplayFactBundle.java` 实现 `agent_name -> plan_thought / tool_thought / result` 映射和 `run.finalSummaryText` 最终答案 fallback
- [ ] T020 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/BaseAgentResponseHandler.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/ReactAgentResponseHandler.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/PlanSolveAgentResponseHandler.java` 收敛实时 `eventData` 组装逻辑，改为复用共享 projector
- [ ] T021 [P] [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/AbstractToolInvocationProjector.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/DefaultToolInvocationProjector.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ArtifactReferenceRespVO.java` 对齐工具结果、文件引用缺失态和失败/停止 run 的回放字段
- [ ] T022 [US2] 在 `ui/src/components/Dialogue/index.tsx`、`ui/src/components/ActionView/FilePreview.tsx`、`ui/src/components/ActionView/RunStatus.tsx`、`ui/src/utils/conversationHistory.ts` 接入共享历史 payload 的失败/停止态和缺失产物展示
- [ ] T023 [US2] 在 `specs/017-conversation-history-projector-replay/quickstart.md` 指定的场景 C 下执行 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorTest.java`、`ReactExecutionLedgerIntegrationTest.java`、`PlanSolveExecutionLedgerIntegrationTest.java`、`ui/src/utils/chat.test.ts` 的独立回归并记录结果

**Checkpoint**: User Story 2 完成后，历史详情与实时对话已经收敛到同一套细节语义

---

## Phase 5: User Story 3 - 会话摘要与会话详情保持一致 (Priority: P3)

**Goal**: 提供系统范围近期会话摘要列表，并保证标题、最近查询预览、状态、轮次统计与详情一致  
**Independent Test**: 准备多个状态不同的会话，查看近期会话列表并进入详情，确认默认只返回最近 20 条、按最近活动倒序，且摘要与详情统计一致

### Tests for User Story 3 ⚠️

- [ ] T024 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java` 与 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java` 先补写近期会话默认 20 条、`last_active_at` 倒序、摘要与详情统计一致的失败用例
- [ ] T025 [P] [US3] 在 `ui/src/pages/Home/RecentSessionList.test.tsx` 与 `ui/src/utils/conversationHistory.test.ts` 先补写近期会话列表展示标题/最近查询预览、点击后切换详情且不暴露总结正文的失败用例

### Implementation for User Story 3

- [ ] T026 [P] [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerQueryService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueSessionLedgerDao.java`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_session_ledger_mapper.xml` 实现近期会话摘要查询、默认 limit 归一和排序规则
- [ ] T027 [P] [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ConversationHistoryReplayService.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationHistoryController.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationSessionRespVO.java` 实现 `GET /api/agent/conversation/sessions?limit=20` 列表接口，并保证摘要字段与详情共用同一会话头统计
- [ ] T028 [P] [US3] 在 `ui/src/services/agentConversation.ts`、`ui/src/types/chat.ts`、`ui/src/pages/Home/RecentSessionList.tsx` 接入近期会话摘要类型和列表渲染，展示标题、最近查询预览、状态和最近活动信息
- [ ] T029 [US3] 在 `ui/src/pages/Home/index.tsx` 与 `ui/src/utils/conversationHistory.ts` 实现手动选择近期会话后加载对应详情，并保证列表摘要与详情状态/轮次数展示保持一致
- [ ] T030 [US3] 在 `specs/017-conversation-history-projector-replay/quickstart.md` 指定的场景 D、E 下执行 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java`、`ExecutionLedgerQueryServiceTest.java`、`ui/src/pages/Home/RecentSessionList.test.tsx` 的独立回归并记录结果

**Checkpoint**: User Story 3 完成后，近期会话摘要列表和详情已经形成一致、可手动选择的历史入口

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 完成文档、注释、安全假设和最终全量回归收口

- [ ] T031 [P] 在 `specs/017-conversation-history-projector-replay/quickstart.md`、`specs/017-conversation-history-projector-replay/contracts/conversation-history-api.md`、`specs/017-conversation-history-projector-replay/contracts/replay-hydration-contract.md` 回填最终实现约束、错误语义和手工验收说明
- [ ] T032 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ConversationHistoryReplayService.java`、`ui/src/utils/conversationHistory.ts` 清理重复历史映射逻辑并补齐关键中文注释
- [ ] T033 在 `specs/017-conversation-history-projector-replay/quickstart.md` 指定的命令下执行 `mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ExecutionLedgerQueryServiceTest,ReplayProjectorTest,ConversationHistoryControllerTest -Dsurefire.failIfNoSpecifiedTests=false`、`cd ui && npm run test -- conversationHistory.test.ts chat.test.ts`、`cd ui && npm run build` 并修复最后回归问题

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: 无依赖，可立即开始
- **Phase 2: Foundational**: 依赖 Setup 完成，阻塞全部用户故事
- **Phase 3: US1**: 依赖 Foundational 完成；这是 MVP，建议最先交付
- **Phase 4: US2**: 依赖 US1 已经打通历史详情恢复链，再收敛实时/历史同构语义
- **Phase 5: US3**: 依赖 Foundational 的会话头与查询能力；可以在 US1 完成后推进，但为了减少返工，建议在 US2 主要接口稳定后收口
- **Phase 6: Polish**: 依赖所有目标用户故事完成

### User Story Dependencies

- **US1 (P1)**: 只依赖 Foundational，是首个可交付增量
- **US2 (P2)**: 依赖 US1 已具备历史详情与 hydrate 主链路，但有独立 projector/实时同构验收
- **US3 (P3)**: 依赖 Foundational 的会话头写入和查询能力；前端选择入口可复用 US1 已有空态入口

### Within Each User Story

- 先补测试，再改实现
- 先完成模型/查询/投影核心，再接 controller 或 UI 层
- 每个故事完成后都要执行对应的独立回归和 quickstart 手工验收

### Parallel Opportunities

- Setup 中的 `T002`、`T003`、`T004` 可并行
- Foundational 中的 `T006`、`T007`、`T008` 可并行
- US1 中的 `T010`、`T011` 可并行；`T012`、`T014` 可并行
- US2 中的 `T017`、`T018` 可并行；`T019`、`T021` 可并行
- US3 中的 `T024`、`T025` 可并行；`T026`、`T028` 可并行
- Polish 中的 `T031`、`T032` 可并行

---

## Parallel Example: User Story 1

```text
T010 [US1] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java
T011 [US1] ui/src/utils/conversationHistory.test.ts

T012 [US1] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ConversationHistoryReplayService.java
T014 [US1] ui/src/services/agentConversation.ts
```

## Parallel Example: User Story 2

```text
T017 [US2] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorTest.java
T018 [US2] ui/src/utils/chat.test.ts

T019 [US2] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java
T021 [US2] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/AbstractToolInvocationProjector.java
```

## Parallel Example: User Story 3

```text
T024 [US3] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java
T025 [US3] ui/src/pages/Home/RecentSessionList.test.tsx

T026 [US3] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerQueryService.java
T028 [US3] ui/src/pages/Home/RecentSessionList.tsx
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational
3. 完成 Phase 3: US1
4. 执行 `T016` 的自动化与手工验证
5. 在确认“刷新恢复当前会话”稳定后再继续

### Incremental Delivery

1. 先完成 Setup + Foundational，打稳会话主表、共享查询与历史回放基础
2. 交付 US1，解决“当前 `sessionId` 刷新后无法恢复”的核心问题
3. 交付 US2，收敛实时和历史的细节语义
4. 交付 US3，补齐系统范围近期会话摘要与详情一致性
5. 最后执行 Polish，完成文档、注释和全量回归

### Parallel Team Strategy

1. 一组先完成 Setup + Foundational
2. US1 完成后：
   - 一组推进 US2 的 projector / realtime handler 收敛
   - 一组推进 US3 的 recent sessions 查询与前端列表
3. 最后共同完成 Polish 和全量回归

---

## Notes

- 所有任务都限定在 `ai-agent-station-study-domain`、`ai-agent-station-study-trigger`、`ai-agent-station-study-app`、`ui` 范围内，本期不改 `reactor-tool/`、`reactor-client/`
- `ai_agent_dialogue_session` 只允许承接会话级摘要与排序信息，不得复制 LLM/tool 细节
- `ReplayProjector` 必须成为 `agent_name` 语义映射的唯一收口点，禁止 controller、UI 再各写一套判断
- 近期会话系统范围可见性的安全边界按“当前受控内部环境”处理，若后续引入 owner/tenant，只能收紧查询条件，不重写回放链路
- 复杂逻辑、边界条件和 fallback 规则都要补中文注释
