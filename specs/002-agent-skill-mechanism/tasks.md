# Tasks: Agent Skill Mechanism

**Input**: Design documents from `/specs/002-agent-skill-mechanism/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this belongs to (`US1`, `US2`, `US3`)
- 每条任务都写出准确文件路径，便于直接开工

## Phase 1: Setup (Shared Preparation)

**Purpose**: 准备 skill 配置入口、运行时目录占位和可复用测试样例，避免后续实现时反复补环境

- [X] T001 在 `ai-agent-station-study-app/src/main/resources/application.yml`、`ai-agent-station-study-app/src/main/resources/application-dev.yml`、`ai-agent-station-study-app/src/main/resources/application-test.yml`、`ai-agent-station-study-app/src/main/resources/application-prod.yml` 中补充 `autobots.autoagent.skill.*` 配置示例、默认目录说明和脚本超时/读取上限参数
- [X] T002 在 `ai-agent-station-study-app/src/test/resources/skills/sql-analysis/SKILL.md`、`ai-agent-station-study-app/src/test/resources/skills/sql-analysis/scripts/summarize.py`、`ai-agent-station-study-app/src/test/resources/skills/sql-analysis/references/metrics.md`、`ai-agent-station-study-app/src/test/resources/skills/sql-analysis/scripts.yaml` 与 `runtime/skills/.gitkeep` 中准备 skill 测试夹具和运行时目录占位

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 完成 skill 机制的共享配置、元数据模型、注册中心和工具装配工厂，这些能力阻塞所有用户故事

**⚠️ CRITICAL**: 本阶段完成前，不要开始用户故事实现

- [X] T003 在 `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/AiAgentSkillProperties.java` 与 `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/AiAgentAutoConfiguration.java` 中新增 skill 配置绑定和 Spring Bean 装配入口
- [X] T004 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/skill/SkillDefinition.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/skill/SkillScriptDefinition.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/skill/SkillLoadException.java` 中建立 skill 元数据模型和确定性错误类型
- [X] T005 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/skill/SkillMarkdownParser.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/skill/SkillScriptDiscoverer.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/skill/SkillPathGuard.java` 中实现 `SKILL.md` 解析、`scripts.yaml`/`scripts/` 发现和路径边界校验
- [X] T006 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/skill/SkillRegistry.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/skill/DefaultSkillRegistry.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/DataAgentInitRunner.java` 中实现 skill 扫描缓存、重名冲突处理和启动期初始化
- [X] T007 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/factory/AgentToolCollectionFactory.java` 中抽取 `PlanSolve/ReAct` 共用工具装配入口，给 skill 工具和 MCP 工具预留统一注册位
- [X] T008 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java` 与 `ai-agent-station-study-app/src/main/resources/application.yml` 中补齐 skill 启用范围、默认超时和可观测日志所需读取字段

**Checkpoint**: skill 配置、注册中心、路径守卫和共享工具装配入口已就绪，用户故事可以开始推进

---

## Phase 3: User Story 1 - 智能体可按需加载技能 (Priority: P1) 🎯 MVP

**Goal**: `PlanSolve/ReAct` 智能体可以发现可用 skill，并通过 `skill_tool` 返回技能正文、基路径和脚本摘要

**Independent Test**: 配置测试 skill 后，模型初始化可见 skill 列表；调用 `skill_tool` 能返回 `SKILL.md` 正文、绝对基路径和脚本摘要；传入不存在的 skill 名称时返回明确错误

### Tests for User Story 1

- [X] T009 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/spring/ai/SkillRegistryTest.java` 中覆盖合法 skill 加载、缺失 frontmatter、空目录降级和重名 skill 冲突场景
- [X] T010 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/spring/ai/SkillToolTest.java` 中覆盖 `skill_tool` 描述生成、命中 skill 成功返回和不存在 skill 明确报错场景

### Implementation for User Story 1

