# Tasks: Agent Legacy Bridge 实质删除与子域再收敛

**Input**: Design documents from `/specs/020-prune-agent-bridges/`  
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: 本特性在 [spec.md](./spec.md) 的独立验收、[plan.md](./plan.md) 的 Verification Plan 与 [quickstart.md](./quickstart.md) 中明确要求目录扫描、聚焦编译与聚焦回归，因此任务清单包含对应验证任务，但不采用 TDD 流程。  
**Organization**: 任务按用户故事组织，先建立 legacy bridge 分类守卫与稳定 seam 骨架，再按 P1 → P2 → P3 完成 bridge 删除、子域再收敛与最终守卫锁定，保证每个故事都能独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可以并行执行（不同文件、没有未完成前置依赖）
- **[Story]**: 仅用于用户故事阶段，表示任务归属的故事
- 每个任务都显式列出真实文件路径，便于直接执行

## Phase 1: Setup (Shared Bridge-Convergence Scaffolding)

**Purpose**: 为 bridge 删除与 legacy 包分类治理建立共享扫描骨架与职责说明

- [X] T001 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java` 建立 must-delete bridge、deferred legacy contract、stable ownership root 三类分类常量与扫描辅助方法
- [X] T002 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/package-info.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/rag/package-info.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/package-info.java` 补充本轮 legacy bridge 删除与允许延期契约的中文职责说明
- [X] T003 [P] 在 `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/query/IGptQueryApplicationService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/query/GptQueryApplicationService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/dataquery/IDataAgentApplicationService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/dataquery/DataAgentApplicationService.java` 标注稳定 seam 目标与 bridge 禁止依赖边界

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 完成所有用户故事都会复用的入口拓扑守卫、allowlist 守卫与装配边界基线

**⚠️ CRITICAL**: 本阶段完成前，不开始任何用户故事实现

- [X] T004 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorHttpControllerTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/DataAgentCapabilityDegradeTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentImageGenerationControllerTest.java` 预留入口层不得依赖 legacy bridge 的断言骨架
- [X] T005 [P] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerBoundaryTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorBeanTopologyTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SpringRuntimeBoundaryTest.java` 建立 legacy model/config allowlist 与禁止扩张的基础断言
- [X] T006 [P] 在 `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/AiAgentAutoConfiguration.java`、`ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/ReactorRuntimeAutoConfiguration.java`、`ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/AgentHandlerAutoConfiguration.java`、`ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/DataAgentInitRunner.java` 梳理当前装配边界并标明本轮允许延期的历史契约

**Checkpoint**: bridge 分类守卫、入口拓扑守卫和装配基线都已就绪，用户故事可以继续推进

---

## Phase 3: User Story 1 - 开发者可以沿唯一主链路维护 legacy query 与 dataagent 能力 (Priority: P1) 🎯 MVP

**Goal**: 删除 GPT query / multi-agent / dataagent 过渡 bridge，并让 case 入口改为只依赖稳定领域 seam  
**Independent Test**: 扫描 `case/trigger/app/infrastructure/domain` 对 `IGptProcessService`、`IMultiAgentService`、`DataAgentService`、`Nl2SqlService` 的生产级依赖，并执行 `AgentContextConvergenceBoundaryTest`、`ReactorHttpControllerTest`、`DataAgentCapabilityDegradeTest`、`SessionContextMemoryIntegrationTest`；确认入口仍可用且不再经由旧 bridge

### Verification for User Story 1

