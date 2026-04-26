# Tasks: 后端事实对话账本重构

**Input**: Design documents from `/specs/011-transcript-fact-persistence/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: 本特性要求历史详情、生成文件查询和会话记忆恢复都可验证，因此任务清单包含对应的后端回归测试、前端构建校验和手工验收任务。  

**Organization**: 任务按用户故事组织，先完成共享基础能力，再按 P1 → P2 → P3 逐步交付，确保每个故事都能独立实现、独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（不同文件、无未完成依赖）
- **[Story]**: 对应用户故事标签（`[US1]`、`[US2]`、`[US3]`）
- 所有任务都显式列出真实文件路径

## Phase 1: Setup (Shared Fixtures & Inputs)

**Purpose**: 准备事实账本重构所需的共享夹具和验收输入

- [X] T001 [P] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryArtifactTest.java` 整理共享断言和样本装配入口
- [X] T002 [P] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionTranscriptBlockAssemblerTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryReopenResumeTest.java` 准备事实块恢复与续聊样本夹具

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 建立消息账本/事实块账本/历史投影的共享基础能力

**⚠️ CRITICAL**: 本阶段完成前，禁止开始任何用户故事任务

- [X] T003 在 `ai-agent-station-study-app/src/main/resources/db/schema.sql`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentMessage.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentMessageEvent.java` 明确消息表与事件表的新职责，并固化 `generated_files_json` 与事实块语义
- [X] T004 [P] 在 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_mapper.xml`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_event_mapper.xml`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentMessageDao.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentMessageEventDao.java` 校准消息/事件读写契约
- [X] T005 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/history/ConversationTurnDetail.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/history/ConversationEventDetail.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationTurnRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationEventRespVO.java` 收口历史详情输出模型
- [X] T006 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionArtifactRestoreSupport.java` 统一 artifact 引用归一化、重内容裁剪和生成文件摘要基础能力

**Checkpoint**: 基础账本模型、Mapper 契约和 artifact 归一化能力已经就绪，用户故事可以开始实现

---

## Phase 3: User Story 1 - 重开历史时复现与实时一致的最终结果 (Priority: P1) 🎯 MVP

**Goal**: 历史详情从后端事实账本投影后，仍能通过实时同款渲染契约复现对话结束时的最终可见结果

**Independent Test**: 完成一条 `PLAN_SOLVE` 和一条 `REACT` 会话，记录结束时的最终答案、关键细节块和文件入口；刷新并重开历史后，确认可见结果一致

### Tests for User Story 1

- [X] T007 [P] [US1] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java`，覆盖“事件表保存事实块而非 UI 快照”“同一轮多块顺序稳定”“生成文件摘要从事实块提取”的断言
- [X] T008 [P] [US1] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java`，覆盖历史详情接口返回 canonical live-like payload 且 `generatedFiles` 直接来自消息账本字段的断言

### Implementation for User Story 1

- [X] T009 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 把 `projectFinalDetailEvents(...)` 重构为“事实块收集 + 生成文件摘要提取”主链路
- [X] T010 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageEventServiceImpl.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/multi/OrderedEvent.java` 收口事实块写入语义，避免继续把前端最终态快照直接落库
- [X] T011 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java` 增加“事实块 -> 历史详情 canonical payload”投影逻辑
- [X] T012 [US1] 在 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationController.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationDetailRespVO.java` 保持详情接口外层结构稳定，并输出后端投影后的 payload
- [X] T013 [US1] 在 `ui/src/services/agentConversation.ts` 清理不再需要的 history-only payload 修补逻辑，优先消费后端直接投影好的 canonical payload
- [ ] T014 [US1] 按 `specs/011-transcript-fact-persistence/quickstart.md` 第 2 节执行验收，确认历史详情与实时结束态一致

**Checkpoint**: US1 完成后，历史详情已经可以基于后端事实账本复现实时结束态，是本特性的 MVP

---

## Phase 4: User Story 2 - 同会话续聊时恢复后端事实记忆 (Priority: P2)

**Goal**: 会话续聊时的工作记忆直接来自消息账本和事实块账本，而不是来自前端快照语义

**Independent Test**: 先完成一轮包含工具调用和文件产物的任务，再发送“继续刚才任务”，确认系统能感知上一轮事实与生成文件

### Tests for User Story 2

- [X] T015 [P] [US2] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionTranscriptBlockAssemblerTest.java`，覆盖事实块直接恢复 `ASSISTANT_THOUGHT / TOOL_USE / TOOL_RESULT / ARTIFACT_REFERENCE` 的断言
- [X] T016 [P] [US2] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryReopenResumeTest.java`，覆盖历史重开后仍可基于事实账本恢复工具链和生成文件的断言

### Implementation for User Story 2

- [X] T017 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionTranscriptBlockAssembler.java` 改为直接消费事实块账本，而不是继续从历史 UI payload 猜测 transcript 语义
- [X] T018 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionWorkingMemoryAssembler.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java` 共享新的事实账本恢复链路
- [X] T019 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 确保 `buildWorkingMemoryMessages(...)`、`historyDialogue` 与 `sessionFiles` 都使用事实账本恢复结果
- [ ] T020 [US2] 按 `specs/011-transcript-fact-persistence/quickstart.md` 第 4 节执行验收，确认续聊记忆来自后端事实账本

