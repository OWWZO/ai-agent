# Tasks: ReAct / PlanSolve 完整链路会话上下文复原

**Input**: Design documents from `/specs/006-session-context-memory/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: `spec.md` 明确要求覆盖同会话续聊、历史重开、长输出引用化、`ERROR / FORCE_STOPPED` 排除、模式冲突与并发冲突，因此本任务清单包含对应的后端自动化测试与 quickstart 手工验收任务。  

**Organization**: 任务按用户故事组织，先完成共享基础能力，再按 P1 → P2 → P3 逐步交付，保证每个故事都能独立实现、独立验收。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（不同文件、无未完成依赖）
- **[Story]**: 对应用户故事标签（`[US1]`、`[US2]`、`[US3]`）
- 所有任务都显式列出真实文件路径

## Phase 1: Setup (Shared Fixtures & Inputs)

**Purpose**: 准备 transcript-ledger 改造所需的测试夹具和样本载荷

- [X] T001 [P] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryTestSupport.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryFixtureFactory.java` 补齐已完成 turn、snapshot 边界、legacy payload、长输出引用化场景的共享夹具
- [X] T002 [P] 新增 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionEventPayloadFixtureBuilder.java`，集中构造 `assistant_thought / tool_use / tool_result / artifact_reference` 样本 payload，避免各测试类重复拼装 JSON

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 建立 transcript block 运行时模型、全量事件查询能力和共享映射规则

**⚠️ CRITICAL**: 本阶段完成前，禁止开始任何用户故事任务

- [X] T003 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/TranscriptContextBlock.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/TranscriptBlockType.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/SessionTurnMemory.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/SessionWorkingMemory.java` 落地“turn + ordered transcript blocks”运行时模型
- [X] T004 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/req/AgentRequest.java` 新增 `messageType / toolCalls / toolCallId / artifactRefs / referenceOnly / files` 等结构化字段，明确 `AgentRequest.Message` 的兼容扩展方案
- [X] T005 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentMessageEventDao.java` 与 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_event_mapper.xml` 把 working-memory 读取从 `queryArtifactEventsByMessageIds(...)` 升级为“按 messageIds 批量读取完整 final events”
- [X] T006 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionTranscriptBlockAssembler.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java` 实现共享的 `event -> block` 映射、`toolUseId` 提取和 reference-only 判定规则

**Checkpoint**: transcript block 模型、事件查询能力和共享映射契约已经就绪，用户故事可以开始实现

---

## Phase 3: User Story 1 - 同会话延续完整执行链 (Priority: P1) 🎯 MVP

**Goal**: 同一 `sessionId` 连续追问时，模型能够感知上一轮的思考、工具调用链、工具结果和稳定引用，而不是只记住“用户问题 + 最终回答”

**Independent Test**: 第一轮执行搜索/MCP/skilltool/文件/命令等工具链，第二轮用“继续”“基于上次结果补充”等指令追问，验证 working memory 会复用真实历史工具链且模式冲突/并发冲突不回归

### Tests for User Story 1

- [X] T007 [P] [US1] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionWorkingMemoryAssemblerTest.java`，覆盖 snapshot 边界之后的完整 transcript rebuild、无 event 回退为 `query + response`、同轮多 block 顺序保留
- [X] T008 [P] [US1] 新增 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/AgentStreamPersistWorkingMemoryMessagesTest.java`，断言 `buildWorkingMemoryMessages(...)` 会生成 `assistant_thought / tool_use / tool_result / artifact_reference` 对应的 richer `AgentRequest.Message`

### Implementation for User Story 1

