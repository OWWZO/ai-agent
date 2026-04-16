# Tasks: 对话历史最终态重构与一致性修复

**Input**: Design documents from `/specs/005-fix-history-replay/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`

**Tests**: 需要保留并扩展历史持久化、详情接口、工作区产物三类回归测试；每个用户故事都包含独立验收步骤。

**Organization**: 任务按用户故事组织，确保每个故事都能独立实现、独立验证，并且符合“只保留最终态细节、不做实时回放”的目标。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（在其前置依赖完成后，可与其他不同文件任务并行）
- **[Story]**: 用户故事标签（`[US1]`、`[US2]`、`[US3]`）
- 所有任务都显式列出真实文件路径

## Phase 1: Setup (Shared Inputs & Test Baseline)

**Purpose**: 固化最终态契约和验收样例，建立后续实现所依赖的测试基线

- [ ] T001 对齐 `specs/005-fix-history-replay/contracts/conversation-history-api.md`、`specs/005-fix-history-replay/contracts/final-detail-event-payload.md`、`specs/005-fix-history-replay/quickstart.md`，冻结最终态接口、payload 和手工验收口径
- [ ] T002 [P] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java` 和 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java` 准备多条最终细节事件、最终答案和历史重开场景的测试夹具
- [ ] T003 [P] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryArtifactTest.java` 准备 `artifactRefs` 成功预览与缺失态样例夹具

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 收敛三张历史表与最终态事件装配基础能力；完成前不得进入任何用户故事实现

**⚠️ CRITICAL**: 本阶段完成前，禁止开始 US1/US2/US3 的业务改造

- [ ] T004 调整 `ai-agent-station-study-app/src/main/resources/db/schema.sql`，收敛 `ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event` 的字段职责、默认值、约束与冗余索引
- [ ] T005 [P] 同步 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentConversation.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentMessage.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentMessageEvent.java` 的字段定义与中文注释
- [ ] T006 [P] 同步 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentConversationDao.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentMessageDao.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentMessageEventDao.java` 与 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_conversation_mapper.xml`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_mapper.xml`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_event_mapper.xml` 的持久化映射
- [ ] T007 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java` 定义最终态事件 payload、`artifactRefs` 和缺失态字段的 canonical 归一化规则
- [ ] T008 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageEventServiceImpl.java` 建立按 message 覆盖写入最终态细节事件的一次性持久化入口
- [ ] T009 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java` 收敛历史详情读取为“只装配最终态事件”

**Checkpoint**: 三表结构、DAO/Mapper、最终态 payload 归一化和历史详情装配基础能力已就绪，用户故事可以开始

---

## Phase 3: User Story 1 - 延迟回看仍能看到同一份最终细节 (Priority: P1) 🎯 MVP

**Goal**: 用户几天后重新打开深度思考历史时，仍能看到与对话结束时一致的最终答案、最终工具细节和结果展示

**Independent Test**: 完成一条 `PLAN_SOLVE` 会话，结束时记录最终答案、最终细节数量和顺序；刷新或延迟重开后，历史详情返回相同数量、相同顺序的最终态细节，且不包含过程回放片段

### Tests for User Story 1

- [ ] T010 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java` 覆盖“一条最终可见细节对应一条 `ai_agent_message_event` 记录”与多条同类型 `deep_search` 最终细节不折叠的断言
- [ ] T011 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java` 覆盖刷新后历史详情仍返回最终答案、最终细节数量/顺序一致，且不回放 `plan_thought`、`tool_thought`、`agent_stream` 的断言

### Implementation for User Story 1

- [ ] T012 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 将运行时思考/搜索/工具缓冲投影为最终可见细节集合，并在消息完成时一次性写入 `ai_agent_message_event`
- [ ] T013 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java` 保持历史重开时按 `seq_no` 返回最终细节，不合并同类结果项
- [ ] T014 [US1] 在 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationController.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationDetailRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationTurnRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationEventRespVO.java` 输出最终态 `turns[].events[]` 契约并兼容恒定 `isFinal=1`
- [ ] T015 [US1] 在 `ui/src/services/agentConversation.ts`、`ui/src/utils/chatHistory.ts`、`ui/src/hooks/useAgentConversation.ts` 按最终态事件恢复历史详情，保留多个同类型最终细节项
- [ ] T016 [US1] 在 `ui/src/pages/Home/index.tsx` 和 `ui/src/components/Dialogue/index.tsx` 清理对过程回放片段的依赖，确保历史重开只消费最终态细节
- [ ] T017 [US1] 按 `specs/005-fix-history-replay/quickstart.md` 场景 A 执行验收，记录结束时与历史重开后的最终细节数量、顺序和答案对照结果

**Checkpoint**: US1 完成后，历史详情已能稳定复现对话结束时的最终细节，是可演示的 MVP

---

## Phase 4: User Story 2 - 正确展示最终计划状态 (Priority: P2)

**Goal**: 已完成的计划步骤在历史重开后仍显示为已完成，不回退为初始计划组件

**Independent Test**: 完成一条包含多个计划步骤完成态的深度思考会话；刷新并重开历史后，所有已完成步骤继续显示 `completed`

### Tests for User Story 2

- [ ] T018 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java` 覆盖初始计划与最终完成态并存时，以 `eventType=plan` + `eventSubType=final_state` 为准的断言

