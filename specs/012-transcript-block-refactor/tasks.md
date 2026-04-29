# Tasks: TranscriptBlock 会话记忆重写

**Input**: Design documents from `/specs/012-transcript-block-refactor/`  
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: 本特性在 [plan.md](./plan.md) 的 Verification Plan 中已经明确要求 Java 回归、前端构建校验和手工验收，因此本任务清单包含目标测试与验证任务。  
**Organization**: 任务按用户故事组织，确保每个故事都可以独立实现、独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可以并行执行（不同文件、无未完成前置依赖）
- **[Story]**: 仅用于用户故事阶段，表示任务归属的故事
- 每个任务都写出真实文件路径，便于直接执行

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 为硬切换重写建立新表、新文件和回归夹具骨架

- [x] T001 在 `ai-agent-station-study-app/src/main/resources/db/schema.sql` 中加入 `ai_agent_turn`、`ai_agent_transcript_block`、`ai_agent_display_event` 和新版 `ai_agent_session_memory` 的硬切换注释与 DDL 骨架
- [x] T002 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/Turn.java`、`TranscriptBlock.java`、`DisplayEvent.java`、`SessionMemory.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/enums/TranscriptBlockType.java` 建立新领域模型骨架
- [x] T003 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ITurnDao.java`、`ITranscriptBlockDao.java`、`IDisplayEventDao.java`、`ISessionMemoryDao.java` 以及 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/turn_mapper.xml`、`transcript_block_mapper.xml`、`display_event_mapper.xml`、`session_memory_mapper.xml` 建立新持久化骨架
- [x] T004 [P] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryFixtureFactory.java`、`SessionMemoryTestSupport.java` 与 `SessionEventPayloadFixtureBuilder.java` 准备 turn/block/display_event/snapshot 测试夹具入口

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 完成所有用户故事都会依赖的新事实模型、共享服务和接口边界

**⚠️ CRITICAL**: 本阶段完成前，不开始任何用户故事实现

- [x] T005 在 `ai-agent-station-study-app/src/main/resources/db/schema.sql`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/Turn.java`、`TranscriptBlock.java`、`DisplayEvent.java`、`SessionMemory.java` 与对应 DAO/Mapper XML 中实现新表字段、索引、状态和值对象的完整对齐
- [x] T006 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptBlockMapper.java`、`TurnWriter.java`、`TranscriptBlockWriter.java`、`DisplayEventProjector.java` 中实现写路径共享能力
- [x] T007 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptContextBuilder.java`、`TranscriptPromptFormatter.java`、`DisplayHistoryQueryService.java` 中实现读路径共享能力
- [x] T008 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/SessionMemoryPreparationResult.java`、`SessionMemoryDecisionType.java`、`SessionWorkingMemory.java`、`SessionTurnMemory.java`、`TranscriptContextBlock.java` 中重写共享配置和内存模型契约
- [x] T009 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/history/ConversationTurnDetail.java`、`ConversationEventDetail.java` 以及 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationDetailRespVO.java`、`ConversationTurnRespVO.java`、`ConversationEventRespVO.java` 中收口新的 `turn + displayEvents` 历史详情契约
- [x] T010 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/TranscriptBlockMapperTest.java`、`DisplayHistoryQueryServiceTest.java` 与 `SessionMemoryFixtureFactory.java` 中建立基础回归覆盖，验证新事实模型和新读模型的最小闭环

**Checkpoint**: 新表、新模型、新读写公共服务和新历史契约都已就绪，用户故事可继续推进

---

## Phase 3: User Story 1 - 同会话续聊沿用单一事实链 (Priority: P1) 🎯 MVP

**Goal**: 让同一 `sessionId` 的续聊只依赖新的 turn/block/snapshot 事实链继续任务，不再经过旧账本和多层兼容装配  
**Independent Test**: 发送一轮带工具调用和文件产出的 `REACT` / `PLAN_SOLVE` 请求后，在同一 `sessionId` 继续追问，确认请求开始前完成记忆准备，且续聊能直接引用上一轮结果与产物

### Tests for User Story 1

- [x] T011 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/TranscriptBlockMapperTest.java` 和 `AgentStreamPersistCoordinatorRewriteTest.java` 中先补写失败中的 turn/block 持久化与同轮顺序测试
- [x] T012 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/TranscriptContextBuilderTest.java` 和 `SessionMemoryPreparationServiceTest.java` 中先补写失败中的续聊上下文恢复与请求前预处理测试

### Implementation for User Story 1

- [x] T013 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistCoordinator.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TurnWriter.java`、`TranscriptBlockWriter.java` 中把流式结束写入重构为 `turn + transcript_block`
- [x] T014 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IAgentSessionMemoryService.java` 中把请求前记忆准备改为“加载最新快照 + 查询已完成 turns/blocks + 压缩失败继续当前请求”
- [x] T015 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptContextBuilder.java`、`TranscriptPromptFormatter.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/req/AgentRequest.java` 与 `AgentStreamPersistCoordinator.java` 中把新的 working context 接入 `historyDialogue`、结构化 messages 和 session files
- [x] T016 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionWorkingMemoryAssembler.java`、`SessionTranscriptBlockAssembler.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageServiceImpl.java`、`AgentMessageEventServiceImpl.java` 中移除旧续聊装配和旧消息服务的主路径调用
- [ ] T017 [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/AgentStreamPersistCoordinatorRewriteTest.java`、`SessionMemoryPreparationServiceTest.java`、`TranscriptContextBuilderTest.java` 和 `specs/012-transcript-block-refactor/quickstart.md` 中完成同会话续聊回归与手工验收