- [X] T009 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionWorkingMemoryAssembler.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java` 接入“快照边界后 completed turns + 完整 final events”的 transcript 装配逻辑
- [X] T010 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 用 ordered transcript blocks 重写 `buildWorkingMemoryMessages(...)`，避免继续退化成纯 `query + response`
- [X] T011 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/step/RootNode.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/step/Step1SopRecallAndPrepareNode.java` 打通 richer `AgentRequest.Message` 到现有 `agent.dto.Message` 的转换
- [ ] T012 [US1] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/AgentStreamPersistServiceSessionGuardTest.java` 并按 `specs/006-session-context-memory/quickstart.md` 第 2、6、7 节执行验收，确认 rich transcript 注入后 `mode_conflict / session_busy` 语义不回归

**Checkpoint**: US1 完成后，同一会话的连续追问已经能恢复完整工具链，是本特性的 MVP

---

## Phase 4: User Story 2 - 历史重开仍可恢复完整上下文 (Priority: P2)

**Goal**: 重新进入旧会话后，系统仍能基于持久化账本恢复之前的思考、工具结果和稳定文件引用继续工作

**Independent Test**: 完成一段带有 MCP / skilltool / 文件 / 命令结果的历史会话，重开后继续追问，验证运行时上下文与历史展示都基于同一套 transcript 语义

### Tests for User Story 2

- [X] T013 [P] [US2] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryReopenResumeTest.java` 与 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryArtifactTest.java`，覆盖历史重开后恢复工具结果、artifactRefs 和 legacy payload 兼容

### Implementation for User Story 2

- [X] T014 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionArtifactRestoreSupport.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java` 复用同一套 transcript block 语义，保证“前端看得到、模型也用得到”
- [X] T015 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageEventServiceImpl.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java` 收口 artifactRefs/legacy 文件字段的规范化输出，保证历史重开与续聊装配使用同一份 canonical payload
- [ ] T016 [US2] 按 `specs/006-session-context-memory/quickstart.md` 第 3 节执行历史重开验收，并在 `specs/006-session-context-memory/acceptance-report.md` 记录至少 1 组 REACT 与 1 组 PLAN_SOLVE 样本结果

**Checkpoint**: US2 完成后，历史重开可以稳定恢复事件链和稳定文件引用

---

## Phase 5: User Story 3 - 直接回放窗口内的长链路上下文不再退化 (Priority: P3)

**Goal**: 对仍位于快照边界之后的复杂历史，系统保留关键执行链顺序；对长输出只保留关键结果与稳定引用，不再退化成 summary-only

**Independent Test**: 构造包含重复工具调用、缺失 `toolUseId`、超长报告正文和 `ERROR / FORCE_STOPPED` 轮次的样本，验证 working memory 仍能保留关键 tool chain 且不会把大段正文或未完成事实误带入下一轮

### Tests for User Story 3

- [X] T017 [P] [US3] 新增 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionTranscriptBlockAssemblerTest.java`，覆盖 repeated tool calls、`tool_result` 配对、缺失 `toolUseId` 的确定性 fallback、`deepsearch report` reference-only 规则
- [ ] T018 [P] [US3] 扩展 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionWorkingMemoryAssemblerTest.java` 与 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryReopenResumeTest.java`，覆盖 `ERROR / FORCE_STOPPED` 排除和边界前摘要/边界后 rich transcript 共存

### Implementation for User Story 3

- [X] T019 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionTranscriptBlockAssembler.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java` 实现 `toolUseId` 提取优先级、最近未闭合调用回退配对和长输出 reference-only 裁剪
- [X] T020 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionWorkingMemoryAssembler.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryPromptFormatter.java` 保证 `ai_agent_session_memory` 只负责边界前摘要，边界后直接回放窗口保留 rich transcript，不回退为 summary-only
- [ ] T021 [US3] 按 `specs/006-session-context-memory/quickstart.md` 第 4、5、8 节执行验收，并在 `specs/006-session-context-memory/acceptance-report.md` 记录 reference-only、边界共存和未完成轮次排除结果

**Checkpoint**: US3 完成后，边界后的复杂事件链不会再被简化成“问题 + 最终回答”

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 完成文档同步、回归测试与最终验收

