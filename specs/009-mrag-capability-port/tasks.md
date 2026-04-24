# Tasks: 移植 MRAG 多模态知识检索能力

**Input**: Design documents from `/specs/009-mrag-capability-port/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: 本特性的规格中已经显式定义独立验收标准、异常场景和 quickstart 验证路径，因此本任务单包含 Java / Python / 手工冒烟测试任务。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Backend contracts**: `ai-agent-station-study-api/src/main/java/...`
- **Backend domain logic**: `ai-agent-station-study-domain/src/main/java/...`
- **Persistence / gateway**: `ai-agent-station-study-infrastructure/src/main/java/...`
- **HTTP / listener / job**: `ai-agent-station-study-trigger/src/main/java/...`
- **Application config / mapper / tests**: `ai-agent-station-study-app/src/...`
- **Frontend**: `ui/src/...`
- **Python MCP tooling**: `reactor-tool/...` or `reactor-client/...`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 为 MRAG 迁移准备最小运行骨架与测试入口

- [X] T001 在 `reactor-tool/pyproject.toml` 中补齐 MRAG 运行所需的最小依赖集合，并保留当前 `reactor-tool` 依赖风格
- [X] T002 [P] 在 `reactor-tool/reactor_tool/tool/mrag/__init__.py`、`reactor-tool/reactor_tool/tool/mrag/query/__init__.py`、`reactor-tool/reactor_tool/tool/mrag/retrieval/__init__.py`、`reactor-tool/reactor_tool/tool/mrag/generation/__init__.py` 和 `reactor-tool/reactor_tool/tool/mrag/storage/__init__.py` 创建 MRAG 包骨架
- [X] T003 [P] 在 `reactor-tool/tests/test_mrag_api.py`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/MultiModalAgentToolTest.java` 和 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/spring/ai/ReactorConfigMultiModalAgentTest.java` 创建本特性测试骨架

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 建立所有用户故事共用的协议、配置与基础装配能力

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/dto/MultiModalAgentRequest.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/dto/MultiModalAgentResponse.java` 新增 Java 侧 MRAG 请求/流式响应 DTO
- [X] T005 [P] 在 `reactor-tool/reactor_tool/model/protocal.py` 中新增 `MultimodalRAGRequest`，并让字段定义与 `specs/009-mrag-capability-port/contracts/mrag-query.openapi.yaml` 对齐
- [X] T006 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java` 和 `ai-agent-station-study-app/src/main/resources/application-dev.yml` 中补齐 `multimodalagent_tool` 描述、参数、默认工具别名、`multimodalagent_url` 与 `message_interval.knowledge` 配置
- [X] T007 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/MultiModalAgent.java` 创建工具类骨架，并在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/factory/AgentToolCollectionFactory.java` 预留 `multimodalagent` 的装配入口
- [X] T008 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/multi/EventResult.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/BaseAgentResponseHandler.java` 中补齐 `knowledge` 的流式任务归类基础能力
- [X] T009 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/spring/ai/AgentToolCollectionFactoryTest.java` 和 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/spring/ai/ReactorConfigMultiModalAgentTest.java` 先补基础装配与配置绑定测试，并确保在实现前失败

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - 现有对话可完成多模态知识检索 (Priority: P1) 🎯 MVP

**Goal**: 在现有 REACT / PlanSolve 会话中触发 MRAG 检索，持续返回图文混合结果，并生成 Markdown 产物

**Independent Test**: 在现有对话入口发起一条需要图文混合知识理解的文本问题，确认系统能调用 `multimodalagent_tool`、持续输出 MRAG 结果，并生成与该请求绑定的 Markdown 产物

### Tests for User Story 1

- [X] T010 [P] [US1] 在 `reactor-tool/tests/test_mrag_api.py` 中补充 `/v1/tool/mragQuery` 的 SSE 契约测试，覆盖增量 `choices[].delta.content` 与最终 `[DONE]`
- [X] T011 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/MultiModalAgentToolTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryArtifactTest.java` 和 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionTranscriptBlockAssemblerTest.java` 中补充 MRAG 成功链路、Markdown 产物与历史回放测试

### Implementation for User Story 1

