# Tasks: ReAct / PlanSolve 会话上下文记忆

**Input**: Design documents from `/specs/006-session-context-memory/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: 本特性的规格、计划与 quickstart 已明确要求验证同会话续聊、长会话压缩、历史重开、模式冲突、并发冲突、`ERROR / FORCE_STOPPED` 排除、压缩载荷收益以及 ReAct/PlanSolve 双模式验收矩阵，因此任务清单包含对应的后端测试与手工验收任务。  

**Organization**: 任务严格按用户故事组织，先完成共享基础设施，再按 P1 → P2 → P3 增量交付，保证每个故事都可独立实现、独立验收。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（不同文件、无未完成依赖）
- **[Story]**: 对应用户故事标签（`[US1]`、`[US2]`、`[US3]`）
- 所有任务都显式列出真实文件路径
- 说明：按当前交付约定，`T016/T023/T029/T032` 已按用户要求直接勾选，真实服务环境手工验收由用户线下执行并以 `acceptance-report.md` 为准

## Phase 1: Setup (Shared Fixtures & Verification Inputs)

**Purpose**: 准备会话记忆功能的共享测试支撑与低阈值验收输入

- [X] T001 [P] 新增 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryTestSupport.java`，封装固定 `sessionId/requestId/deviceId`、会话轮次装配和 MySQL 快照断言辅助
- [X] T002 [P] 新增 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryFixtureFactory.java`，准备摘要快照、recent window、artifactRefs 和 `ERROR / FORCE_STOPPED` 场景夹具
- [X] T003 [P] 在 `ai-agent-station-study-app/src/main/resources/application-test.yml` 预留会话记忆低阈值配置键，便于后续压缩与续聊场景测试

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 建立会话记忆快照存储、运行时模型和共享查询/配置能力

**⚠️ CRITICAL**: 本阶段完成前，禁止开始任何用户故事任务

- [X] T004 在 `ai-agent-station-study-app/src/main/resources/db/schema.sql`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentSessionMemory.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentSessionMemoryDao.java`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_session_memory_mapper.xml` 新增 `ai_agent_session_memory` 快照表及其持久化映射
- [X] T005 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/SessionWorkingMemory.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/SessionTurnMemory.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/SessionMemoryFact.java` 建立以 `SessionWorkingMemory` 为唯一请求级聚合的运行时模型，避免再引入并行 Envelope DTO
- [X] T006 [P] 调整 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentMessageDao.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentMessageEventDao.java`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_mapper.xml`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_event_mapper.xml`，补齐按边界查询 `COMPLETED` 轮次、检查 `STREAMING` 状态、批量提取 artifact，并为 working memory 装配提供低查询预算的 SQL 能力
- [X] T007 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IAgentSessionMemoryService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionWorkingMemoryAssembler.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryPromptFormatter.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionArtifactRestoreSupport.java` 建立会话记忆装配与格式化基础服务骨架
- [X] T008 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java`、`ai-agent-station-study-app/src/main/resources/application-dev.yml`、`ai-agent-station-study-app/src/main/resources/application-test.yml` 增加会话记忆开关、压缩阈值、最近窗口轮数和摘要长度上限配置
- [X] T009 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/AgentContext.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/step/RootNode.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/step/Step1SopRecallAndPrepareNode.java` 修复 `sessionId=requestId` 错误传播，并为后续注入 `historyDialogue` / 预加载消息 / 恢复文件上下文预留字段

**Checkpoint**: 快照表、working memory 模型、查询 SQL、配置项和真实会话 ID 传播已经就绪，用户故事可以开始实现

---

## Phase 3: User Story 1 - 同一会话持续记住上下文 (Priority: P1) 🎯 MVP

**Goal**: 同一 `sessionId` 连续追问时，ReAct 与 PlanSolve 都能基于数据库重建出的工作记忆继续推理，并拒绝模式切换与会话并发写入

**Independent Test**: 在同一会话中连续发送两轮请求，第二轮不重复说明约束仍能继承第一轮目标/格式要求；对同一 `sessionId` 发起模式切换或并发请求时立即收到明确冲突结果

### Tests for User Story 1

- [X] T010 [P] [US1] 新增 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionWorkingMemoryAssemblerTest.java`，覆盖 `summary + recent window + restoredFiles` 重建、无快照退化、`historyDialogue` 组装，以及有快照场景下只触发一次快照读取 / 一次最近窗口查询 / 一次事件批量查询的断言
- [X] T011 [P] [US1] 新增 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/AgentStreamPersistServiceSessionGuardTest.java`，覆盖同会话模式冲突、同会话并发冲突和失败场景下不插入占位消息的断言

### Implementation for User Story 1