**Checkpoint**: US2 完成后，历史与续聊已经共享同一份后端事实真相源

---

## Phase 5: User Story 3 - 开发者可直接查询生成文件并优雅演进账本 (Priority: P3)

**Goal**: 生成文件可直接从消息账本查询，文件缺失态明确可解释，后续新增块类型只需扩展事实映射与投影规则

**Independent Test**: 完成一条生成 HTML/Markdown/PPT 或其他文件的会话，直接检查 `generated_files_json`、历史详情中的文件入口和缺失态表现

### Tests for User Story 3

- [X] T021 [P] [US3] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryArtifactTest.java`，覆盖 `generated_files_json`、`artifactRefs`、缺失态和大体量内容不再内联全文的断言
- [X] T022 [P] [US3] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java`，覆盖新增一种事实块类型时仅需补充映射规则而非新增前端快照字段的断言样本

### Implementation for User Story 3

- [X] T023 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionArtifactRestoreSupport.java` 稳定生成文件摘要写入和恢复逻辑
- [X] T024 [US3] 在 `ui/src/services/agentConversation.ts`、`ui/src/components/ActionView/FilePreview.tsx` 只基于 `generatedFiles` 与 `artifactRefs` 恢复历史文件预览和缺失态提示
- [X] T025 [US3] 清理 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java`、`ui/src/services/agentConversation.ts` 中不再需要的 payload 提取分支和兼容代码
- [ ] T026 [US3] 按 `specs/011-transcript-fact-persistence/quickstart.md` 第 3、5 节执行验收，确认生成文件可直接查询且文件缺失态明确

**Checkpoint**: US3 完成后，生成文件查询、历史文件预览和账本演进模型都已收口

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 完成文档同步、全链路回归和遗留清理

- [X] T027 在 `specs/011-transcript-fact-persistence/contracts/conversation-history-api.md`、`specs/011-transcript-fact-persistence/contracts/fact-event-storage.md`、`specs/011-transcript-fact-persistence/contracts/session-memory-rebuild.md` 回填最终实现样例
- [X] T028 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionTranscriptBlockAssembler.java` 补齐中文注释和边界日志
- [X] T029 执行 `mvn test -pl ai-agent-station-study-app -DskipTests=false` 并记录结果
- [X] T030 执行 `cd ui && npm run lint` 与 `cd ui && npm run build` 并记录结果

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: 无依赖，可立即开始
- **Phase 2: Foundational**: 依赖 Phase 1，且阻塞全部用户故事
- **Phase 3: US1**: 依赖 Phase 2；这是 MVP，建议最先落地
- **Phase 4: US2**: 依赖 Phase 2 和 US1 的事实账本主链路
- **Phase 5: US3**: 依赖 Phase 2，并复用 US1/US2 的生成文件与事实账本语义
- **Phase 6: Polish**: 依赖所有目标用户故事完成

### User Story Dependencies

- **US1 (P1)**: 基于 foundational 能力即可独立完成，是本特性的 MVP
- **US2 (P2)**: 复用 US1 已完成的事实账本持久化，但可独立验证续聊记忆恢复
- **US3 (P3)**: 复用 US1/US2 的事实账本和 artifact 语义，可独立验证生成文件查询与缺失态

### Within Each User Story

- 自动化测试任务先于实现任务
- 先完成后端账本建模与投影，再接前端最小消费适配
- 每个故事完成后都必须按 quickstart 对应章节独立验收

---

## Parallel Opportunities

- `T001` 与 `T002` 可并行：分别准备历史详情和 session memory 样本
- `T004` 与 `T005` 可并行：分别处理 Mapper 契约和详情输出模型
- `T007` 与 `T008` 可并行：分别覆盖持久化与详情接口断言
- `T015` 与 `T016` 可并行：分别覆盖事实块恢复和历史重开恢复
- `T021` 与 `T022` 可并行：分别覆盖 artifact/文件摘要和块类型扩展样本

### Parallel Example: User Story 1

```bash
Task: T007 扩展 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java
Task: T008 扩展 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java
```

### Parallel Example: User Story 2

```bash
Task: T015 扩展 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionTranscriptBlockAssemblerTest.java
Task: T016 扩展 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryReopenResumeTest.java
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational
3. 完成 Phase 3: US1
4. 按 `quickstart.md` 第 2 节验证历史详情与实时结束态一致
5. 通过后再继续 US2 和 US3

### Incremental Delivery

1. 先交付 US1，解决“历史重开效果不变”的核心问题
2. 再交付 US2，解决“后端记住之前事实”的核心诉求
3. 最后交付 US3，解决生成文件查询、缺失态和账本演进优雅性
4. Phase 6 统一完成回归和文档收尾

### Parallel Team Strategy

1. 一人先完成 `T003-T006` 的基础账本能力
2. 一人推进 US1 的后端事实块持久化与历史投影
3. 一人推进 US2 的 session memory 恢复
4. 一人推进 US3 的文件摘要与前端文件预览收口
5. 每个故事结束后按 quickstart 独立验收，再进入下一阶段

---

## Notes

- `ai_agent_message` 是 turn 级真相源，`ai_agent_message_event` 是 turn 内事实块真相源
- `generated_files_json` 是生成文件查询入口，不能再依赖事件 payload 临时提取
- 历史详情 API 返回的是后端投影后的 canonical payload，不是数据库原始 payload
- 旧历史数据允许清空，不做双路径兼容