**Checkpoint**: User Story 1 完成后，同会话续聊已经完全依赖新的事实链，可单独演示和验证

---

## Phase 4: User Story 2 - 历史重开与实时续聊共享同一记忆来源 (Priority: P2)

**Goal**: 让历史详情和实时续聊都来自同一份持久化事实，其中历史详情直接读取 display read model，UI 不再做 history-only 兼容恢复  
**Independent Test**: 完成一条带多步执行和文件产物的会话，刷新后重新打开详情，确认看到的 turn/displayEvents 与续聊实际使用的事实来源一致

### Tests for User Story 2

- [x] T018 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java` 和 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/DisplayHistoryQueryServiceTest.java` 中先补写失败中的 `turn + displayEvents` 历史接口测试
- [x] T019 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java` 和 `ConversationHistoryArtifactTest.java` 中先补写失败中的 display read model 持久化与文件引用测试

### Implementation for User Story 2

- [x] T020 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/DisplayHistoryQueryService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IAgentConversationService.java` 与 `AgentConversationServiceImpl.java` 中用 display read model 替换旧历史装配
- [x] T021 [US2] 在 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationController.java`、`ConversationDetailRespVO.java`、`ConversationTurnRespVO.java`、`ConversationEventRespVO.java` 中输出新的 `turn + displayEvents` 详情契约
- [x] T022 [P] [US2] 在 `ui/src/services/agentConversation.ts` 和 `ui/src/types/chat.ts` 中把历史详情解析改为直接消费 `displayEvents`
- [x] T023 [US2] 在 `ui/src/pages/Home/index.tsx`、`ui/src/components/Dialogue/index.tsx`、`ui/src/components/ActionView/ActionView.tsx` 与 `ui/src/components/ActionView/FilePreview.tsx` 中把历史渲染改为直接基于 `displayEvents` 展示
- [x] T024 [US2] 在 `ui/src/services/agentConversation.ts`、`ui/src/utils/chatHistory.ts` 中删除旧的 history-only 兼容恢复桥接
- [ ] T025 [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java`、`ConversationHistoryPersistenceTest.java`、`ConversationHistoryArtifactTest.java` 和 `specs/012-transcript-block-refactor/quickstart.md` 中完成历史重开回归与手工验收

**Checkpoint**: User Story 2 完成后，历史重开和实时续聊共享同一事实来源，且 UI 已不再依赖旧历史兼容链

---

## Phase 5: User Story 3 - 长会话压缩后继续任务不丢状态 (Priority: P3)

**Goal**: 让长会话压缩围绕新的 turn/block/snapshot 模型运行，并在压缩失败时直接继续当前请求，不再走 reject/circuit 分支  
**Independent Test**: 构造超过压缩阈值的长会话，确认请求前先压缩、成功时生成新快照版本、失败时继续当前请求且不写入半成品 snapshot

### Tests for User Story 3

- [x] T026 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryCompactionServiceTest.java` 和 `SessionMemoryReopenResumeTest.java` 中先补写失败中的多版本快照与边界推进测试
- [x] T027 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryPreparationServiceTest.java` 和 `TranscriptContextBuilderTest.java` 中先补写失败中的压缩失败继续请求与最近窗口保留测试

### Implementation for User Story 3

