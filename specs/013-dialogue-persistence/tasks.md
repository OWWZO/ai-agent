# Tasks: 对话执行持久化账本

**Input**: Design documents from `/specs/013-dialogue-persistence/`  
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: 本特性在 [plan.md](./plan.md) 的 Verification Plan 中明确要求 DAO/Mapper、查询服务、ReAct/PlanSolve 集成和 fail-open 验证，因此任务清单包含目标测试与手工验收任务。  
**Organization**: 任务按用户故事组织，先建立共享账本基础能力，再按 P1 → P2 → P3 逐步交付，确保每个故事都可以独立实现、独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可以并行执行（不同文件、无未完成前置依赖）
- **[Story]**: 仅用于用户故事阶段，表示任务归属的故事
- 每个任务都显式列出真实文件路径，便于直接执行

## Phase 1: Setup (Shared Ledger Skeletons)

**Purpose**: 为执行账本建立 DDL、领域模型、Mapper 与测试骨架

- [X] T001 在 `ai-agent-station-study-app/src/main/resources/db/schema.sql` 中加入 `ai_agent_dialogue_run`、`ai_agent_llm_invocation`、`ai_agent_tool_invocation`、`ai_agent_artifact` 的 DDL 区块占位、索引注释和中文说明
- [X] T002 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueRun.java`、`LlmInvocation.java`、`ToolInvocation.java`、`ArtifactRecord.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/AgentRunState.java` 建立账本实体和运行态骨架
- [X] T003 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueRunLedgerDao.java`、`ILlmInvocationLedgerDao.java`、`IToolInvocationLedgerDao.java`、`IArtifactLedgerDao.java` 以及 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml`、`llm_invocation_ledger_mapper.xml`、`tool_invocation_ledger_mapper.xml`、`artifact_ledger_mapper.xml` 建立 DAO 与 Mapper XML 骨架
- [X] T004 [P] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutionLedgerRepositoryTest.java`、`ExecutionLedgerQueryServiceTest.java`、`ReactExecutionLedgerIntegrationTest.java`、`PlanSolveExecutionLedgerIntegrationTest.java` 与 `ExecutionLedgerFixtureFactory.java` 建立测试类骨架和样本工厂

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 完成所有用户故事都会复用的账本模型、写入契约、查询契约和运行态装配

**⚠️ CRITICAL**: 本阶段完成前，不开始任何用户故事实现

- [X] T005 在 `ai-agent-station-study-app/src/main/resources/db/schema.sql`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueRun.java`、`LlmInvocation.java`、`ToolInvocation.java`、`ArtifactRecord.java` 中实现完整字段、唯一键、索引、状态值和中文注释，对齐 `specs/013-dialogue-persistence/data-model.md`
- [X] T006 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/DialogueRunStartRecord.java`、`DialogueRunFinishRecord.java`、`LlmInvocationStartRecord.java`、`LlmInvocationFinishRecord.java`、`ToolInvocationBatchStartRecord.java`、`ToolInvocationFinishRecord.java`、`ArtifactRecordCommand.java` 定义 recorder 命令模型
- [X] T007 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/AgentExecutionRecorder.java`、`ExecutionLedgerQueryService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/ExecutionRunDetail.java`、`DialogueRunView.java`、`LlmInvocationView.java`、`ToolInvocationView.java`、`ArtifactView.java` 收口内部写入/查询契约
- [X] T008 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java`、`ExecutionLedgerQueryServiceImpl.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/AgentContext.java` 搭建共享运行态、fail-open 包装和基础查询装配
- [X] T009 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java`、`AgentExecutionLedgerRepositoryTest.java`、`ExecutionLedgerQueryServiceTest.java` 中建立共享断言、SQL 夹具和最小回归闭环

**Checkpoint**: 新账本表结构、领域契约、查询视图和共享运行态都已就绪，用户故事可以开始实现

---

## Phase 3: User Story 1 - 排查单次执行全链路 (Priority: P1) 🎯 MVP

**Goal**: 让研发/运维可以按 `requestId` 直接查看一次执行的 run 总账、LLM 顺序、工具顺序、产物列表和最终状态  
**Independent Test**: 构造一条同时包含多次模型调用、至少一次工具调用和至少一个产物的 ReAct/PlanSolve 样本，按 `requestId` 查询后能一次性返回完整执行链路，并在失败样本里保留终态和最后已记录关键步骤

### Tests for User Story 1

- [X] T010 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutionLedgerRepositoryTest.java` 先补写 run / llm / tool / artifact 的前插后更、终态回写和 `request_id` 唯一键失败用例
- [X] T011 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java` 与 `PlanSolveExecutionLedgerIntegrationTest.java` 先补写按 `requestId` 查询完整链路、失败保留最后步骤和至少一个产物的集成用例

### Implementation for User Story 1

- [X] T012 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/step/RootNode.java`、`SummaryResultNode.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/step/Step1SopRecallAndPrepareNode.java`、`Step2PlanExecuteNode.java` 接入 run 创建、输入产物登记和 run 收口
- [X] T013 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/llm/LLM.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/llm/LlmChatResponseMapper.java` 记录 `ask/askTool` 的 invocation 顺序、完整文本、toolCallCount、token 和 finishReason
- [X] T014 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueRunLedgerDao.java`、`ILlmInvocationLedgerDao.java`、`IToolInvocationLedgerDao.java`、`IArtifactLedgerDao.java` 实现按 `requestId` 聚合 run 明细查询
- [X] T015 [US1] 在 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml`、`llm_invocation_ledger_mapper.xml`、`tool_invocation_ledger_mapper.xml`、`artifact_ledger_mapper.xml` 完成 run detail 所需的插入、更新和关联查询 SQL
- [X] T016 [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java`、`PlanSolveExecutionLedgerIntegrationTest.java` 与 `specs/013-dialogue-persistence/quickstart.md` 第 4、6 节完成单次执行链路回归与 SQL 验收

