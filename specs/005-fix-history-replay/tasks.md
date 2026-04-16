# Tasks: 对话历史最终态重构与一致性修复

**Input**: Design documents from `/specs/005-fix-history-replay/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`

**Tests**: 已补齐自动化回归资产，但按当前用户要求，本轮执行以“直接改好代码 + 用户手工验收”为主，不把运行测试命令作为当前必做项。  

**Organization**: 任务按用户故事组织，确保每个故事都能独立完成，并且严格围绕“历史只展示对话结束时最终仍可见的 1:1 细节态”推进。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（不同文件、无未完成前置依赖）
- **[Story]**: 用户故事标签（`[US1]`、`[US2]`、`[US3]`）
- 所有任务都显式列出真实文件路径

## Phase 1: Setup (Shared Inputs & Regression Assets)

**Purpose**: 固化最终态契约、验收口径与已准备好的回归资产

- [x] T001 对齐 `specs/005-fix-history-replay/contracts/conversation-history-api.md`、`specs/005-fix-history-replay/contracts/final-detail-event-payload.md`、`specs/005-fix-history-replay/quickstart.md`，冻结最终态接口、payload 与手工验收口径
- [x] T002 [P] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java` 和 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java` 准备最终细节事件、多条同类搜索结果与历史重开夹具
- [x] T003 [P] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryArtifactTest.java` 准备 `artifactRefs` 成功预览与缺失态夹具

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 收敛三张历史表、最终态事件语义与详情装配基础能力

**⚠️ CRITICAL**: 本阶段完成前，禁止开始 US1/US2/US3 的业务修复

- [x] T004 调整 `ai-agent-station-study-app/src/main/resources/db/schema.sql`，收敛 `ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event` 的字段职责、默认值、约束与冗余索引
- [x] T005 [P] 同步 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentConversation.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentMessage.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentMessageEvent.java` 的字段定义与中文注释
- [x] T006 [P] 同步 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentConversationDao.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentMessageDao.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentMessageEventDao.java` 与 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_conversation_mapper.xml`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_mapper.xml`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_event_mapper.xml` 的持久化映射
- [x] T007 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java` 定义最终态事件 payload、`artifactRefs` 与缺失态字段的 canonical 归一化规则
- [x] T008 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageEventServiceImpl.java` 建立按 message 覆盖写入最终态细节事件的一次性持久化入口
- [x] T009 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java` 收敛历史详情读取为“只装配最终态事件”

**Checkpoint**: 三表结构、DAO/Mapper、最终态 payload 归一化和历史详情装配基础能力已就绪

---

## Phase 3: User Story 1 - 延迟回看仍能看到同一份最终细节 (Priority: P1) 🎯 MVP

**Goal**: 用户几天后重新打开深度思考历史时，仍能看到与对话结束时一致的思考过程、任务分组、工具调用、搜索/总结卡片和最终答案

**Independent Test**: 完成一条 `PLAN_SOLVE` 会话，记录对话结束时最终细节块的数量、顺序与主要文案；刷新或延迟重开后，历史详情必须恢复同样的最终细节块集合，而不是退化成少量摘要

### Regression Assets for User Story 1

- [x] T010 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java` 覆盖“一条最终可见细节对应一条 `ai_agent_message_event` 记录”与多条同类型 `deep_search` 不折叠的断言
- [x] T011 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java` 覆盖历史重开后仍返回最终答案、最终细节数量/顺序一致，且不回放 `agent_stream` 过程片段的断言

### Implementation for User Story 1

- [x] T012 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 将运行时思考、搜索、工具缓冲投影为最终可见细节集合，并在消息完成时一次性写入 `ai_agent_message_event`
- [x] T013 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java` 保持历史重开时按 `seq_no` 返回最终细节，不合并多条同类结果项
- [x] T014 [US1] 在 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationController.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationDetailRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationTurnRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationEventRespVO.java` 输出最终态 `turns[].events[]` 契约并兼容恒定 `isFinal=1`
- [x] T015 [US1] 在 `ui/src/services/agentConversation.ts`、`ui/src/utils/chatHistory.ts`、`ui/src/hooks/useAgentConversation.ts` 按最终态事件恢复历史详情，保留多个同类型最终细节项
- [x] T016 [US1] 在 `ui/src/pages/Home/index.tsx`、`ui/src/components/Dialogue/index.tsx`、`ui/src/utils/chatHistory.ts` 清理对过程回放摘要的依赖，确保历史重开只消费最终态思考块、任务块、工具块与搜索/总结卡片
- [ ] T017 [US1] 由用户按 `specs/005-fix-history-replay/quickstart.md` 场景 A 手工验收 `PLAN_SOLVE` 的最终细节数量、顺序与主要文案恢复结果

**Checkpoint**: US1 完成后，历史详情已能稳定复现对话结束时的最终细节，是可演示的 MVP

---

## Phase 4: User Story 2 - 正确展示最终计划状态 (Priority: P2)

**Goal**: 已完成的计划步骤在历史重开后仍显示为已完成，不回退为初始计划组件

**Independent Test**: 完成一条包含多个计划步骤完成态的深度思考会话；刷新并重开历史后，所有已完成步骤继续显示 `completed`

### Regression Assets for User Story 2

- [x] T018 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryDetailApiTest.java` 覆盖初始计划与最终完成态并存时，以 `eventType=plan` + `eventSubType=final_state` 为准的断言