- [x] T028 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryCompactionService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/SessionMemory.java` 和 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/session_memory_mapper.xml` 中把压缩逻辑重写为基于 turn/block 的多版本 snapshot 生成
- [x] T029 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ISessionMemoryDao.java`、`ITurnDao.java`、`ITranscriptBlockDao.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptContextBuilder.java` 中实现最新快照读取、边界之后已完成 turn 查询和最近窗口恢复
- [x] T030 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/SessionMemoryPreparationResult.java`、`SessionMemoryDecisionType.java` 和 `AgentSessionMemoryServiceImpl.java` 中删除 reject/circuit-open 主链路语义，保留“压缩失败直接继续”
- [ ] T031 [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryCompactionServiceTest.java`、`SessionMemoryReopenResumeTest.java`、`SessionMemoryPreparationServiceTest.java` 和 `specs/012-transcript-block-refactor/quickstart.md` 中完成长会话压缩回归与手工验收

**Checkpoint**: User Story 3 完成后，压缩逻辑已经围绕新事实模型稳定运行，长会话可以独立验证

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 清理遗留代码、完成硬切换收口和最终回归

- [x] T032 [P] 删除 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentMessage.java`、`AgentMessageEvent.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentMessageDao.java`、`IAgentMessageEventDao.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IAgentMessageService.java`、`IAgentMessageEventService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageServiceImpl.java`、`AgentMessageEventServiceImpl.java` 以及 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_mapper.xml`、`ai_agent_message_event_mapper.xml`
- [x] T033 [P] 删除 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java`、`ConversationEventPayloadNormalizer.java`、`ConversationEventFactSupport.java`、`EventProjector.java`、`PersistCoordinator.java`、`SessionWorkingMemoryAssembler.java`、`SessionTranscriptBlockAssembler.java`、`SessionMemorySummaryBuilder.java`、`SessionMemoryPromptFormatter.java`
- [x] T034 在 `ai-agent-station-study-app/src/main/resources/db/schema.sql`、`AGENTS.md` 和 `specs/012-transcript-block-refactor/quickstart.md` 中同步硬切换后的表名、验证步骤和中文注释说明
- [ ] T035 在 `specs/012-transcript-block-refactor/quickstart.md` 指定的命令与场景下完成最终回归、构建、SQL 抽查和验收收口，并修复在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/`、`ui/src/` 中暴露的最后问题

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: 无依赖，可立即开始
- **Phase 2: Foundational**: 依赖 Setup 完成，且阻塞所有用户故事
- **Phase 3: US1**: 依赖 Foundational 完成；这是 MVP，建议最先完成
- **Phase 4: US2**: 依赖 US1 已经把新的 turn/block/display 写路径接入主链路
- **Phase 5: US3**: 依赖 US1 已经完成新的续聊事实链；可在 US2 后半段并行推进
- **Phase 6: Polish**: 依赖所有目标用户故事完成，负责硬切换删除与最终收口

### User Story Dependencies

- **US1 (P1)**: 只依赖 Foundational，是第一个可交付增量
- **US2 (P2)**: 依赖 US1 的新事实写路径，因为历史重开要读取新产生的 display events
- **US3 (P3)**: 依赖 US1 的新 turn/block 事实链和 snapshot 基础，但不依赖 US2 的 UI 改造

### Within Each User Story

- 先补测试，再改实现
- 先完成模型/查询/写入核心，再接入口层或 UI 层
- 每个故事完成后都要跑独立回归和 quickstart 场景

### Parallel Opportunities

- Setup 中的 `T002`、`T003`、`T004` 可并行
- Foundational 中的 `T006`、`T007`、`T008` 可并行
- US1 中的 `T011`、`T012` 可并行；`T013` 与 `T014` 可并行
- US2 中的 `T018`、`T019` 可并行；`T022` 可在后端契约稳定后与 `T020` 并行
- US3 中的 `T026`、`T027` 可并行
- Polish 中的 `T032`、`T033` 可并行

---

## Parallel Example: User Story 1

```text
T011 [US1] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/TranscriptBlockMapperTest.java
T012 [US1] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/TranscriptContextBuilderTest.java

T013 [US1] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistCoordinator.java
T014 [US1] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java
```

## Parallel Example: User Story 2

```text
T018 [US2] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java
T019 [US2] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java

T020 [US2] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/DisplayHistoryQueryService.java
T022 [US2] ui/src/services/agentConversation.ts
```

## Parallel Example: User Story 3

```text
T026 [US3] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryCompactionServiceTest.java
T027 [US3] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/AgentStreamPersistServiceSessionGuardTest.java
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational
3. 完成 Phase 3: US1
4. 运行 `T017` 的自动化与手工验证
5. 在确认同会话续聊稳定后再继续

### Incremental Delivery

1. Setup + Foundational 打稳新事实模型
2. 交付 US1，确保续聊完全切到新链路
3. 交付 US2，确保历史重开与 UI 详情切换完成
4. 交付 US3，确保请求前压缩与多版本 snapshot 稳定
5. 最后执行 Polish，删除旧代码和旧表结构

### Parallel Team Strategy

1. 一组先完成 Setup + Foundational
2. US1 完成后：
   - 一组推进 US2 的后端/前端历史详情
   - 一组推进 US3 的压缩与 snapshot 重写
3. 最后共同完成旧链路删除和全量回归

---

## Notes

- [P] 任务表示不同文件、没有未完成依赖时可以并行执行
- 每个用户故事都保留了独立验证任务，便于中途停下做演示或回归
- 本清单按“先新链路可运行，再删旧链路”安排，避免过早删除导致无法调试
- 所有涉及复杂逻辑的代码块都应补中文注释
- 旧链路删除是本需求的一部分，Phase 6 不可跳过