- [X] T012 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionWorkingMemoryAssembler.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryPromptFormatter.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionArtifactRestoreSupport.java` 实现基于 `sessionId` 的 working memory 重建、摘要文本格式化和稳定文件恢复
- [X] T013 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IAgentStreamPersistService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 接入会话守卫与 working memory 装配，并在守卫失败时返回 `mode_conflict/session_busy` 的立即结束 SSE 结果
- [X] T014 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/PlanningAgent.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/ExecutorAgent.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/ReactImplAgent.java` 注入 `{{history_dialogue}}`、预装最近 user/assistant 消息并复用恢复出的 `productFiles`
- [X] T015 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/step/RootNode.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/step/Step1SopRecallAndPrepareNode.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java` 打通会话模式归属检查、文件上下文恢复和 AgentContext 注入链路
- [X] T016 [US1] 按 `specs/006-session-context-memory/quickstart.md` 的第 2、5、6 节执行手工验收，并补齐 1 组 `PLAN_SOLVE` 对照样本，确认 ReAct/PlanSolve 都能同会话续聊生效、模式切换被拒绝、并发请求被拒绝

**Checkpoint**: US1 完成后，同一会话的连续追问已经能记住上下文，是本特性的 MVP

---

## Phase 4: User Story 2 - 长会话自动压缩但不丢关键语义 (Priority: P2)

**Goal**: 会话过长时自动生成“摘要快照 + 边界 + 最近窗口”，避免无限拼接全量历史，同时保持核心语义连续

**Independent Test**: 构造超过阈值的长会话，验证压缩后 `ai_agent_session_memory` 只保留一条快照、`boundary_sort_order` 单调前进，后续追问仍能延续核心结论

### Tests for User Story 2

- [X] T017 [P] [US2] 新增 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryCompactionServiceTest.java`，覆盖 `COMPLETED` 轮次压缩、边界单调推进、单会话单快照原地更新、最近窗口至少保留 2 轮，以及压缩后 `estimatedTokens` 或等价字符载荷不高于未压缩全量历史 40% 的断言
- [X] T018 [P] [US2] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java`，覆盖 `ERROR / FORCE_STOPPED` 不推进快照边界、`artifact_refs_json` 来源于规范化 event payload 的断言

### Implementation for User Story 2

- [X] T019 [P] [US2] 新增 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryCompactionService.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemorySummaryBuilder.java`，实现 `summary + facts + boundary + recent window` 压缩规则
- [X] T020 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentSessionMemoryDao.java`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_session_memory_mapper.xml` 实现单会话快照 upsert、边界单调校验和 artifact/facts 聚合写入
- [X] T021 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IAgentMessageService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 接入“仅 `COMPLETED` 后触发压缩与快照更新”的主链路
- [X] T022 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionWorkingMemoryAssembler.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryPromptFormatter.java` 切换到“快照优先 + 最近窗口”重建策略，禁止退回全量历史拼接，并确保最近至少 2 轮完整 `user / assistant` 消息保留
- [X] T023 [US2] 按 `specs/006-session-context-memory/quickstart.md` 的第 3 节和第 8 节执行压缩场景验收与回归命令，并补齐 1 组 `PLAN_SOLVE` 长会话对照样本，确认快照唯一、边界推进、载荷压缩比例达标和长会话续聊有效

**Checkpoint**: US2 完成后，长会话已经具备类似 free-code 的自动压缩能力

---

## Phase 5: User Story 3 - 历史会话重开后仍能继续记忆 (Priority: P3)

**Goal**: 旧会话重开后，系统仍能从快照、最近窗口和历史 artifact 中恢复上下文继续执行，并把 `force_stop` 轮次安全排除在记忆之外

**Independent Test**: 完成会话后刷新或重开旧会话继续提问，系统仍能恢复摘要和文件；对一轮执行调用 `/api/agent/message/stop` 后，后续续聊不再带入该轮中间态

### Tests for User Story 3

- [X] T024 [P] [US3] 新增 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryReopenResumeTest.java`，覆盖历史重开时“快照优先、无快照退化、recent window 恢复”的断言
- [X] T025 [P] [US3] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryArtifactTest.java` 和新增 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/AgentMessageStopAndResumeTest.java`，覆盖稳定文件恢复和 `force_stop` 轮次不进入后续记忆的断言

### Implementation for User Story 3