- [X] T012 [P] [US1] 在 `reactor-tool/reactor_tool/tool/mrag/query/aigent.py`、`reactor-tool/reactor_tool/tool/mrag/query/query_processor.py` 以及 `reactor-tool/reactor_tool/tool/mrag/` 下被其引用的 `retrieval/`、`generation/`、`storage/` 子模块中迁入并适配 MRAG 查询主链路
- [X] T013 [P] [US1] 在 `reactor-tool/reactor_tool/api/tool.py` 中新增 `/mragQuery` 路由，调用 `reactor-tool/reactor_tool/tool/mrag/query/` 的 MRAG 入口并按契约输出 SSE
- [X] T014 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/MultiModalAgent.java` 中实现成功路径的 SSE 消费、`knowledge/markdown` 推送和 Markdown 文件上传
- [X] T015 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/factory/AgentToolCollectionFactory.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/ExecutorAgent.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/ReactImplAgent.java` 中打通工具注册与重复 `tool_result` 抑制
- [ ] T016 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/printer/SSEPrinter.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageEventServiceImpl.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 中完成 MRAG 结果的工作区/历史持久化闭环
- [ ] T017 [US1] 执行 `mvn test -pl ai-agent-station-study-app -Dtest=MultiModalAgentToolTest,ConversationHistoryArtifactTest,SessionTranscriptBlockAssemblerTest -DskipTests=false`、`cd reactor-tool && python -m unittest tests.test_mrag_api` 以及 `curl.exe -N http://127.0.0.1:1601/v1/tool/mragQuery ...` 验证 US1 闭环

**Checkpoint**: User Story 1 should now support MRAG retrieval end-to-end inside the existing dialogue flow

---

## Phase 4: User Story 2 - 运维可通过现有配置启用和管理多模态工具 (Priority: P2)

**Goal**: 让 MRAG 工具像当前其他工具一样，由现有配置体系控制启停、说明、参数和外部地址

**Independent Test**: 修改 `tool_list.default`、工具描述和 `multimodalagent_url` 后，新建 REACT / PlanSolve 会话可立即体现开启、关闭和配置变更效果，`dataAgent` 路径保持不暴露

### Tests for User Story 2

- [X] T018 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/spring/ai/AgentToolCollectionFactoryTest.java` 和 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/spring/ai/ReactorConfigMultiModalAgentTest.java` 中补充默认启用、移除 `multimodalagent`、`dataAgent` 隔离和工具描述/参数读取测试

### Implementation for User Story 2