### Implementation for User Story 2

- [ ] T019 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 将 plan 完成态投影为独立最终事件，并保留 stepStatus 最终生命周期状态
- [ ] T020 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationEventRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationTurnRespVO.java` 确保历史读取优先返回最终计划状态而不是初始计划片段
- [ ] T021 [US2] 在 `ui/src/utils/chatHistory.ts`、`ui/src/pages/Home/index.tsx`、`ui/src/components/Dialogue/index.tsx` 恢复最终计划步骤的完成态渲染，避免历史重开后回退为初始计划组件
- [ ] T022 [US2] 按 `specs/005-fix-history-replay/quickstart.md` 场景 B 执行验收，记录 plan 完成态重开一致性结果

**Checkpoint**: US2 完成后，历史详情中的计划组件与对话结束时保持一致，不再发生完成态回退

---

## Phase 5: User Story 3 - 可复看最终工作区产物 (Priority: P3)

**Goal**: 历史中的工作区文件和最终产物可再次预览；引用失效时给出明确不可用原因

**Independent Test**: 完成一条会生成文件或报告的会话；重开历史后可正常预览，若资源失效则显示明确 `missingReason`

### Tests for User Story 3

- [ ] T023 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryArtifactTest.java` 覆盖 `artifactRefs` 预览成功、引用失效返回 `missingReason`、详情接口不暴露通用 `Failed to fetch` 的断言

### Implementation for User Story 3

- [ ] T024 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ArtifactReferenceRespVO.java` 将 workspace/file/html/markdown 最终产物统一映射为 canonical `artifactRefs`
- [ ] T025 [US3] 在 `ui/src/services/agentConversation.ts`、`ui/src/utils/chatHistory.ts`、`ui/src/components/ActionView/index.tsx` 把历史产物恢复为稳定预览数据模型并透传缺失原因
- [ ] T026 [US3] 在 `ui/src/components/ActionView/FilePreview.tsx` 支持历史资源预览与显式缺失态提示，替换通用 `Failed to fetch`
- [ ] T027 [US3] 按 `specs/005-fix-history-replay/quickstart.md` 场景 C、D 执行验收，记录文件预览成功率与缺失态提示结果

**Checkpoint**: US3 完成后，历史工作区结果可复用；资源缺失时也有明确用户可理解的反馈

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 完成全量回归、跨模式验证和遗留过程回放分支清理

- [ ] T028 [P] 按 `specs/005-fix-history-replay/quickstart.md` 执行 `mvn -pl ai-agent-station-study-app -DskipTests=false -Dtest=ConversationHistoryPersistenceTest,ConversationHistoryDetailApiTest,ConversationHistoryArtifactTest test`，并回写结果到 `specs/005-fix-history-replay/quickstart.md`
- [ ] T029 [P] 按 `specs/005-fix-history-replay/quickstart.md` 执行 `mvn -pl ai-agent-station-study-domain,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests=false test`，确认最终态改造未破坏跨模块主链路
- [ ] T030 [P] 按 `ui/package.json` 与 `specs/005-fix-history-replay/quickstart.md` 执行 `npm run lint`、`npm run build`，确认历史恢复与文件预览前端构建通过
- [ ] T031 按 `specs/005-fix-history-replay/quickstart.md` 场景 E 完成 `REACT` 历史详情回归，并清理 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java`、`ui/src/utils/chatHistory.ts` 中遗留的过程回放分支与注释

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: 无依赖，可立即开始
- **Phase 2: Foundational**: 依赖 Phase 1 完成，且阻塞全部用户故事
- **Phase 3: US1**: 依赖 Phase 2 完成；这是 MVP，建议最先交付
- **Phase 4: US2**: 依赖 Phase 2 和 US1 的最终态事件链路
- **Phase 5: US3**: 依赖 Phase 2 和 US1 的详情恢复链路
- **Phase 6: Polish**: 依赖已选用户故事全部完成