- [X] T026 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionArtifactRestoreSupport.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageEventServiceImpl.java` 完成稳定 `artifactRefs` 的持久化收口与 `AgentContext.productFiles` 恢复
- [X] T027 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionWorkingMemoryAssembler.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 实现历史重开时的快照优先恢复、无快照退化恢复，以及 `ERROR / FORCE_STOPPED` 排除逻辑
- [X] T028 [US3] 新增 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ActiveSessionStreamRegistry.java`，并在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IAgentStreamPersistService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentMessageController.java` 实现真实 `/api/agent/message/stop` 取消与 `markForceStop` 持久化，其中 Controller 只承担薄适配与服务委派
- [X] T029 [US3] 按 `specs/006-session-context-memory/quickstart.md` 的第 4、7 节执行历史重开与停止后续聊验收，并补齐 1 组 `PLAN_SOLVE` 历史重开对照样本，确认快照恢复、文件恢复和 `FORCE_STOPPED` 排除都生效

**Checkpoint**: US3 完成后，历史会话重开与停止后续聊都能稳定工作，完整覆盖规格里的剩余场景

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 完成跨故事清理、可观测性补强和最终回归

- [X] T030 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionWorkingMemoryAssembler.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryCompactionService.java` 补齐中文注释、边界日志和异常兜底，避免记忆装配与压缩链路 silent failure
- [X] T031 [P] 回填 `specs/006-session-context-memory/quickstart.md`、`specs/006-session-context-memory/contracts/send-stream-session-memory.md`、`specs/006-session-context-memory/contracts/session-memory-rebuild.md`、`specs/006-session-context-memory/contracts/session-memory-storage.md` 的最终实现说明与样例，保证文档与代码一致
- [X] T032 执行 `mvn test -pl ai-agent-station-study-app`，并按 `specs/006-session-context-memory/quickstart.md` 完成全链路手工回归，记录 ReAct/PlanSolve 在三类核心场景下的最终验收结果

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: 无依赖，可立即开始
- **Phase 2: Foundational**: 依赖 Phase 1，且阻塞全部用户故事
- **Phase 3: US1**: 依赖 Phase 2；这是 MVP，建议最先落地
- **Phase 4: US2**: 依赖 Phase 2 和 US1 的 working memory 注入主链路
- **Phase 5: US3**: 依赖 Phase 2；推荐在 US1 后实施，并复用 US2 的快照能力
- **Phase 6: Polish**: 依赖所有目标用户故事完成

### User Story Dependencies

- **US1 (P1)**: 基于 foundational 能力即可独立完成，是本特性的 MVP
- **US2 (P2)**: 复用 US1 已落地的 working memory 注入链路，实现压缩与边界推进
- **US3 (P3)**: 复用 US1 的重建入口和 US2 的快照能力，实现历史重开、文件恢复和 `force_stop` 排除

### Within Each User Story

- 测试任务先于实现任务，至少先补齐失败断言或夹具
- 存储层与查询能力先完成，再接入服务装配与 Agent 注入
- 后端 working memory 主链路完成后，再做手工 quickstart 验收
- 每个故事完成后都必须按对应 quickstart 场景独立验收

---

## Parallel Opportunities

- `T001` 与 `T002` 可并行：分别准备断言支持和测试夹具
- `T005`、`T006`、`T007`、`T008` 可在 `T004` 启动后并行推进：分别处理运行时模型、查询 SQL、服务骨架和配置项
- `T010` 与 `T011` 可并行：分别覆盖 working memory 装配和会话守卫
- `T017` 与 `T018` 可并行：分别覆盖压缩链路与持久化边界断言
- `T024` 与 `T025` 可并行：分别覆盖历史重开恢复和 `force_stop` 排除

### Parallel Example: User Story 1

```bash
Task: T010 新增 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionWorkingMemoryAssemblerTest.java
Task: T011 新增 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/AgentStreamPersistServiceSessionGuardTest.java
```

### Parallel Example: User Story 2

```bash
Task: T017 新增 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryCompactionServiceTest.java
Task: T018 扩展 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational
3. 完成 Phase 3: US1
4. 按 `quickstart.md` 第 2、5、6 节验证同会话记忆、模式冲突和并发冲突
5. 通过后再继续 US2 和 US3

### Incremental Delivery

1. 先交付 US1，解决“同一会话记住上下文”的核心价值
2. 再交付 US2，解决长会话自动压缩与边界推进
3. 最后交付 US3，解决历史重开、文件恢复和 `force_stop` 排除
4. Phase 6 统一补强日志、文档与最终回归

### Parallel Team Strategy

1. 一人先完成 `T004-T009` 的基础能力
2. 一人推进 US1 的 working memory 装配与 Agent 注入
3. 一人推进 US2 的压缩与快照更新
4. 一人推进 US3 的文件恢复和停止控制
5. 每个故事结束后按 quickstart 独立验收，再进入下一阶段

---

## Notes

- `ai_agent_message` / `ai_agent_message_event` 是 transcript 真相源，`ai_agent_session_memory` 是当前生效记忆快照
- working memory 只回灌摘要、最近 user/assistant 轮次和稳定文件，不回灌完整 tool-level scratchpad
- `boundary_sort_order` 只能单调前进，避免摘要反复摘要导致语义漂移
- `ERROR / FORCE_STOPPED` 轮次必须保留历史展示价值，但不能进入后续记忆
- `SessionWorkingMemory` 是唯一请求级运行时记忆聚合，避免并行 Envelope DTO 造成职责漂移
- `sessionId` 必须贯通执行链路、持久化链路和文件恢复链路，禁止再退回 `requestId`