- [X] T019 [P] [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/MultiModalAgent.java` 中将工具描述、参数定义和外部地址完全收口到配置读取逻辑
- [X] T020 [P] [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/factory/AgentToolCollectionFactory.java` 和 `ai-agent-station-study-app/src/main/resources/application-dev.yml` 中实现 `tool_list.default` 驱动的默认注册/关闭逻辑，并保持 `dataAgent` 现有工具语义不变
- [ ] T021 [US2] 在 `specs/009-mrag-capability-port/quickstart.md` 中同步实际生效的配置键、启停方式和本地联调步骤，确保运维说明与实现一致
- [ ] T022 [US2] 执行 `mvn test -pl ai-agent-station-study-app -Dtest=AgentToolCollectionFactoryTest,ReactorConfigMultiModalAgentTest -DskipTests=false` 并按 `specs/009-mrag-capability-port/quickstart.md` 完成配置启停冒烟验证

**Checkpoint**: User Stories 1 and 2 should now both work, with configuration-driven enable/disable behavior

---

## Phase 5: User Story 3 - 上游异常不会拖垮既有 Agent 流程 (Priority: P3)

**Goal**: 让 MRAG 在空输入、超时、上游失败和流式异常时明确失败并安全收口，不挂起、不静默降级

**Independent Test**: 模拟空问题、服务不可达、超时、心跳/空片段、混合格式响应等异常，确认会话明确结束、不会自动退回普通搜索，且普通工具链路和历史回放保持稳定

### Tests for User Story 3

- [ ] T023 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/MultiModalAgentToolTest.java` 和 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java` 中补充空输入、超时、Malformed SSE 与不自动降级的失败测试
- [X] T024 [P] [US3] 在 `reactor-tool/tests/test_mrag_api.py` 中补充空请求、上游异常、默认知识库回退和流结束标记的边界测试

### Implementation for User Story 3

- [X] T025 [P] [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/MultiModalAgent.java` 中补齐超时取消、心跳/空片段容错、双格式解析回退和明确失败文案
- [X] T026 [P] [US3] 在 `reactor-tool/reactor_tool/api/tool.py`、`reactor-tool/reactor_tool/model/protocal.py` 和 `reactor-tool/reactor_tool/tool/mrag/query/query_processor.py` 中补齐输入校验、默认 `kb_id` 回退和上游错误透出
- [ ] T027 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageEventServiceImpl.java` 中确保失败事件、最终状态和历史回放不会把异常请求伪装成成功
- [ ] T028 [US3] 执行 `mvn test -pl ai-agent-station-study-app -Dtest=MultiModalAgentToolTest,ConversationHistoryPersistenceTest,AgentToolCollectionFactoryTest,SessionTranscriptBlockAssemblerTest -DskipTests=false`、`cd reactor-tool && python -m unittest tests.test_mrag_api tests.test_script_runner` 以及普通搜索/报告/文件的手工回归验证

**Checkpoint**: All user stories should now be independently functional with explicit failure handling

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 收口跨故事的文档、一致性和最终验收

- [ ] T029 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/MultiModalAgent.java` 和 `reactor-tool/reactor_tool/tool/mrag/query/query_processor.py` 中清理重复逻辑并补齐必要的中文注释
- [ ] T030 [P] 对照 `specs/009-mrag-capability-port/contracts/mrag-query.openapi.yaml`、`specs/009-mrag-capability-port/contracts/multimodalagent-tool.schema.json`、`reactor-tool/reactor_tool/api/tool.py` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/MultiModalAgent.java` 校验契约与实现一致性
- [ ] T031 按 `specs/009-mrag-capability-port/quickstart.md` 完成最终联调验收，并记录 Java 服务、`reactor-tool`、SSE 冒烟和普通链路回归结果

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - blocks all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational completion - delivers MVP
- **User Story 2 (Phase 4)**: Depends on User Story 1 core tool path being available, because it configures the newly introduced MRAG capability
- **User Story 3 (Phase 5)**: Depends on User Story 1 core MRAG path being available; can overlap with late-stage US2 verification once the tool works end-to-end
- **Polish (Phase 6)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational - no dependency on other stories
- **User Story 2 (P2)**: Builds on US1 because config-driven enable/disable controls the new MRAG tool
- **User Story 3 (P3)**: Builds on US1 because timeout and parsing hardening target the implemented MRAG execution chain

### Within Each User Story

- Tests MUST be written and fail before implementation adjustments in that story
- Python request/route changes should land before Java tool integration that consumes them
- Tool success path must be complete before persistence / replay / workspace verification
- Failure handling should be added after the happy path is stable

### Parallel Opportunities

- T002 and T003 can run in parallel once dependency scope is clear
- T004 and T005 can run in parallel because they touch Java DTOs vs Python request protocol
- In US1, T010 and T011 can run in parallel; T012 and T013 can run in parallel; T015 and T016 can run in parallel after T014 stabilizes the response shape
- In US2, T019 and T020 can run in parallel because one focuses on config readers and the other on registration rules / YAML defaults
- In US3, T023 and T024 can run in parallel; T025 and T026 can run in parallel once failure cases are enumerated

---

## Parallel Example: User Story 1

```bash
# 先并行补测试
Task: "T010 在 reactor-tool/tests/test_mrag_api.py 中补充 /v1/tool/mragQuery SSE 契约测试"
Task: "T011 在 MultiModalAgentToolTest.java / ConversationHistoryArtifactTest.java / SessionTranscriptBlockAssemblerTest.java 中补充成功链路测试"

# 再并行实现 Python 侧主链路与路由
Task: "T012 适配 reactor-tool/reactor_tool/tool/mrag/query/ 与相关 retrieval/generation/storage 子模块"
Task: "T013 在 reactor-tool/reactor_tool/api/tool.py 中接入 /mragQuery 路由"
```

## Parallel Example: User Story 2

```bash
# 并行补配置测试与配置读取实现
Task: "T018 在 AgentToolCollectionFactoryTest.java 和 ReactorConfigMultiModalAgentTest.java 中补充配置场景"
Task: "T019 在 ReactorConfig.java 和 MultiModalAgent.java 中完成配置驱动读取"
Task: "T020 在 AgentToolCollectionFactory.java 和 application-dev.yml 中完成默认注册/关闭逻辑"
```

## Parallel Example: User Story 3

```bash
# 并行覆盖失败测试与双端异常处理
Task: "T023 在 MultiModalAgentToolTest.java 和 ConversationHistoryPersistenceTest.java 中补充失败测试"
Task: "T024 在 reactor-tool/tests/test_mrag_api.py 中补充边界测试"
Task: "T025 在 MultiModalAgent.java 中补齐超时、解析回退和失败文案"
Task: "T026 在 reactor-tool/reactor_tool/api/tool.py / protocal.py / query_processor.py 中补齐异常处理"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: 按 T017 验证现有会话内的 MRAG 闭环
5. 通过后再进入配置管理和异常加固

### Incremental Delivery

1. Complete Setup + Foundational → 建立 MRAG 迁移基础
2. Add User Story 1 → 打通前端零改动的 MRAG 主链路
3. Add User Story 2 → 让运维通过现有配置体系管理该能力
4. Add User Story 3 → 收口异常、历史回放和兼容性风险
5. Complete Phase 6 → 做最终联调与契约校验

### Parallel Team Strategy

With multiple developers:

1. 一人处理 Python MRAG 迁移（T001, T002, T005, T012, T013, T024, T026）
2. 一人处理 Java Tool / Config / Agent 装配（T004, T006, T007, T014, T015, T019, T020, T025）
3. 一人处理会话历史 / 持久化 / 回放测试（T008, T009, T011, T016, T018, T023, T027, T031）

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 每个用户故事都保留独立验收路径，避免“全部做完再一起验证”
- 任务描述已经写出真实文件路径，执行时不要把 MRAG 迁移扩散到 `ui/` 或新增数据库结构
- 参考项目存在 `multimodalagent_tool` / `knowledge_tool` 命名不一致，实施时以当前计划中的真实工具名和契约为准