- [X] T011 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/skill/DefaultSkillRegistry.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/skill/SkillDefinition.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/skill/SkillScriptDefinition.java` 中完成 skill 缓存、摘要输出和脚本清单聚合逻辑
- [X] T012 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/dto/skill/SkillToolResult.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/SkillTool.java` 中实现 `skill_tool` 输入输出协议和正文返回格式
- [X] T013 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/factory/AgentToolCollectionFactory.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/step/RootNode.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/step/Step1SopRecallAndPrepareNode.java` 中接入 `skill_tool`，让 `PlanSolve/ReAct` 共用同一套 skill 装配逻辑
- [ ] T014 [US1] 执行 `mvn -pl ai-agent-station-study-app -am test -Dtest=SkillRegistryTest,SkillToolTest`，并按 `specs/002-agent-skill-mechanism/quickstart.md` 完成 `skill_tool` 的手工冒烟验证

**Checkpoint**: 到这里为止，skill 已经能被发现、列出并按需读取，这是第一阶段可演示 MVP

---

## Phase 4: User Story 2 - 技能机制与现有 React 工具链优雅集成 (Priority: P2)

**Goal**: skill 机制复用现有 `BaseTool + ToolCollection` 装配链，消除 `RootNode`/`Step1SopRecallAndPrepareNode` 的重复注册逻辑，同时保持 `Fix` 链路不变

**Independent Test**: `PlanSolve/ReAct` 初始化时自动装入 skill 工具与原有工具；`Fix` 链路仍按现有 Spring AI/MCP 逻辑执行，没有新增 skill 注入

### Tests for User Story 2

- [X] T015 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/spring/ai/AgentToolCollectionFactoryTest.java` 中验证 `PlanSolve/ReAct` 工具集合包含 skill 工具、现有本地工具和 MCP 工具，且装配顺序稳定
- [X] T016 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/FixedAgentExecuteStrategyTest.java` 中补充回归用例，确认启用 skill 后 `Fix` 链路仍不暴露 `skill_tool`

### Implementation for User Story 2

- [X] T017 [P] [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/factory/AgentToolCollectionFactory.java` 中统一组装 `FileTool`、`CodeInterpreterTool`、`ReportTool`、`DeepSearchTool`、`DataAnalysisTool`、skill 工具和 MCP 工具
- [X] T018 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/step/RootNode.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/step/Step1SopRecallAndPrepareNode.java` 中删除重复 `buildToolCollection` 逻辑并改用 `AgentToolCollectionFactory`
- [X] T019 [US2] 在 `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/AiAgentSkillProperties.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java`、`ai-agent-station-study-app/src/main/resources/application.yml` 中补齐 `PlanSolve/ReAct` 启用范围控制，确保 skill 机制不会侵入 `Fix`
- [X] T020 [US2] 执行 `mvn -pl ai-agent-station-study-app -am test -Dtest=AgentToolCollectionFactoryTest,FixedAgentExecuteStrategyTest`，并对照 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/node/AiClientNode.java` 验证 `Fix` 装配路径保持原样

**Checkpoint**: skill 已经成为 `PlanSolve/ReAct` 的标准本地工具扩展点，后续无需再在两个节点重复拼装

---

## Phase 5: User Story 3 - 新增技能和后续新工具接入更轻量 (Priority: P3)

**Goal**: 新增 skill 目录后无需改业务代码即可被发现，并且模型可以通过只读文件工具与 `script_runner_tool` 继续消费 `references/` 和 `scripts/`

**Independent Test**: 新增或替换一个测试 skill 目录后，系统重载后可自动发现；模型可调用 `read_tool`/`list_directory_tool`/`glob_tool`/`grep_tool` 访问 skill 目录资源，并通过 `script_runner_tool` 成功执行注册脚本或明确拒绝越界/未注册脚本

### Tests for User Story 3

