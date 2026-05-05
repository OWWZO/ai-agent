# Tasks: Agent 领域边界最终收敛

**Input**: Design documents from `/specs/019-agent-ddd-convergence/`  
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: 本特性在 [spec.md](./spec.md) 的用户故事独立验收、[plan.md](./plan.md) 的 Verification Plan 与 [quickstart.md](./quickstart.md) 中明确要求边界守卫、关键字符串扫描、聚焦编译和回归测试，因此任务清单包含对应验证任务，但不采用 TDD 流程要求。  
**Organization**: 任务按用户故事组织，先建立最终边界守卫与子域骨架，再按 P1 → P2 → P3 完成应用编排收口、领域子域重组和技术依赖下沉，保证每个故事都能独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可以并行执行（不同文件、没有未完成前置依赖）
- **[Story]**: 仅用于用户故事阶段，表示任务归属的故事
- 每个任务都显式列出真实文件路径，便于直接执行

## Phase 1: Setup (Shared Convergence Scaffolding)

**Purpose**: 为最终收敛建立子域骨架、case seam 骨架与边界守卫入口

- [X] T001 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/package-info.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/package-info.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/memory/package-info.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/rag/package-info.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/role/package-info.java` 建立五个子域的中文职责说明与迁移占位
- [X] T002 [P] 在 `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/role/IFixRoleQueryService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/role/FixRoleQueryApplicationService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/rag/IRagApplicationService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/rag/RagApplicationService.java` 建立 Trigger 专用的 case 层 seam 骨架
- [X] T003 [P] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SpringRuntimeBoundaryTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorPersistenceBoundaryTest.java` 预留最终目录扫描、禁止依赖扫描与项目根路径辅助方法

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 完成所有用户故事都会复用的边界守卫、装配约束与验收入口

**⚠️ CRITICAL**: 本阶段完成前，不开始任何用户故事实现