- [ ] T022 在 `specs/006-session-context-memory/plan.md`、`specs/006-session-context-memory/data-model.md`、`specs/006-session-context-memory/contracts/send-stream-session-memory.md`、`specs/006-session-context-memory/contracts/session-memory-rebuild.md`、`specs/006-session-context-memory/contracts/session-memory-storage.md`、`specs/006-session-context-memory/quickstart.md` 回填最终实现细节，确保文档与代码一致
- [X] T023 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionTranscriptBlockAssembler.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionWorkingMemoryAssembler.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 补齐中文注释、边界日志与异常兜底，避免 transcript 装配 silent failure
- [ ] T024 执行 `mvn test -pl ai-agent-station-study-app -DskipTests=false`，并在 `specs/006-session-context-memory/acceptance-report.md` 汇总自动化回归与 quickstart 手工验收结果

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: 无依赖，可立即开始
- **Phase 2: Foundational**: 依赖 Phase 1，且阻塞所有用户故事
- **Phase 3: US1**: 依赖 Phase 2；这是 MVP，建议最先落地
- **Phase 4: US2**: 依赖 Phase 2 和 US1 的 transcript rebuild 主链路
- **Phase 5: US3**: 依赖 Phase 2，并复用 US1/US2 已建立的 transcript 语义与 reopen 能力
- **Phase 6: Polish**: 依赖所有目标用户故事完成

### User Story Dependencies

- **US1 (P1)**: 基于 foundational 能力即可独立完成，是本特性的 MVP
- **US2 (P2)**: 依赖 US1 的 working-memory rebuild 主链路，但可独立验证“历史重开恢复”
- **US3 (P3)**: 复用 US1/US2 的 transcript 语义，实现复杂事件链、reference-only 和未完成轮次排除

### Within Each User Story

- 自动化测试任务优先于实现任务，先补失败断言或夹具
- 先完成存储/映射/运行时模型，再打通 Agent 注入与历史回放
- 每个故事完成后都必须按 `quickstart.md` 中对应章节独立验收

---

## Parallel Opportunities

- `T001` 与 `T002` 可并行：分别处理共享夹具和事件 payload builder
- `T004` 与 `T005` 可并行：分别处理 `AgentRequest.Message` 结构扩展和事件查询能力
- `T007` 与 `T008` 可并行：分别覆盖 assembler 和 preloaded messages 组装测试
- `T013` 可与 `T017` 并行：分别验证历史重开与复杂事件链映射
- `T022` 与 `T023` 可并行：分别同步文档与补强日志/注释

### Parallel Example: User Story 1

```bash
Task: T007 扩展 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionWorkingMemoryAssemblerTest.java
Task: T008 新增 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/AgentStreamPersistWorkingMemoryMessagesTest.java
```

### Parallel Example: User Story 3

```bash
Task: T017 新增 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionTranscriptBlockAssemblerTest.java
Task: T018 扩展 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionWorkingMemoryAssemblerTest.java 与 SessionMemoryReopenResumeTest.java
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational
3. 完成 Phase 3: US1
4. 按 `quickstart.md` 第 2、6、7 节验证 rich transcript 续聊与守卫语义
5. 通过后再继续 US2 和 US3

### Incremental Delivery

1. 先交付 US1，解决“同一会话记住完整执行链”的核心价值
2. 再交付 US2，解决历史重开后的完整上下文恢复
3. 最后交付 US3，解决复杂事件链、长输出引用化和未完成轮次排除
4. Phase 6 统一补强文档、日志与最终回归

### Parallel Team Strategy

1. 一人先完成 `T003-T006` 的基础能力
2. 一人推进 US1 的 assembler 与 richer message 注入
3. 一人推进 US2 的 replay / reopen 共享语义
4. 一人推进 US3 的复杂事件映射与 reference-only 策略
5. 每个故事结束后按 quickstart 独立验收，再进入下一阶段

---

## Notes

- `ai_agent_message + ai_agent_message_event` 是边界后 rich transcript 的主来源；`ai_agent_session_memory` 只负责边界与摘要
- 本期不新增 MySQL 表结构，也不改 `SessionMemoryCompactionService`、`ai_agent_session_memory` 写入语义或上下文压缩策略
- `AgentRequest.Message` 采用结构化扩展方案，不再把完整 transcript 压成单个字符串字段
- 长输出默认走“关键结果 + 稳定引用”；`deepsearch report` 正文、超长命令输出、大 `diff` 不直接内联到 working memory
- `ERROR / FORCE_STOPPED` 轮次保留历史展示价值，但不能进入新的 working memory