**Checkpoint**: US1 完成后，可以按单次请求直接重建完整执行链路，这是本特性的 MVP

---

## Phase 4: User Story 2 - 分析工具近期行为与稳定性 (Priority: P2)

**Goal**: 让工具负责人可以按 `toolName` 查看近期调用、输入输出、状态、耗时，并区分模型原始分发顺序和真实执行时间线  
**Independent Test**: 为同一工具构造成功、失败和并发分发样本，按 `toolName` 查询最近调用后，能看到 `dispatchIndex`、startedAt、状态、耗时和原始输入输出

### Tests for User Story 2

- [X] T017 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java` 先补写按 `toolName` 查询最近 100 条、`createTime DESC` 排序和 `artifactCount` 聚合用例
- [X] T018 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java` 先补写同批并发 tool call 保留 `dispatch_index`、startedAt 时间线、工具失败终态和 fail-open 不阻断主流程的用例

### Implementation for User Story 2

- [X] T019 [P] [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/AgentRunState.java` 实现 tool 主线程预登记、`toolCallId -> toolInvocationId` 映射和并发回写边界
- [X] T020 [P] [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java`、`ExecutionLedgerQueryServiceImpl.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IToolInvocationLedgerDao.java` 实现近期工具查询、耗时聚合和失败状态回写
- [X] T021 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/llm/LLM.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java` 补齐账本失败的错误日志、失败计数和成功率指标
- [X] T022 [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java`、`PlanSolveExecutionLedgerIntegrationTest.java` 与 `specs/013-dialogue-persistence/quickstart.md` 第 5、7 节完成工具治理查询和 fail-open 验收

**Checkpoint**: US2 完成后，可以直接按工具观察近期行为与稳定性，不再依赖原始执行日志

---

## Phase 5: User Story 3 - 追踪输入文件与工具产物归属 (Priority: P3)

**Goal**: 让支持人员可以区分输入文件和输出文件，确认输出文件来自哪次工具调用，并保证同一稳定文件不会重复落账  
**Independent Test**: 构造同时包含用户上传文件和工具输出文件的执行样本，按 run 和 session 查询后，能看到输入文件归属 run、输出文件归属 tool invocation，且重复登记同一稳定文件不会产生重复记录

### Tests for User Story 3

- [X] T023 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutionLedgerRepositoryTest.java` 先补写 `run_id + tool_call_id + storage_key` 去重、输入文件 `toolInvocationId` 为空和无产物工具不生成伪记录的用例
- [X] T024 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolArtifactBindingRuntimeTest.java` 与 `ExecutionLedgerQueryServiceTest.java` 先补写 `ToolArtifactRegistry` 产物绑定映射到账本、可见/内部文件区分和按 `sessionId` 查询最近 runs/产物摘要的用例

### Implementation for User Story 3

- [X] T025 [P] [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/step/RootNode.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/step/Step1SopRecallAndPrepareNode.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/ArtifactRecordCommand.java` 实现 `AgentRequest.sessionFiles` 到输入产物记录的转换
- [X] T026 [P] [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/AgentContext.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java` 复用产物绑定结果写出输出文件归属、`visibility/sourceType/sourceName` 语义和去重策略
- [X] T027 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueRunLedgerDao.java`、`IArtifactLedgerDao.java`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml`、`artifact_ledger_mapper.xml` 实现按 `sessionId` 查询最近 runs 与产物摘要
- [X] T028 [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutionLedgerRepositoryTest.java`、`ToolArtifactBindingRuntimeTest.java`、`ExecutionLedgerQueryServiceTest.java` 与 `specs/013-dialogue-persistence/quickstart.md` 第 6、8 节完成文件归属、去重和 session 查询验收

**Checkpoint**: US3 完成后，输入文件与工具产物的来源关系可直接追溯，且稳定文件不会重复落账

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 完成文档回填、注释收口和最终回归

- [X] T029 [P] 在 `specs/013-dialogue-persistence/contracts/execution-ledger-recorder.md`、`specs/013-dialogue-persistence/contracts/execution-ledger-query.md`、`specs/013-dialogue-persistence/quickstart.md` 回填最终实现细节、SQL 示例和指标字段
- [X] T030 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/AgentContext.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/llm/LLM.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java` 补齐中文注释、边界日志和异常上下文字段
- [X] T031 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolArtifactBindingRuntimeTest.java`、`AgentExecutionLedgerRepositoryTest.java`、`ExecutionLedgerQueryServiceTest.java`、`ReactExecutionLedgerIntegrationTest.java`、`PlanSolveExecutionLedgerIntegrationTest.java` 执行 `mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=ToolArtifactBindingRuntimeTest,AgentExecutionLedgerRepositoryTest,ExecutionLedgerQueryServiceTest,ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest`，并按 `specs/013-dialogue-persistence/quickstart.md` 完成 ReAct / PlanSolve / fail-open / SQL 抽查最终验收

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: 无依赖，可立即开始
- **Phase 2: Foundational**: 依赖 Setup 完成，且阻塞全部用户故事
- **Phase 3: US1**: 依赖 Foundational 完成；这是 MVP，建议最先交付
- **Phase 4: US2**: 依赖 US1 已经把 run / llm / tool 写链路接入主执行路径
- **Phase 5: US3**: 依赖 US1 已经打通 run / artifact 主链路；完成后可与 US2 并行收口
- **Phase 6: Polish**: 依赖所有目标用户故事完成

### User Story Dependencies

- **US1 (P1)**: 只依赖 Foundational，是本特性的第一个可交付增量
- **US2 (P2)**: 依赖 US1 的账本主路径和查询视图，但可以独立验证工具治理诉求
- **US3 (P3)**: 依赖 US1 的 run / tool / artifact 基础写入，但不依赖 US2 的近期工具查询能力

### Within Each User Story

- 先补测试，再改实现
- 先完成写入/查询核心能力，再做集成回归和 quickstart 验收
- 每个故事完成后都要执行该故事的独立验证任务

### Parallel Opportunities

- Setup 中的 `T002`、`T003`、`T004` 可并行
- Foundational 中的 `T006`、`T007`、`T008` 可并行
- US1 中的 `T010`、`T011` 可并行；`T012`、`T013` 可并行
- US2 中的 `T017`、`T018` 可并行；`T019`、`T020` 可并行
- US3 中的 `T023`、`T024` 可并行；`T025`、`T026` 可并行
- Polish 中的 `T029`、`T030` 可并行

---

## Parallel Example: User Story 1

```text
T010 [US1] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutionLedgerRepositoryTest.java
T011 [US1] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java

T012 [US1] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/step/RootNode.java
T013 [US1] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/llm/LLM.java
```

## Parallel Example: User Story 2

```text
T017 [US2] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java
T018 [US2] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java

T019 [US2] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java
T020 [US2] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java
```

## Parallel Example: User Story 3

```text
T023 [US3] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutionLedgerRepositoryTest.java
T024 [US3] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolArtifactBindingRuntimeTest.java

T025 [US3] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/step/Step1SopRecallAndPrepareNode.java
T026 [US3] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/AgentContext.java
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational
3. 完成 Phase 3: US1
4. 执行 `T016` 的自动化回归与 SQL 验收
5. 在确认单次执行链路可直接查询后再继续

### Incremental Delivery

1. 先完成 Setup + Foundational，打稳账本表、契约和运行态
2. 交付 US1，解决“单次执行无法直接排查”的核心问题
3. 交付 US2，补齐工具治理视角和 fail-open 观测
4. 交付 US3，补齐输入/输出文件来源与去重语义
5. 最后执行 Polish，统一完成文档回填和最终验收

### Parallel Team Strategy

1. 一组先完成 Setup + Foundational
2. US1 完成后：
   - 一组推进 US2 的 tool 查询与并发时序
   - 一组推进 US3 的 artifact 来源与 session 查询
3. 最后共同执行 Polish 和全量回归

---

## Notes

- 所有任务都保持在 Java 主链路和 MyBatis 持久化范围内，本期不改 `ui/`、`reactor-tool/`、`reactor-client/`
- `ToolArtifactRegistry` 继续作为工具产物事实源，禁止在 `SSEPrinter`、`BaseAgentResponseHandler` 或前端展示链路反推账本
- 账本写入必须坚持 fail-open，任何 recorder 异常都要留下日志和指标，但不能直接中断用户主流程
- 复杂逻辑、状态流转和并发顺序处理必须补中文注释