- [X] T007 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java` 与 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorHttpControllerTest.java` 扩充 `IGptProcessService`、`IMultiAgentService`、`DataAgentService`、`Nl2SqlService` 的零生产依赖断言
- [X] T008 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/DataAgentCapabilityDegradeTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryIntegrationTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/MultiAgentServiceImplTest.java` 校准 bridge 删除后的主链路回归样本

### Implementation for User Story 1

- [X] T009 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/` 建立 GPT query / multi-agent 稳定领域 seam，并删除 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IGptProcessService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IMultiAgentService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/GptProcessServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/MultiAgentServiceImpl.java`
- [X] T010 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/rag/`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/adapter/port/` 拆分稳定 dataagent seam，并删除 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/DataAgentService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/Nl2SqlService.java`
- [X] T011 [US1] 在 `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/query/IGptQueryApplicationService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/query/GptQueryApplicationService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/dataquery/IDataAgentApplicationService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/dataquery/DataAgentApplicationService.java` 改为只依赖新稳定 seam
- [X] T012 [US1] 在 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/AiAgentController.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/reactor/ReactorController.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/dataagent/DataAgentController.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationHistoryController.java` 调整入口依赖，移除对已删 bridge 的残余耦合
- [X] T013 [US1] 在 `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/DataAgentInitRunner.java`、`ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/AgentHandlerAutoConfiguration.java`、`ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/ReactorRuntimeAutoConfiguration.java` 收口 bridge 删除后的装配与 Bean 拓扑
- [X] T014 [US1] 在 `specs/020-prune-agent-bridges/quickstart.md` 指定的 US1 命令下执行扫描、编译与聚焦回归，并记录旧 bridge 清理结果

**Checkpoint**: User Story 1 完成后，GPT query / multi-agent / dataagent 主链路已不再依赖 legacy bridge，这是本特性的 MVP

---

## Phase 4: User Story 2 - 维护者可以按稳定子域理解剩余 legacy 模型与配置归属 (Priority: P2)

**Goal**: 让 residual model/config/imagegeneration/execute/armory 语义归入稳定子域或稳定契约，不再默认挂在旧 `reactor/service` 总树  
**Independent Test**: 对 `domain/agent`、`app/config`、`trigger`、`infrastructure` 做目录审计和聚焦回归，并执行 `ExecutionLedgerBoundaryTest`、`ReplayProjectorBeanTopologyTest`、`AgentImageGenerationControllerTest`、`WorkspaceImageGenerationServiceTest`；确认 legacy model/config 都有明确主归属

### Verification for User Story 2

- [X] T015 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerBoundaryTest.java` 与 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorBeanTopologyTest.java` 增加 legacy model/config/imagegeneration 稳定归属断言
- [X] T016 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java` 与 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SpringRuntimeBoundaryTest.java` 增加 `reactor/model`、`reactor/config/data`、`service/execute`、`service/armory` 的 allowlist / no-expansion 断言

### Implementation for User Story 2

- [X] T017 [P] [US2] 将 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/req/`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/response/`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/multi/`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/dto/` 重新分类为稳定请求/响应契约；本轮通过 `package-info`、allowlist 守卫与主链路引用审计锁定历史包名只作为稳定历史契约存在
- [X] T018 [P] [US2] 将 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/imagegeneration/`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IWorkspaceImageGenerationService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/imagegeneration/**` 重新分类为工作台生图稳定历史契约，并同步校准 `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/reactor/service/impl/WorkspaceImageGenerationServiceImpl.java`、`ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/gateway/ReactorImageGenerationGateway.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentImageGenerationController.java` 的依赖边界
- [X] T019 [P] [US2] 将 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/data/**` 连同 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ChatModelInfoService.java`、`ChatModelSchemaService.java`、`ColumnValueSyncService.java`、`EmbeddingService.java`、`QdrantService.java`、`VectorService.java` 重新分类为稳定 dataagent 配置/元数据契约，并同步锁定 `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/DataAgentInitRunner.java`、`ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/data/Es7HighLevelClientConfig.java`、`ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dataquery/**` 的允许引用面
- [X] T020 [P] [US2] 将 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/runtime/**`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/**`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/**` 明确收敛为 domain 内部稳定策略节点/运行时注册表，并通过 `SpringRuntimeBoundaryTest` 与模块文档限制它们只被 case/domain 主链路消费
- [X] T021 [US2] 在 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/*.xml`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationHistoryDetailRespVO.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/*Replay*.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentResponseHandlerReplayContractTest.java` 核对 residual legacy model 的包引用，只保留已登记的 ledger / replay / VO / 测试契约引用
- [X] T022 [US2] 在 `specs/020-prune-agent-bridges/quickstart.md` 指定的 US2 命令下执行目录审计、编译与聚焦回归，并记录 legacy model/config 归属结果

**Checkpoint**: User Story 2 完成后，剩余 legacy 模型、配置和执行/armory 语义已被明确归类，不再处于半收敛状态

---

## Phase 5: User Story 3 - 交付团队可以用最终守卫锁定“bridge 已删完”的边界状态 (Priority: P3)

**Goal**: 用自动化守卫和模块文档把 must-delete bridge、deferred legacy contract 与 stable ownership root 的最终边界锁定下来  
**Independent Test**: 执行目录扫描、禁止依赖扫描和聚焦回归，并核对根级与模块级文档；确认任何旧 bridge 回流、旧目录扩张或错误主归属都会被发现

### Verification for User Story 3

- [X] T023 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorHttpControllerTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SpringRuntimeBoundaryTest.java` 升级 must-delete / deferred / stable 三类 legacy 状态守卫
- [X] T024 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/DataAgentCapabilityDegradeTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentImageGenerationControllerTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorBeanTopologyTest.java` 校准最终拓扑与 no-bridge 断言

### Implementation for User Story 3

- [X] T025 [P] [US3] 在 `CLAUDE.md`、`ai-agent-station-study-domain/CLAUDE.md`、`ai-agent-station-study-infrastructure/CLAUDE.md`、`ai-agent-station-study-trigger/CLAUDE.md`、`ai-agent-station-study-app/CLAUDE.md` 回填 bridge 删除规则、deferred allowlist 与稳定子域归属说明
- [X] T026 [US3] 在 `specs/020-prune-agent-bridges/quickstart.md`、`specs/020-prune-agent-bridges/contracts/bridge-removal-contract.md`、`specs/020-prune-agent-bridges/contracts/subdomain-ownership-contract.md` 回填最终 seam、最终 allowlist 和禁止扩张规则
- [X] T027 [US3] 在 `specs/020-prune-agent-bridges/quickstart.md` 指定的 US3 命令下执行目录扫描、禁止依赖扫描与聚焦回归，并记录最终守卫结果

**Checkpoint**: User Story 3 完成后，bridge 删除边界、允许延期边界和稳定归属边界都已被测试与文档共同锁定

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 清理残留空壳、压缩重复实现并完成最终全量验收

- [X] T028 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/` 及本轮新增的过渡文件中清理已无意义的 bridge 注释、空壳目录与 orphan 文件
- [X] T029 [P] 在本轮触达的 `ai-agent-station-study-case`、`ai-agent-station-study-domain`、`ai-agent-station-study-infrastructure`、`ai-agent-station-study-trigger`、`ai-agent-station-study-app` 文件中压缩重复导入、重复委派与易混淆中文注释，保持行为不变
- [X] T030 在 `specs/020-prune-agent-bridges/quickstart.md` 指定的最终验收命令下执行全量扫描、全量编译与最终测试，并记录交付结果

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: 无依赖，可立即开始
- **Phase 2: Foundational**: 依赖 Setup 完成，阻塞所有用户故事
- **Phase 3: US1**: 依赖 Foundational 完成；这是 MVP，建议最先交付
- **Phase 4: US2**: 依赖 US1 已删除 legacy query/dataagent bridge，再做 residual model/config 归属收敛
- **Phase 5: US3**: 依赖 US2 已明确残余 legacy 归属，再锁最终守卫与文档
- **Phase 6: Polish**: 依赖所有目标用户故事完成

### User Story Dependencies

- **US1 (P1)**: 只依赖 Foundational，是首个可交付增量
- **US2 (P2)**: 依赖 US1 已删掉主要 bridge，但有独立的目录审计与 imagegeneration / replay 回归
- **US3 (P3)**: 依赖 US2 已明确 stable ownership，再完成最终守卫和文档锁定

### Within Each User Story

- 先补齐该故事需要的扫描守卫和聚焦回归
- 再删除 bridge、迁移模型/配置/步骤节点或更新文档
- 每个故事完成后都要执行该故事自己的扫描、编译与回归命令

### Parallel Opportunities

- Setup 中的 `T002`、`T003` 可并行
- Foundational 中的 `T005`、`T006` 可并行
- US1 中的 `T007`、`T008` 可并行；`T009`、`T010` 可按 query/dataagent 两条线并行
- US2 中的 `T015`、`T016` 可并行；`T017`、`T018`、`T019`、`T020` 可按 model、imagegeneration、data config、execute/armory 四条线并行
- US3 中的 `T023`、`T024` 可并行；`T025`、`T026` 可并行
- Polish 中的 `T028`、`T029` 可并行

---

## Parallel Example: User Story 1

```text
T007 [US1] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java
T008 [US1] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/DataAgentCapabilityDegradeTest.java

T009 [US1] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IGptProcessService.java
T010 [US1] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/DataAgentService.java
```

## Parallel Example: User Story 2

```text
T015 [US2] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerBoundaryTest.java
T016 [US2] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SpringRuntimeBoundaryTest.java

T017 [US2] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/req/
T018 [US2] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/imagegeneration/
T019 [US2] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/data/
T020 [US2] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/
```

## Parallel Example: User Story 3

```text
T023 [US3] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java
T024 [US3] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentImageGenerationControllerTest.java

T025 [US3] CLAUDE.md
T026 [US3] specs/020-prune-agent-bridges/contracts/bridge-removal-contract.md
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational
3. 完成 Phase 3: US1
4. 执行 `T014` 的扫描、编译和聚焦回归
5. 在确认 legacy query/dataagent bridge 已删除后再继续

### Incremental Delivery

1. 先完成 Setup + Foundational，锁定分类守卫和装配基线
2. 交付 US1，完成最核心的 legacy bridge 实质删除
3. 交付 US2，完成 residual model/config/execute/armory 的再收敛
4. 交付 US3，锁定最终守卫与文档边界
5. 最后执行 Polish，清残留空壳并做最终全量验收

### Parallel Team Strategy

1. 一组先完成 Setup + Foundational
2. US1 完成后：
   - 一组推进 GPT query / multi-agent seam 删除
   - 一组推进 dataagent seam 删除
3. US2 开始后：
   - 一组推进 residual model / imagegeneration 契约迁移
   - 一组推进 data config / vector / metadata 归属收敛
   - 一组推进 execute / armory 清理
4. 最后共同完成守卫、文档与最终回归

---

## Notes

- 本特性只改 `ai-agent-station-study-case`、`ai-agent-station-study-domain`、`ai-agent-station-study-infrastructure`、`ai-agent-station-study-trigger`、`ai-agent-station-study-app`，不改 `ui/`、`reactor-tool/`、`reactor-client/`
- 本任务单显式使用“验证任务”而不是 TDD 先测后写流程，符合本轮“不使用 TDD”的要求
- `reactor/model/**`、`reactor/config/data/**` 这类 residual legacy 内容不能再笼统视为 bridge，必须在实现中明确分类为稳定契约或明确延期项
- 任务描述中的目录路径表示“整批迁移或整批审计”的执行边界；实现时仍需保持提交粒度清晰，避免跨故事混改