### Implementation for User Story 2

- [x] T019 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 将 plan 完成态投影为独立最终事件，并保留 `stepStatus` 最终生命周期状态
- [x] T020 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationEventRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationTurnRespVO.java` 确保历史读取优先返回最终计划状态而不是初始计划片段
- [x] T021 [US2] 在 `ui/src/utils/chatHistory.ts`、`ui/src/pages/Home/index.tsx`、`ui/src/components/Dialogue/index.tsx` 恢复最终计划步骤的完成态渲染，避免历史重开后回退为初始计划组件或单条计划摘要
- [ ] T022 [US2] 由用户按 `specs/005-fix-history-replay/quickstart.md` 场景 B 手工验收 plan 完成态重开一致性

**Checkpoint**: US2 完成后，历史详情中的计划组件与对话结束时保持一致，不再发生完成态回退

---

## Phase 5: User Story 3 - 可复看最终工作区产物 (Priority: P3)

**Goal**: 历史中的工作区文件和最终产物可再次预览；引用失效时给出明确不可用原因

**Independent Test**: 完成一条会生成文件或报告的会话；重开历史后可正常预览，若资源失效则显示明确 `missingReason`

### Regression Assets for User Story 3

- [x] T023 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryArtifactTest.java` 覆盖 `artifactRefs` 预览成功、引用失效返回 `missingReason`、详情接口不暴露通用 `Failed to fetch` 的断言

### Implementation for User Story 3

- [x] T024 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ArtifactReferenceRespVO.java` 将 workspace/file/html/markdown 最终产物统一映射为 canonical `artifactRefs`
- [x] T025 [US3] 在 `ui/src/services/agentConversation.ts`、`ui/src/utils/chatHistory.ts`、`ui/src/components/ActionView/index.tsx` 把历史产物恢复为稳定预览数据模型，并把 `missingReason` 透传到工作区入口与时间线入口
- [x] T026 [US3] 在 `ui/src/components/ActionView/FilePreview.tsx`、`ui/src/components/ActionPanel/FileRenderer.tsx`、`ui/src/components/ActionPanel/HTMLRenderer.tsx`、`ui/src/components/ActionPanel/TableRenderer.tsx` 支持历史资源预览与显式缺失态提示，替换通用 `Failed to fetch`
- [ ] T027 [US3] 由用户按 `specs/005-fix-history-replay/quickstart.md` 场景 C、D 手工验收历史文件预览成功率与缺失态提示结果

**Checkpoint**: US3 完成后，历史工作区结果可复看；资源缺失时也有明确用户可理解的反馈

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 清理跨模式遗留回放分支，并补齐最终用户手工回归路径

- [ ] T028 在 `ui/src/utils/chatHistory.ts`、`ui/src/pages/Home/index.tsx`、`ui/src/components/Dialogue/index.tsx` 收口 `PLAN_SOLVE`、`REACT`、`CHAT` 三种历史恢复分支，移除仍依赖过程回放摘要的弱兼容逻辑
- [ ] T029 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java` 和 `ui/src/utils/chatHistory.ts` 清理遗留过程回放注释与分支，固定“只读取最终态事件”的语义边界
- [ ] T030 由用户按 `specs/005-fix-history-replay/quickstart.md` 场景 E、F 手工回归 `REACT` 与普通 `CHAT`，确认结构化最终态不退化、轻量聊天不受影响

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

