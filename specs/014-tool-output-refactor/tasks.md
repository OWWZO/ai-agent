# Tasks: 工具输出独立表重构

**Input**: Design documents from `/specs/014-tool-output-refactor/`  
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: 本特性在 [plan.md](./plan.md) 的 Verification Plan、[quickstart.md](./quickstart.md) 和各用户故事 Independent Test 中明确要求编译、账本/reader/projector 回归、rich tool 单测与 direct tool call 验证，因此任务清单包含对应测试任务。  
**Organization**: 任务按用户故事组织，先建立共享 typed output / 输出表基础设施，再按 P1 → P2 → P3 逐步交付，保证每个故事都有独立验收闭环。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可以并行执行（不同文件、没有未完成前置依赖）
- **[Story]**: 仅用于用户故事阶段，表示任务归属的故事
- 每个任务都显式列出真实文件路径，便于直接执行

## Phase 1: Setup (Shared Output Refactor Scaffolding)

**Purpose**: 为主账本瘦身、typed output 与输出表读写建立资源和测试骨架

- [X] T001 在 `ai-agent-station-study-app/src/main/resources/db/schema.sql` 与 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/tool_invocation_ledger_mapper.xml` 预留主账本移除 `output_json` 和 8 张 `ai_agent_tool_output_*` 表的改造区块
- [X] T002 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ToolStructuredOutput.java`、`ToolFileRef.java`、`ToolOutputPersistCommand.java` 建立 typed output 基础文件骨架
- [X] T003 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/tooloutput/ToolOutputWriter.java`、`ToolOutputReader.java`、`ToolOutputView.java` 与 `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputWriterImpl.java`、`ToolOutputReaderImpl.java` 建立输出表读写骨架
- [X] T004 [P] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolStructuredOutputWriterTest.java`、`ToolStructuredOutputReaderTest.java` 与 `ExecutionLedgerFixtureFactory.java` 建立输出表测试骨架和夹具入口

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 完成所有用户故事都会复用的共享模型、DDL、DAO/PO、旧契约清理与基础路由能力

**⚠️ CRITICAL**: 本阶段完成前，不开始任何用户故事实现