- [X] T021 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/spring/ai/SkillFileAccessToolTest.java` 中覆盖 `read_tool`、`list_directory_tool`、`glob_tool`、`grep_tool` 的路径守卫、读取截断和未注册 skill 错误分支
- [X] T022 [P] [US3] 在 `reactor-tool/tests/test_script_runner.py` 中覆盖 `python`/`shell` 运行时、超时、越界路径拒绝和产出文件上传返回 `fileInfo` 的冒烟场景

### Implementation for User Story 3

- [X] T023 [P] [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/ReadTool.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/ListDirectoryTool.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/GlobTool.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/GrepTool.java` 中实现 skill 目录内只读访问工具族
- [X] T024 [P] [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/dto/skill/ScriptRunnerToolRequest.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/dto/skill/ScriptRunnerToolResponse.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/skill/SkillScriptRunnerClient.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/ScriptRunnerTool.java` 中实现 Java 侧脚本执行协议和客户端
- [X] T025 [P] [US3] 在 `reactor-tool/reactor_tool/model/protocal.py` 与 `reactor-tool/reactor_tool/api/tool.py` 中新增 `script_runner` 请求响应模型和 `POST /v1/tool/script_runner` 路由
- [X] T026 [P] [US3] 在 `reactor-tool/reactor_tool/tool/script_runner.py` 与 `reactor-tool/reactor_tool/tool/script_runtime.py` 中实现 `python`、`node`、`shell`、`powershell`、`bat` 五类 runtime 的命令构建、工作目录隔离和超时控制
- [X] T027 [US3] 在 `reactor-tool/reactor_tool/tool/script_runner.py`、`reactor-tool/reactor_tool/util/file_util.py`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/ScriptRunnerTool.java` 中打通脚本输出文件上传、`fileInfo` 返回和 agent 文件上下文追加
- [X] T028 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/factory/AgentToolCollectionFactory.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/SkillTool.java` 中注册 `script_runner_tool` 与四个只读工具，并让 `skill_tool` 返回脚本摘要和基路径供模型继续调用
- [ ] T029 [US3] 执行 `mvn -pl ai-agent-station-study-app -am test -Dtest=SkillFileAccessToolTest`、`cd reactor-tool && python -m unittest tests/test_script_runner.py`，并按 `specs/002-agent-skill-mechanism/quickstart.md` 完成脚本执行与最小安全边界的手工验收

**Checkpoint**: skill 已经形成“可安装、可发现、可读取、可执行”的完整闭环，新 skill 通过目录和配置即可接入

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 收尾清理、文档对齐和全链路验证

- [ ] T030 [P] 在 `reactor-tool/README.md`、`specs/002-agent-skill-mechanism/quickstart.md`、`specs/002-agent-skill-mechanism/contracts/skill-tool-contract.md`、`specs/002-agent-skill-mechanism/contracts/script-runner-api.md` 中同步实现后的最终命名、示例和执行说明
- [ ] T031 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/factory/AgentToolCollectionFactory.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/skill/DefaultSkillRegistry.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/ScriptRunnerTool.java`、`reactor-tool/reactor_tool/tool/script_runner.py` 中清理重复逻辑、补齐中文注释并统一日志字段
- [ ] T032 执行完整回归：`mvn -pl ai-agent-station-study-app -am test -Dtest=SkillRegistryTest,SkillToolTest,AgentToolCollectionFactoryTest,FixedAgentExecuteStrategyTest,SkillFileAccessToolTest`、`cd reactor-tool && python -m unittest tests/test_script_runner.py`，并按 `specs/002-agent-skill-mechanism/quickstart.md` 完成最终人工验收

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: 可立即开始
- **Phase 2: Foundational**: 依赖 Setup，且阻塞全部用户故事
- **Phase 3: US1**: 依赖 Foundational，是第一个 MVP 闭环
- **Phase 4: US2**: 依赖 Foundational；建议在 US1 可演示后继续推进
- **Phase 5: US3**: 依赖 Foundational，并直接复用 US1 的 skill 注册与 skill_tool 契约
- **Phase 6: Polish**: 依赖所有目标用户故事完成

### User Story Dependencies

- **US1**: 无故事级前置依赖，是首个 MVP
- **US2**: 依赖 Foundational，共享 `AgentToolCollectionFactory` 与 `ReactorConfig` 的装配收口
- **US3**: 依赖 Foundational，并建立在 US1 已有的 `SkillRegistry` 与 `skill_tool` 能力之上

### Parallel Opportunities

- T004 与 T005 可并行
- T009 与 T010 可并行
- T015 与 T016 可并行
- T021 与 T022 可并行
- T023、T024、T025、T026 可在约定协议后并行推进
- T030 与 T031 可并行

---

## Parallel Example: User Story 1

```bash
# 并行编写 US1 的测试
Task: "在 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/spring/ai/SkillRegistryTest.java 中补齐注册中心用例"
Task: "在 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/spring/ai/SkillToolTest.java 中补齐 skill_tool 用例"

# 并行完成 US1 的两个核心实现
Task: "在 ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/skill/DefaultSkillRegistry.java 中完善缓存与冲突处理"
Task: "在 ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/SkillTool.java 中实现返回格式"
```

---

## Parallel Example: User Story 3

```bash
# 协议确认后并行推进 Java 和 Python 两侧
Task: "在 ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/ScriptRunnerTool.java 中完成 Java 侧脚本调用"
Task: "在 reactor-tool/reactor_tool/api/tool.py 中新增 /v1/tool/script_runner 路由"
Task: "在 reactor-tool/reactor_tool/tool/script_runtime.py 中实现多 runtime 执行器"
```

---

## Implementation Strategy

### MVP First

1. 完成 Phase 1-2，先把配置、注册中心、路径守卫和共享工厂搭好
2. 完成 Phase 3，只交付 `skill_tool` 的发现与读取闭环
3. 先做一次 MVP 冒烟和演示，再继续推进装配收口与脚本执行

### Incremental Delivery

1. **MVP**: US1 交付“可发现、可读取 skill”
2. **架构收口**: US2 交付“优雅接入现有 `PlanSolve/ReAct` 工具链，`Fix` 不受影响”
3. **生态闭环**: US3 交付“可读取 references、可执行 scripts、新 skill 可零代码接入”

### Notes

- `[P]` 任务只表示文件集可并行，不代表可以跳过前置设计或验收
- `reactor-tool/tests/test_script_runner.py` 建议使用 Python 标准库 `unittest`，避免额外引入测试依赖后再返工
- 若实现时发现 `ScriptRunnerToolRequest` / `ScriptRunnerToolResponse` 的字段命名需要与 `reactor-tool/reactor_tool/model/protocal.py` 进一步统一，先同步契约文档，再继续编码
- 每个故事完成后都先按 `specs/002-agent-skill-mechanism/quickstart.md` 做独立验收，避免把问题积压到最后统一排查