- 先确认共享事件语义与接口输入，再修改 `domain/trigger/ui` 的最终态消费链路
- 优先完成用户真正可见的历史恢复问题，再做跨模式分支清理
- 每个故事完成后，由用户按 `quickstart.md` 对应场景手工验收

### Suggested Execution Order

1. 先完成 US1，修复历史只剩摘要、看不到完整思考与工具细节的问题
2. 再完成 US2，修复 plan 组件重开后回退为初始状态的问题
3. 最后完成 US3，修复历史工作区预览和缺失态提示
4. 完成 Phase 6，统一清理 `PLAN_SOLVE` / `REACT` / `CHAT` 的历史恢复分支

---

## Parallel Execution Examples

### User Story 1

```bash
Task: T016 在 ui/src/pages/Home/index.tsx、ui/src/components/Dialogue/index.tsx、ui/src/utils/chatHistory.ts 收口最终态时间线恢复
Task: T025 在 ui/src/services/agentConversation.ts、ui/src/utils/chatHistory.ts、ui/src/components/ActionView/index.tsx 适配稳定预览数据模型
```

说明：两项都依赖最终态事件契约，但共享 `ui/src/utils/chatHistory.ts`，实际执行时应串行处理该文件，避免冲突。

### User Story 2

```bash
Task: T021 在 ui/src/utils/chatHistory.ts、ui/src/pages/Home/index.tsx、ui/src/components/Dialogue/index.tsx 恢复最终 plan 完成态
Task: T026 在 ui/src/components/ActionView/FilePreview.tsx、ui/src/components/ActionPanel/*.tsx 修复历史预览缺失态
```

说明：这两组任务文件集合基本分离，适合在多人协作时并行推进。

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 保持 Phase 1 和 Phase 2 作为既有前置成果
2. 完成 T016，确保历史重开只消费最终态细节
3. 由用户完成 T017 手工验收
4. 若 US1 验收通过，再继续 US2 与 US3

### Incremental Delivery

1. 先交付 US1，解决“历史只剩摘要”的核心问题
2. 再交付 US2，解决 plan 完成态回退
3. 最后交付 US3，解决工作区文件预览与缺失态提示
4. 末尾再做 Phase 6 的跨模式收口，避免返工

### Parallel Team Strategy

1. 一名开发先完成 T016/T021，统一收口时间线与 plan 的最终态恢复
2. 另一名开发在此期间处理 T025/T026，收口工作区预览模型与缺失态 UI
3. 所有代码改动完成后，由用户按 T017/T022/T027/T030 执行手工验收

---

## Notes

- 所有 `events[]` 都应被视为最终态细节事件，不能再回退为过程回放语义
- `ai_agent_message_event` 必须保持“一条最终可见细节一条记录”
- `payload.artifactRefs[]` 是文件引用唯一 canonical 表达，`fileInfo` 只能在兼容层派生
- 同一份同时出现在时间线与工作区的内容，只允许存在一条 canonical 事件记录
- 不为旧历史数据做双路径兼容；切换前按 `specs/005-fix-history-replay/quickstart.md` 清理旧数据
- 当前执行策略不要求先跑自动化测试，后续验收以用户按 `quickstart.md` 的手工场景为准