### User Story Dependencies

- **US1 (P1)**: 无需依赖其他用户故事，是本次最终态历史模型的最小闭环
- **US2 (P2)**: 复用 US1 的最终态事件持久化与详情读取链路
- **US3 (P3)**: 复用 US1 的最终态详情恢复链路和 `artifactRefs` 归一化能力

### Within Each User Story

- 先补测试，再修改领域投影与持久化，再修改 trigger/UI 读取链路，最后执行独立验收
- 任何涉及 `artifactRefs`、plan 完成态、事件排序的变更，都必须通过对应故事的独立测试与手工场景

### Suggested Execution Order

1. 完成 Phase 1 和 Phase 2，建立新三表模型与最终态事件基础能力
2. 完成 US1，先把“历史重开仍显示最终细节”做成可演示 MVP
3. 完成 US2，修复 plan 完成态回退
4. 完成 US3，修复历史工作区预览与缺失态提示
5. 完成 Phase 6 的全量回归与跨模式验证

---

## Parallel Execution Examples

### User Story 1

```bash
Task: T010 在 ConversationHistoryPersistenceTest.java 覆盖最终细节持久化断言
Task: T011 在 ConversationHistoryDetailApiTest.java 覆盖历史详情最终态断言
```

### User Story 2

```bash
Task: T018 在 ConversationHistoryDetailApiTest.java 覆盖 final_state plan 断言
Task: T021 在 ui/src/utils/chatHistory.ts、ui/src/pages/Home/index.tsx、ui/src/components/Dialogue/index.tsx 恢复完成态渲染
```

### User Story 3

```bash
Task: T023 在 ConversationHistoryArtifactTest.java 覆盖 artifact 缺失态断言
Task: T025 在 ui/src/services/agentConversation.ts、ui/src/utils/chatHistory.ts、ui/src/components/ActionView/index.tsx 适配稳定预览数据模型
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: 对齐契约与测试夹具
2. 完成 Phase 2: 收敛三表与最终态事件基础能力
3. 完成 Phase 3: 让历史详情稳定读取最终态细节
4. 停在 US1 验证 `PLAN_SOLVE` 历史重开与最终结果一致

### Incremental Delivery

1. 先交付 US1，保证历史“最终态一致性”
2. 再交付 US2，保证 plan 组件不会回退
3. 最后交付 US3，保证工作区产物可复看且缺失态可解释
4. 每个阶段完成后都按 `specs/005-fix-history-replay/quickstart.md` 执行对应手工场景

### Parallel Team Strategy

1. 一名开发先完成 Phase 1 和 Phase 2
2. Foundation 稳定后：
   - 开发 A 负责 US1 的领域/trigger 链路
   - 开发 B 负责 US2 的 plan 最终态渲染
   - 开发 C 负责 US3 的 artifact 预览与缺失态体验
3. 最后统一执行 Phase 6 回归

---

## Notes

- 所有 `events[]` 都应被视为最终态细节事件，不能重新回退为过程回放语义
- `ai_agent_message_event` 必须保持“一条最终可见细节一条记录”
- `payload.artifactRefs[]` 是文件引用唯一 canonical 表达，`fileInfo` 只能在兼容层派生
- 不为旧历史数据做双路径兼容；切换前按 `specs/005-fix-history-replay/quickstart.md` 清理旧数据
- 任务描述已尽量压到真实文件路径，实施时不要再扩散到无关模块