- [X] T004 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SpringRuntimeBoundaryTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorPersistenceBoundaryTest.java` 将旧 `domain/agent/service`、旧 `domain/agent/reactor`、`SseEmitter`、`new OkHttpClient`、`JdbcDataProvider`、`SpringContextHolder`、`applicationContext.getBean(` 纳入统一边界守卫
- [X] T005 [P] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorHttpControllerTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentHandlerAutoConfigurationTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorBeanTopologyTest.java` 固化 controller、handler 与 replay 装配只能经由 case/app 的聚焦回归
- [X] T006 [P] 在 `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/AiAgentAutoConfiguration.java`、`ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/ReactorRuntimeAutoConfiguration.java`、`ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/AgentHandlerAutoConfiguration.java` 清理收敛阶段共享装配的边界注释、无效依赖和 app-owned wiring 说明
- [X] T007 [P] 在 `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/package-info.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/adapter/port/package-info.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/adapter/repository/package-info.java` 补齐 application/domain seam 的中文包说明
- [X] T008 在 `specs/019-agent-ddd-convergence/quickstart.md` 校准 US1、US2、US3 的编译、扫描与回归命令分组，作为后续统一验收入口

**Checkpoint**: 最终边界守卫、case seam 骨架和统一验收入口都已就绪，用户故事可以继续推进

---

## Phase 3: User Story 1 - 开发者可在清晰边界内扩展 Agent 能力 (Priority: P1) 🎯 MVP

**Goal**: 让 Trigger 与 App 只通过 case seam 进入 Agent 编排和角色/RAG 应用服务，旧 `domain/agent/service` 不再承担应用编排主路径  
**Independent Test**: 扫描 `case/trigger/app/domain` 的主链路 import，并执行 `AgentContextConvergenceBoundaryTest`、`ReactorHttpControllerTest`、`SessionContextMemoryIntegrationTest`；确认 dispatch、execute、armory、task、role-library、rag-upload 入口都不再直接依赖 `domain.agent.service` 根接口

### Verification for User Story 1

- [X] T009 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java` 与 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorHttpControllerTest.java` 扩充旧根接口零引用、controller 只依赖 case seam 的断言
- [X] T010 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryIntegrationTest.java` 与 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/FixedAgentExecuteStrategyTest.java` 校准主链路经由 `ai-agent-station-study-case` 执行后的聚焦回归样本

### Implementation for User Story 1

- [X] T011 [P] [US1] 在 `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/role/IFixRoleQueryService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/role/FixRoleQueryApplicationService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/rag/IRagApplicationService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/rag/RagApplicationService.java` 封装角色库查询与知识库上传的应用层入口
- [X] T012 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/IAgentDispatchService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/IExecuteStrategy.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/IArmoryService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/ITaskService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/dispatch/AgentDispatchDispatchService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/ArmoryService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/task/AiAgentTaskService.java` 删除或迁出旧应用编排根接口与实现
- [X] T013 [US1] 在 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/AiAgentController.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/reactor/ReactorController.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/job/AgentTaskJob.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentRoleLibraryController.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/admin/AiClientRagOrderAdminController.java` 改为只注入 case 层服务并移除对 `domain.agent.service.*` 的直接依赖
- [X] T014 [US1] 在 `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/dispatch/AgentDispatchService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/execute/react/ReactAgentExecuteStrategy.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/execute/planexecute/PlanSolveAgentExecuteStrategy.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/execute/workflow/FlowAgentExecuteStrategy.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/stream/AgentSessionPrinter.java` 收口仍停留在 domain/service 的编排、打印桥接和策略选择职责
- [X] T015 [US1] 在 `specs/019-agent-ddd-convergence/quickstart.md` 指定的 US1 命令下执行 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java`、`ReactorHttpControllerTest.java`、`SessionContextMemoryIntegrationTest.java` 的独立回归，并记录 `rg` 扫描旧根接口引用的结果

**Checkpoint**: User Story 1 完成后，应用编排主线已经稳定归属到 case 层，这是本特性的 MVP

---

## Phase 4: User Story 2 - 维护者可按子域理解和演进 Agent 核心能力 (Priority: P2)

**Goal**: 把原先混杂在 `reactor` 总包和 `service/rag|role` 中的核心能力收敛到 `runtime / ledger / memory / rag / role` 唯一主归属  
**Independent Test**: 对 `domain/agent` 做目录审计，并执行 `AgentContextConvergenceBoundaryTest`、`ReactorPersistenceBoundaryTest`、`SessionContextMemoryIntegrationTest`、`ReplayProjectorBeanTopologyTest`；确认 runtime、ledger、memory、rag、role 都有唯一主目录，旧 `reactor/service` 不再承载主逻辑

### Verification for User Story 2

- [X] T016 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java` 与 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorBeanTopologyTest.java` 增加 `runtime / ledger / memory / rag / role` 唯一主归属和旧 `reactor/service` 禁止扩张的断言
- [X] T017 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorPersistenceBoundaryTest.java` 与 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryIntegrationTest.java` 对齐 ledger、memory 迁移后的包路径与主链路回归

### Implementation for User Story 2

- [X] T018 [P] [US2] 将 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/runtime/` 重组到 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/`，并同步修改 `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/ReactorRuntimeAutoConfiguration.java` 与 `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/AgentHandlerAutoConfiguration.java`
- [X] T019 [P] [US2] 将 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/tooloutput/` 收敛到 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/`，并同步修改 `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/repository/ExecutionLedgerReadRepository.java`、`ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/repository/ExecutionLedgerWriteRepository.java`、`ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputReaderImpl.java`、`ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputWriterImpl.java`
- [X] T020 [P] [US2] 将 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/SessionContextMemoryService.java` 与 `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/reactor/service/impl/SessionContextMemoryServiceImpl.java` 收敛到 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/memory/` 并修正实现导入
- [X] T021 [P] [US2] 将 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/IRagService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/rag/RagService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/TableRagService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/SchemaRecallService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/SopRecallService.java` 收敛到 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/rag/`，并同步修改 `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/rag/RagApplicationService.java`
- [X] T022 [P] [US2] 将 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/IFixRoleService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/role/FixRoleService.java` 收敛到 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/role/`，并同步修改 `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/role/FixRoleQueryApplicationService.java` 与 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentRoleLibraryController.java`
- [X] T023 [US2] 在 `specs/019-agent-ddd-convergence/quickstart.md` 指定的 US2 命令下执行 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java`、`ReactorPersistenceBoundaryTest.java`、`SessionContextMemoryIntegrationTest.java`、`ReplayProjectorBeanTopologyTest.java` 的独立回归，并记录旧 `reactor`、`service/rag`、`service/role` 目录审计结果

**Checkpoint**: User Story 2 完成后，Agent 核心能力已经能按子域定位和演进，旧 `reactor` 总包不再是主路径兜底

---

## Phase 5: User Story 3 - 交付团队可用守卫与文档锁定最终边界 (Priority: P3)

**Goal**: 用自动化守卫和更新后的模块文档阻止旧目录、协议泄漏和技术依赖回流  
**Independent Test**: 执行目录扫描、关键字符串扫描、`AgentContextConvergenceBoundaryTest`、`SpringRuntimeBoundaryTest`、`ReactorHttpControllerTest`、`AgentHandlerAutoConfigurationTest`；并核对根级与模块级文档是否能直接解释最终边界

### Verification for User Story 3

- [X] T024 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java` 与 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SpringRuntimeBoundaryTest.java` 扩充 `SseEmitter`、`new OkHttpClient`、`JdbcDataProvider`、`SpringContextHolder`、`applicationContext.getBean(` 零命中的最终守卫
- [X] T025 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorHttpControllerTest.java` 与 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/DataAgentCapabilityDegradeTest.java` 校准 trigger、dataagent 与 runtime adapter 的最终装配回归

### Implementation for User Story 3

- [X] T026 [P] [US3] 将 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/util/SseUtil.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/util/SseEmitterUTF8.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/printer/SSEPrinter.java` 迁出 `domain`，并在 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/reactor/support/SseEmitterAgentSessionStream.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/AiAgentController.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/reactor/ReactorController.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/dataagent/DataAgentController.java` 收口 SSE 建立、心跳与关闭逻辑
- [X] T027 [P] [US3] 在 `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/query/IGptQueryApplicationService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/query/GptQueryApplicationService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/dataquery/IDataAgentApplicationService.java`、`ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/dataquery/DataAgentApplicationService.java` 新增 legacy query 与 dataagent 的 case seam，并同步修改 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IGptProcessService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/GptProcessServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IMultiAgentService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/MultiAgentServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/DataAgentService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/Nl2SqlService.java` 的边界
- [X] T028 [P] [US3] 在迁移后的 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/adapter/port/` 中抽出 HTTP、tool runtime、model invoke 与 file artifact 端口，并在 `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/port/`、`ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/gateway/ReactorFileGateway.java`、`ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/gateway/ReactorImageGenerationGateway.java` 承接 `LLM`、`CodeInterpreterTool`、`DataAnalysisTool`、`DeepSearchTool`、`FileTool`、`MultiModalAgent`、`ReportTool`、`ImageGenerationTool` 的 OkHttp 与远端调用实现
- [X] T029 [P] [US3] 将 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/data/jdbc/`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/data/provider/jdbc/`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/util/JdbcUtils.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/util/HttpUtils.java` 下沉到 `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dataquery/` 与 `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/gateway/`，并同步修改 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ChatModelInfoService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ColumnValueSyncService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/DataAgentService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/Nl2SqlService.java`、`ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/DataAgentInitRunner.java`、`ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/data/Es7HighLevelClientConfig.java`
- [X] T030 [US3] 在 `CLAUDE.md`、`ai-agent-station-study-domain/CLAUDE.md`、`ai-agent-station-study-infrastructure/CLAUDE.md`、`ai-agent-station-study-trigger/CLAUDE.md`、`ai-agent-station-study-app/CLAUDE.md` 回填最终职责边界、兼容桥要求和禁止依赖说明
- [X] T031 [US3] 在 `specs/019-agent-ddd-convergence/quickstart.md` 指定的 US3 命令下执行 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java`、`SpringRuntimeBoundaryTest.java`、`ReactorHttpControllerTest.java`、`AgentHandlerAutoConfigurationTest.java` 的独立回归，并记录目录扫描与文档核对结果

**Checkpoint**: User Story 3 完成后，边界守卫、技术依赖约束和模块文档已经能阻止旧路径回流

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 删除残留兼容桥、回填 feature 文档并完成最终全量验收

- [X] T032 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/` 及迁移后新增的 bridge 文件中清理无依赖方的过渡注释与空壳目录，只保留带明确删除时机的短期桥接
- [X] T033 [P] 在 `specs/019-agent-ddd-convergence/quickstart.md`、`specs/019-agent-ddd-convergence/contracts/application-boundary-contract.md`、`specs/019-agent-ddd-convergence/contracts/domain-infrastructure-contract.md` 回填最终 seam、扫描命令和 bridge 删除条件
- [X] T034 在 `specs/019-agent-ddd-convergence/quickstart.md` 指定的最终验收命令下执行 `rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service"`、`rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor"`、`mvn compile -pl ai-agent-station-study-types,ai-agent-station-study-api,ai-agent-station-study-case,ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests` 与 `mvn test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,AgentHandlerAutoConfigurationTest,ReplayProjectorBeanTopologyTest,ReactorPersistenceBoundaryTest,SpringRuntimeBoundaryTest" "-Dsurefire.failIfNoSpecifiedTests=false"` 并记录最终收敛结果

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: 无依赖，可立即开始
- **Phase 2: Foundational**: 依赖 Setup 完成，阻塞所有用户故事
- **Phase 3: US1**: 依赖 Foundational 完成；这是 MVP，建议最先交付
- **Phase 4: US2**: 依赖 US1 已稳定 case 入口和 role/rag seam，再做领域子域重组
- **Phase 5: US3**: 依赖 US2 已明确 runtime、ledger、memory、rag、role 主归属，再锁技术依赖与文档边界
- **Phase 6: Polish**: 依赖所有目标用户故事完成

### User Story Dependencies

- **US1 (P1)**: 只依赖 Foundational，是首个可交付增量
- **US2 (P2)**: 依赖 US1 已切断旧 `domain.service` 主路径，但有独立的目录审计和 replay/memory 回归
- **US3 (P3)**: 依赖 US2 已固定子域归属，再完成 SSE、OkHttp、JDBC 与文档守卫收口

### Within Each User Story

- 先补齐该故事需要的边界守卫和聚焦回归
- 再做目录迁移、case seam 或端口/适配器改造
- 每个故事完成后都要执行该故事自己的扫描、编译与回归命令

### Parallel Opportunities

- Setup 中的 `T002`、`T003` 可并行
- Foundational 中的 `T005`、`T006`、`T007` 可并行
- US1 中的 `T009`、`T010` 可并行；`T011`、`T012` 可并行
- US2 中的 `T016`、`T017` 可并行；`T018`、`T019`、`T020`、`T021`、`T022` 可按不同子域并行
- US3 中的 `T024`、`T025` 可并行；`T026`、`T028`、`T029` 可按 SSE、HTTP/tool runtime、JDBC/dataquery 三条线并行
- Polish 中的 `T032`、`T033` 可并行

---

## Parallel Example: User Story 1

```text
T009 [US1] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java
T010 [US1] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryIntegrationTest.java

T011 [US1] ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/role/FixRoleQueryApplicationService.java
T012 [US1] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/IAgentDispatchService.java
```

## Parallel Example: User Story 2

```text
T016 [US2] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java
T017 [US2] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorPersistenceBoundaryTest.java

T018 [US2] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/
T019 [US2] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/
T020 [US2] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/
```

## Parallel Example: User Story 3

```text
T024 [US3] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SpringRuntimeBoundaryTest.java
T025 [US3] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/DataAgentCapabilityDegradeTest.java

T026 [US3] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/util/SseUtil.java
T028 [US3] ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/port/
T029 [US3] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/data/jdbc/
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational
3. 完成 Phase 3: US1
4. 执行 `T015` 的扫描、编译和聚焦回归
5. 在确认 case 成为唯一应用编排入口后再继续

### Incremental Delivery

1. 先完成 Setup + Foundational，打稳边界守卫、package-info 与统一验收入口
2. 交付 US1，解决“应用编排仍在 domain/service 残留”的核心问题
3. 交付 US2，完成 `runtime / ledger / memory / rag / role` 子域收敛
4. 交付 US3，锁定 SSE、OkHttp、JDBC、Spring runtime 与模块文档边界
5. 最后执行 Polish，清桥接、补 feature 文档并做最终全量验收

### Parallel Team Strategy

1. 一组先完成 Setup + Foundational
2. US1 完成后：
   - 一组推进 US2 的 runtime / ledger / memory 子域重组
   - 一组推进 US2 的 rag / role 子域收敛
3. US2 收口后：
   - 一组推进 US3 的 SSE 与 case seam 收口
   - 一组推进 US3 的 OkHttp / JDBC / dataquery 下沉
4. 最后共同完成 Polish 和全量回归

---

## Notes

- 本特性只改 `ai-agent-station-study-case`、`ai-agent-station-study-domain`、`ai-agent-station-study-infrastructure`、`ai-agent-station-study-trigger`、`ai-agent-station-study-app`，不改 `ui/`、`reactor-tool/`、`reactor-client/`
- `ReactorConfig` 继续按 [plan.md](./plan.md) 的过渡约束处理，除非迁移必须，否则不在本任务单中单独扩 scope 重写
- 目录迁移允许分批完成，但任何 bridge 文件都必须写中文注释说明依赖方、保留原因和删除时机
- `domain` 最终只允许保留领域模型、领域服务、仓储契约和外部能力端口，不允许重新引回协议对象或技术执行器
- 任务描述中的目录路径表示“整批迁移或整批审计”的执行边界；实现时仍需保持提交粒度清晰，避免跨故事混改