- [X] T005 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/ToolResultPayload.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/ToolInvocationFinishRecord.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/ToolInvocation.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/ToolInvocationView.java` 删除 `outputJson` 并补齐 `structuredOutput / toolName / runId / sessionId / failed` 共享契约
- [X] T006 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/DeepSearchToolOutput.java`、`DeepSearchStage.java`、`DeepSearchDoc.java`、`FileToolOutput.java`、`CodeInterpreterToolOutput.java`、`ReportToolOutput.java`、`DataAnalysisToolOutput.java`、`MultimodalAgentToolOutput.java`、`ImageGenerationToolOutput.java`、`ScriptRunnerToolOutput.java` 完成 8 类 rich tool 的 typed output 模型
- [X] T007 [P] 在 `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ToolOutputDeepSearchPO.java`、`ToolOutputFileToolPO.java`、`ToolOutputCodeInterpreterPO.java`、`ToolOutputReportToolPO.java`、`ToolOutputDataAnalysisPO.java`、`ToolOutputMultimodalAgentPO.java`、`ToolOutputImageGenerationPO.java`、`ToolOutputScriptRunnerPO.java` 以及 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IToolOutputDeepSearchDao.java`、`IToolOutputFileToolDao.java`、`IToolOutputCodeInterpreterDao.java`、`IToolOutputReportToolDao.java`、`IToolOutputDataAnalysisDao.java`、`IToolOutputMultimodalAgentDao.java`、`IToolOutputImageGenerationDao.java`、`IToolOutputScriptRunnerDao.java` 建立 8 张输出表的 PO 与 DAO
- [X] T008 [P] 在 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/tool_output_deep_search_mapper.xml`、`tool_output_file_tool_mapper.xml`、`tool_output_code_interpreter_mapper.xml`、`tool_output_report_tool_mapper.xml`、`tool_output_data_analysis_mapper.xml`、`tool_output_multimodal_agent_mapper.xml`、`tool_output_image_generation_mapper.xml`、`tool_output_script_runner_mapper.xml` 完成 8 张输出表的 MyBatis XML 骨架、公共列和共享索引映射
- [X] T009 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ToolOutputJsonBuilder.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolOutputJsonBuilderTest.java`、`ToolOutputJsonRuntimeTest.java` 清理旧 `output_json` builder 和相关测试入口，避免新旧契约并存

**Checkpoint**: typed output、输出表 DAO/PO/Mapper 资源和主账本共享契约都已就绪，用户故事可以开始实现

---

## Phase 3: User Story 1 - 独立记录工具输出 (Priority: P1) 🎯 MVP

**Goal**: 让 8 类 rich tool 的结构化结果脱离主账本，作为 typed output 直接落到独立输出表，且主账本只保留调用事实与终态  
**Independent Test**: 触发任一受支持 rich tool 的成功场景和失败/超时场景，验证 `ai_agent_tool_invocation` 只保留调用元数据、终态、`llmObservation`、`errorMsg`，并且对应 `ai_agent_tool_output_*` 恰好生成 1 条结构化终态记录

### Tests for User Story 1

- [X] T010 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolStructuredOutputWriterTest.java` 与 `AgentExecutionLedgerRepositoryTest.java` 先补写 rich tool 成功/失败终态各落 1 条输出记录、主账本不再写 `output_json` 的失败用例
- [X] T011 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/DeepSearchLlmObservationTest.java`、`MultiModalAgentToolTest.java`、`ImageGenerationToolTest.java`、`ScriptRunnerToolTest.java` 先补写 rich tool 返回 typed `structuredOutput`、失败场景显式 `failed=true` 的单测

### Implementation for User Story 1

- [X] T012 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DeepSearchStructuredResultBuilder.java`、`DeepSearchTool.java`、`FileTool.java` 把 `deep_search` / `file_tool` 改为直接返回 `DeepSearchToolOutput`、`FileToolOutput` 和规范化 `fileRefs`
- [X] T013 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/CodeInterpreterTool.java`、`ReportTool.java`、`DataAnalysisTool.java`、`MultiModalAgent.java`、`ImageGenerationTool.java` 以及 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/ScriptRunnerTool.java` 把其余 6 个 rich tool 改为返回 typed output 并统一失败语义
- [X] T014 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java` 打通 `structuredOutput` 写链路，让主账本只更新 `status / llm_oberserve / error_msg / finished_at`
- [X] T015 [US1] 在 `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputWriterImpl.java`、`ai-agent-station-study-app/src/main/resources/db/schema.sql`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/tool_invocation_ledger_mapper.xml` 与 8 个 `tool_output_*_mapper.xml` 完成独立输出表写入、长文本字段和 `file_refs_json / stages_json` 落库
- [X] T016 [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolStructuredOutputWriterTest.java`、`AgentExecutionLedgerRepositoryTest.java`、`DeepSearchLlmObservationTest.java`、`MultiModalAgentToolTest.java`、`ImageGenerationToolTest.java`、`ScriptRunnerToolTest.java` 执行 US1 回归并核对 [quickstart.md](./quickstart.md) 第 4、5 节的独立落账验收

**Checkpoint**: US1 完成后，rich tool 的结构化结果已彻底脱离主账本，这是本特性的 MVP

---

## Phase 4: User Story 2 - 仅基于新输出记录回放历史 (Priority: P2)

**Goal**: 让执行详情查询和 history replay 完全依赖 typed output 与输出表，不再读取主账本 `output_json`  
**Independent Test**: 构造包含 `deep_search`、文件、报告、数据分析、图像生成等 rich tool 的历史样本，验证 replay 和 run detail 在不读取 `output_json` 的前提下正确恢复用户可见事件，且 `deep_search` 部分阶段失败场景只回放已实际完成阶段

### Tests for User Story 2

- [X] T017 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolInvocationProjectorTest.java` 与 `ReplayProjectorTest.java` 先补写 rich projector 不能再读取 `outputJson`、`deep_search` 部分阶段 replay 和 default projector fallback 的失败用例
- [X] T018 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java` 与 `ToolStructuredOutputReaderTest.java` 先补写 run detail enrich typed output、无文件结果归一化为空数组的用例

### Implementation for User Story 2

- [X] T019 [P] [US2] 在 `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputReaderImpl.java` 与 8 个 `tool_output_*_mapper.xml` 实现 `readByInvocationId(...)`，完成 `stages_json`、`file_refs_json` 与 typed output 的反序列化
- [X] T020 [P] [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IToolInvocationLedgerDao.java` 与 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/tool_invocation_ledger_mapper.xml` 删除 `outputJson` 查询映射并为 rich tool 明细补充 `structuredOutput`
- [X] T021 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/DefaultToolInvocationProjector.java`、`AbstractToolInvocationProjector.java`、`DeepSearchToolInvocationProjector.java`、`FileToolInvocationProjector.java`、`CodeInterpreterToolInvocationProjector.java`、`DataAnalysisToolInvocationProjector.java`、`ImageGenerationToolInvocationProjector.java`、`MultiModalToolInvocationProjector.java`、`ReportToolInvocationProjector.java`、`ScriptRunnerToolInvocationProjector.java` 改为只消费 `ToolOutputReader` 和 fallback 文本
- [X] T022 [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java`、`ToolStructuredOutputReaderTest.java`、`ToolInvocationProjectorTest.java`、`ReplayProjectorTest.java` 执行 US2 回归并核对 [quickstart.md](./quickstart.md) 第 6 节的 replay 验收

**Checkpoint**: US2 完成后，历史回放和执行详情查询已经彻底摆脱主账本 JSON

---

## Phase 5: User Story 3 - 支持直接工具调用检索 (Priority: P3)

**Goal**: 让没有主账本关联的 direct tool call 也能在新输出表体系内通过 `requestId + toolCallId` 检索，且重复终态写入采用首次写入生效  
**Independent Test**: 模拟一次没有 `toolInvocationId` 的 direct tool call 并重复写入终态，验证 reader 仍可通过 `requestId + toolCallId` 唯一命中对应 typed output，后续重复终态不会覆盖首条结果且会留下冲突日志

### Tests for User Story 3

- [X] T023 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolStructuredOutputWriterTest.java` 与 `ToolStructuredOutputReaderTest.java` 先补写 direct lookup、first-write-wins、多表重复命中冲突和 nullable `toolInvocationId / runId / sessionId` 的失败用例
- [X] T024 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutionLedgerRepositoryTest.java` 与 `ExecutionLedgerQueryServiceTest.java` 先补写 direct tool call 不依赖主账本、失败终态最小化 typed output 仍可解释的用例

### Implementation for User Story 3

- [X] T025 [P] [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/tooloutput/ToolOutputReader.java`、`ToolOutputView.java` 与 `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputReaderImpl.java` 实现 `readDirect(requestId, toolCallId)` 的 8 表固定扇出读取
- [X] T026 [P] [US3] 在 `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputWriterImpl.java`、8 个 `ToolOutput*PO.java` 与 8 个 `tool_output_*_mapper.xml` 实现 first-write-wins、冲突日志和 direct tool call 可空关联字段写入
- [X] T027 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ToolOutputPersistCommand.java`、`ToolResultPayload.java`、`BaseAgent.java` 与 `ToolOutputReaderImpl.java` 收口 direct tool call 最小写入约束，确保失败/超时也能返回可解释的 `ToolOutputView`
- [X] T028 [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolStructuredOutputWriterTest.java`、`ToolStructuredOutputReaderTest.java`、`AgentExecutionLedgerRepositoryTest.java`、`ExecutionLedgerQueryServiceTest.java` 执行 US3 回归并核对 [quickstart.md](./quickstart.md) 第 7、8 节的 direct lookup / 冲突验收

**Checkpoint**: US3 完成后，直接工具调用与重复终态写入语义已经在新表体系内闭环

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 完成清理、文档回填和最终全量回归

- [X] T029 [P] 在 `specs/014-tool-output-refactor/quickstart.md`、`specs/014-tool-output-refactor/contracts/structured-tool-output-persistence.md`、`specs/014-tool-output-refactor/contracts/structured-tool-output-query-and-replay.md` 回填最终实现细节、SQL 示例和 direct lookup / replay 约束
- [X] T030 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DeepSearchStructuredResultBuilder.java`、`ReportTool.java`、`MultiModalAgent.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/AbstractToolInvocationProjector.java` 清理旧 `output_json` 注释并补齐关键边界的中文注释
- [X] T031 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolStructuredOutputWriterTest.java`、`ToolStructuredOutputReaderTest.java`、`AgentExecutionLedgerRepositoryTest.java`、`ExecutionLedgerQueryServiceTest.java`、`ToolInvocationProjectorTest.java`、`ReplayProjectorTest.java`、`DeepSearchLlmObservationTest.java`、`MultiModalAgentToolTest.java`、`ImageGenerationToolTest.java`、`ScriptRunnerToolTest.java` 执行 [plan.md](./plan.md) Verification Plan 中的 compile/test 命令并完成 [quickstart.md](./quickstart.md) 的最终验收

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: 无依赖，可立即开始
- **Phase 2: Foundational**: 依赖 Setup 完成，阻塞全部用户故事
- **Phase 3: US1**: 依赖 Foundational 完成；这是 MVP，建议最先交付
- **Phase 4: US2**: 依赖 US1 已完成 typed output 写入与基础 reader 资源
- **Phase 5: US3**: 依赖 US1 已完成输出表写入主链路；可在 US2 后或与 US2 尾声并行收口
- **Phase 6: Polish**: 依赖所有目标用户故事完成

### User Story Dependencies

- **US1 (P1)**: 只依赖 Foundational，是首个可交付增量
- **US2 (P2)**: 依赖 US1 已把 rich output 持久化到独立表，但有独立 replay/detail 验收
- **US3 (P3)**: 依赖 US1 的输出表与 writer 基础能力，但不依赖 US2 的 projector 改造

### Within Each User Story

- 先补测试，再改实现
- 先完成 shared model / writer / reader，再做入口链路与回放整合
- 每个故事完成后都要执行对应的独立验证任务

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
T010 [US1] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolStructuredOutputWriterTest.java
T011 [US1] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/DeepSearchLlmObservationTest.java

T012 [US1] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DeepSearchStructuredResultBuilder.java
T013 [US1] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/CodeInterpreterTool.java
```

## Parallel Example: User Story 2

```text
T017 [US2] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolInvocationProjectorTest.java
T018 [US2] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java

T019 [US2] ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputReaderImpl.java
T020 [US2] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java
```

## Parallel Example: User Story 3

```text
T023 [US3] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolStructuredOutputWriterTest.java
T024 [US3] ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutionLedgerRepositoryTest.java

T025 [US3] ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/tooloutput/ToolOutputReader.java
T026 [US3] ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputWriterImpl.java
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational
3. 完成 Phase 3: US1
4. 执行 `T016` 的自动化回归和手工落账验收
5. 在确认 rich tool 已独立落表后再继续

### Incremental Delivery

1. 先完成 Setup + Foundational，打稳 typed output、输出表和主账本共享契约
2. 交付 US1，解决“主账本仍承载 rich 结构化结果”的核心问题
3. 交付 US2，补齐只读新表的详情查询和历史回放闭环
4. 交付 US3，补齐 direct tool call 与重复终态写入语义
5. 最后执行 Polish，统一完成文档回填和最终回归

### Parallel Team Strategy

1. 一组先完成 Setup + Foundational
2. US1 完成后：
   - 一组推进 US2 的 reader / query / projector 改造
   - 一组推进 US3 的 direct lookup / 冲突语义
3. 最后共同执行 Polish 和全量回归

---

## Notes

- 所有任务都限定在 Java 主链路和 MyBatis 持久化范围内，本期不改 `ui/`、`reactor-tool/`、`reactor-client/`
- rich projector 只允许读取 `ToolOutputReader`，禁止继续在任何地方回退主账本 `output_json`
- `deep_search` 的 `stages_json` 只能保存已实际完成阶段，禁止写占位阶段
- direct tool call 的读取只允许在 8 张新输出表内固定扇出，不新增兼容表、不回查旧账本
- 复杂边界、冲突日志和失败语义必须补中文注释
