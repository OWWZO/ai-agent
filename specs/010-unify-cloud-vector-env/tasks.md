# Tasks: 统一 DataAgent 与 MRAG 的云端向量环境

**Input**: Design documents from `/specs/010-unify-cloud-vector-env/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: 本特性的规格已经显式定义独立验收标准、异常演练和 quickstart 验证路径，因此任务单包含 Java 定向测试、Python `unittest` 冒烟和手工联调验证。

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

**Purpose**: 为共享云端向量环境改造准备测试骨架和统一配置入口

- [X] T001 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/CloudVectorConfigBindingTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/DataAgentInitRunnerRefreshTest.java`、`reactor-tool/tests/test_embedding_proxy.py` 和 `reactor-tool/tests/test_table_rag_shared_config.py` 创建本特性测试骨架与共享环境夹具
- [X] T002 [P] 在 `ai-agent-station-study-app/src/main/resources/application-dev.yml` 和 `ai-agent-station-study-app/src/main/resources/application-test.yml` 预留共享 `QDRANT_*`、`TEXT_EMBEDDING_*`、`TR_ES_CONFIGS_*` 与 `DATA_AGENT_FORCE_REFRESH` 的示例绑定

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 建立所有用户故事共用的配置绑定、内部契约与底层接入骨架

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/data/QdrantConfig.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/data/EsConfig.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/data/DataAgentConfig.java` 增加 `url`、`preferGrpc`、`scheme`、`forceRefresh` 等共享云端字段并完成配置绑定
- [X] T004 [P] 在 `reactor-tool/reactor_tool/model/protocal.py` 中新增 `EmbeddingProxyRequest` / `EmbeddingProxyResponse`，并让字段与 `specs/010-unify-cloud-vector-env/contracts/embedding-text.openapi.yaml` 对齐
- [X] T005 [P] 在 `reactor-tool/reactor_tool/api/tool.py` 和 `reactor-tool/server.py` 注册 `/v1/tool/embedding/text` 路由骨架与服务装配入口
- [X] T006 [P] 在 `reactor-tool/reactor_tool/util/qdrant_utils.py` 和 `reactor-tool/reactor_tool/tool/mrag/storage/qdrant_vector_store.py` 抽出共享 Qdrant 配置解析辅助逻辑，作为 MRAG 与 `table_rag` 统一回退的底层入口
- [X] T007 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/EmbeddingService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/QdrantService.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/util/ESUtil.java` 预留 embedding 代理、云端 Qdrant URL/TLS 和 ES `scheme` 的公共接入点
- [X] T008 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/CloudVectorConfigBindingTest.java` 和 `reactor-tool/tests/test_embedding_proxy.py` 先补共享配置绑定与内部 embedding 契约的基础测试，并确保在实现前失败

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - 单套云端配置启用两条检索链路 (Priority: P1) 🎯 MVP

**Goal**: 仅通过共享云端配置就让 MRAG 与 DataAgent 同时接通云端 Qdrant、共享文本向量和共享 ES，而不再依赖独立 embedding 服务

**Independent Test**: 填写共享云端配置并启动 Java 服务与 `reactor-tool` 后，分别执行一次 MRAG 检索和一次 DataAgent 问数请求，确认两条链路都连到同一套云端服务并返回可用结果

### Tests for User Story 1

- [X] T009 [P] [US1] 在 `reactor-tool/tests/test_embedding_proxy.py` 和 `reactor-tool/tests/test_mrag_api.py` 中补充共享 embedding 代理和 MRAG 复用共享 Qdrant 配置的接口测试
- [X] T010 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/QdrantServiceCloudClientTest.java` 和 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/EmbeddingServiceProxyTest.java` 中补充 Qdrant URL/TLS 解析、批量向量代理调用和云端配置下 DataAgent 召回的定向测试

### Implementation for User Story 1

- [X] T011 [P] [US1] 在 `reactor-tool/reactor_tool/tool/mrag/embedding/text_embedding.py` 和 `reactor-tool/reactor_tool/api/tool.py` 实现 `/v1/tool/embedding/text` 的批量文本向量代理与 `normalize=true` 的 L2 归一化返回
- [X] T012 [P] [US1] 在 `reactor-tool/reactor_tool/tool/mrag/storage/qdrant_vector_store.py` 和 `reactor-tool/reactor_tool/util/qdrant_utils.py` 统一 MRAG 侧共享 `QDRANT_URL`、`QDRANT_PORT`、`QDRANT_API_KEY`、`QDRANT_PREFER_GRPC` 的云端解析逻辑
- [X] T013 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/QdrantService.java` 实现 `QDRANT_URL` 的 host/port/TLS 解析与云端 gRPC 客户端初始化，并保留旧 host/port 直连回退
- [X] T014 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/EmbeddingService.java` 和 `ai-agent-station-study-app/src/main/resources/application-dev.yml` 让 Java 默认走 `autobots.data-agent.agent-url + /v1/tool/embedding/text`，同时保留旧显式 embedding URL override
- [X] T015 [P] [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/data/EsConfig.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/util/ESUtil.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/SchemaRecallService.java` 接通共享 ES `scheme` 并修复列值召回字段 `modelCode`
- [X] T016 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/DataAgentInitRunner.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/TableRagService.java` 打通共享配置下的启动校验、可用性日志和问数主链路调用
- [ ] T017 [US1] 运行 `mvn test -pl ai-agent-station-study-app -Dtest=CloudVectorConfigBindingTest,QdrantServiceCloudClientTest,EmbeddingServiceProxyTest -DskipTests=false`、`cd reactor-tool && uv run python -m unittest tests.test_embedding_proxy tests.test_mrag_api`，并结合 `ai-agent-station-study-app/src/main/resources/application-dev.yml` 完成一次 MRAG + DataAgent 联调验证

**Checkpoint**: User Story 1 完成后，应能只靠共享云端配置跑通 MRAG 与 DataAgent 的最小闭环

---

## Phase 4: User Story 2 - 迁移期间保持兼容与可覆盖 (Priority: P2)

**Goal**: 在启用统一云端配置的同时，继续兼容旧本地 host/port、旧 embedding override 和 `table_rag` 专属覆盖配置

**Independent Test**: 分别验证“仅旧本地配置”“仅共享云端配置”“共享配置 + `table_rag` override”三种模式，确认三种模式都能独立工作且互不污染

### Tests for User Story 2

- [X] T018 [P] [US2] 在 `reactor-tool/tests/test_table_rag_shared_config.py` 和 `reactor-tool/tests/test_embedding_proxy.py` 中补充“仅共享配置”“共享配置 + TR_QDRANT_* override”“旧 TR_EMBEDDING_URL override”三类兼容测试
- [X] T019 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/LegacyVectorCompatTest.java` 中补充旧 host/port 模式、旧 embedding URL 和共享云端配置并存时的 Java 兼容测试

### Implementation for User Story 2

- [X] T020 [P] [US2] 在 `reactor-tool/reactor_tool/tool/table_rag/retriever.py` 和 `reactor-tool/reactor_tool/tool/table_rag/qdrant_recall.py` 实现 `TR_QDRANT_*` 优先、缺省回退共享 `QDRANT_*` 的解析顺序，并保留 `TR_QDRANT_URL` 作为外部向量召回 HTTP 服务地址语义
- [X] T021 [P] [US2] 在 `reactor-tool/reactor_tool/tool/table_rag/es_client.py` 和 `reactor-tool/reactor_tool/tool/table_rag/retriever.py` 统一 `TR_ES_CONFIGS_SCHEME` 读取，并保持未配置 ES 时的安全降级行为
- [X] T022 [P] [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/EmbeddingService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/QdrantService.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/data/QdrantConfig.java` 保留旧本地 host/port 与旧 embedding override 路径，不让共享配置破坏存量环境
- [X] T023 [US2] 在 `specs/010-unify-cloud-vector-env/quickstart.md` 和 `ai-agent-station-study-app/src/main/resources/application-dev.yml` 同步共享配置、override 优先级和迁移示例，确保运维说明与实现一致
- [ ] T024 [US2] 运行 `mvn test -pl ai-agent-station-study-app -Dtest=LegacyVectorCompatTest,CloudVectorConfigBindingTest -DskipTests=false`、`cd reactor-tool && uv run python -m unittest tests.test_table_rag_shared_config tests.test_embedding_proxy`，并按 `specs/010-unify-cloud-vector-env/quickstart.md` 验证三种配置模式

**Checkpoint**: User Stories 1 and 2 完成后，应能在统一配置与旧配置之间平滑迁移

---

## Phase 5: User Story 3 - 显式重建保障远端数据一致性 (Priority: P3)

**Goal**: 提供显式 `force-refresh` 重建与常规启动降级机制，保证远端 Qdrant / ES 状态与当前 `model-list` 严格对齐

**Independent Test**: 预置“本地元数据存在但远端为空”的环境，执行一次显式刷新验证远端被重建；再验证不触发刷新时不会破坏远端，且增强能力失败时 DataAgent 会退回基础模式

### Tests for User Story 3

- [X] T025 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/DataAgentInitRunnerRefreshTest.java` 和 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/DataAgentCapabilityDegradeTest.java` 中补充强制刷新、失败即终止、常规启动降级和 stale remote cleanup 测试
- [X] T026 [P] [US3] 在 `reactor-tool/tests/test_embedding_proxy.py` 中补充共享 embedding 上游失败、超时和错误映射为 502/504 的边界测试，确保 Java 能拿到可定位失败结果

### Implementation for User Story 3

- [X] T027 [P] [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/DataAgentInitRunner.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/data/DataAgentConfig.java` 实现 `forceRefresh` 开关、当前 `model-list` 过滤和常规启动/刷新分支
- [X] T028 [P] [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ChatModelInfoService.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/QdrantService.java` 实现按当前 `model-list` 重建 schema 向量、删除陈旧远端点位和刷新失败即终止
- [X] T029 [P] [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ColumnValueSyncService.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/SchemaRecallService.java` 实现列值索引重建、陈旧 ES 数据清理和增强能力失败时的可定位错误透出
- [X] T030 [P] [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/TableRagService.java` 和 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/DataAgentInitRunner.java` 实现增强能力降级后的基础 schema / 基础问数回退，不整体禁用 DataAgent 主流程
- [X] T031 [P] [US3] 在 `reactor-tool/reactor_tool/api/tool.py` 和 `reactor-tool/reactor_tool/tool/mrag/embedding/text_embedding.py` 完成 embedding 上游失败/超时的错误映射与明确失败消息
- [ ] T032 [US3] 运行 `mvn test -pl ai-agent-station-study-app -Dtest=DataAgentInitRunnerRefreshTest,DataAgentCapabilityDegradeTest,LegacyVectorCompatTest -DskipTests=false`、`cd reactor-tool && uv run python -m unittest tests.test_embedding_proxy tests.test_table_rag_shared_config`，并按 `specs/010-unify-cloud-vector-env/quickstart.md` 验证强制刷新与降级闭环

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 收口契约、文档、一致性与最终验收

- [X] T033 [P] 对照 `specs/010-unify-cloud-vector-env/contracts/embedding-text.openapi.yaml`、`specs/010-unify-cloud-vector-env/quickstart.md`、`ai-agent-station-study-app/src/main/resources/application-dev.yml` 和 `reactor-tool/reactor_tool/api/tool.py` 校验契约、文档与实现一致性
- [X] T034 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/QdrantService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/DataAgentInitRunner.java`、`reactor-tool/reactor_tool/tool/table_rag/retriever.py` 和 `reactor-tool/reactor_tool/tool/mrag/embedding/text_embedding.py` 清理重复逻辑并补必要的中文注释
- [ ] T035 按 `specs/010-unify-cloud-vector-env/quickstart.md` 完成最终联调，记录“共享云端配置”“兼容 override”“force-refresh”“降级后基础问数可用”四类结果

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - blocks all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational completion - delivers MVP
- **User Story 2 (Phase 4)**: Depends on User Story 1 core shared-cloud path being available, because compatibility逻辑要围绕共享默认路径做回退
- **User Story 3 (Phase 5)**: Depends on User Story 1 core shared-cloud path being available，并复用 US2 的兼容入口做刷新与降级收口
- **Polish (Phase 6)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational - no dependency on other stories
- **User Story 2 (P2)**: Builds on US1 because override 和兼容逻辑要以共享云端路径为默认基线
- **User Story 3 (P3)**: Builds on US1 because `force-refresh` 和降级策略作用于已经接通的共享云端能力

### Within Each User Story

- 测试任务优先，先让新增断言失败，再落实现
- Python 侧内部 embedding 代理与共享 Qdrant 配置应先完成，再切换 Java DataAgent 的默认调用路径
- 兼容 override 必须建立在共享默认配置已经可用的前提上
- `force-refresh` 的重建与 stale cleanup 必须在降级逻辑之前落地，避免把错误状态误判成正常降级

### Parallel Opportunities

- T001 与 T002 可并行，一个准备测试骨架，一个准备配置样例
- T004、T005、T006、T007 在 T003 后可并行推进，因为它们分别修改 Python 协议、路由、共享工具和 Java 底层服务
- 在 US1 中，T009 与 T010 可并行；T011、T012、T013、T014、T015 可并行；T016 在前述实现稳定后再串联启动与调用链
- 在 US2 中，T018 与 T019 可并行；T020、T021、T022 可并行；T023 在实现完成后再同步文档
- 在 US3 中，T025 与 T026 可并行；T027、T028、T029、T031 可并行；T030 在增强能力状态明确后再接回退逻辑

---

## Parallel Example: User Story 1

```bash
# 先并行补云端主链路测试
Task: "T009 在 reactor-tool/tests/test_embedding_proxy.py 和 reactor-tool/tests/test_mrag_api.py 中补共享云端测试"
Task: "T010 在 QdrantServiceCloudClientTest.java 和 EmbeddingServiceProxyTest.java 中补 Java 定向测试"

# 再并行落共享接入实现
Task: "T011 在 reactor-tool/reactor_tool/tool/mrag/embedding/text_embedding.py 和 reactor-tool/reactor_tool/api/tool.py 中实现 embedding 代理"
Task: "T012 在 reactor-tool/reactor_tool/tool/mrag/storage/qdrant_vector_store.py 和 reactor-tool/reactor_tool/util/qdrant_utils.py 中统一 Qdrant 云端解析"
Task: "T013 在 ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/QdrantService.java 中实现 URL/TLS 初始化"
Task: "T014 在 ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/EmbeddingService.java 和 application-dev.yml 中切默认代理"
```

## Parallel Example: User Story 2

```bash
# 并行补兼容测试与 override 实现
Task: "T018 在 reactor-tool/tests/test_table_rag_shared_config.py 和 reactor-tool/tests/test_embedding_proxy.py 中补兼容测试"
Task: "T019 在 LegacyVectorCompatTest.java 中补 Java 兼容测试"
Task: "T020 在 reactor-tool/reactor_tool/tool/table_rag/retriever.py 和 qdrant_recall.py 中实现回退顺序"
Task: "T021 在 reactor-tool/reactor_tool/tool/table_rag/es_client.py 和 retriever.py 中实现 ES scheme 兼容"
Task: "T022 在 EmbeddingService.java / QdrantService.java / QdrantConfig.java 中保留旧配置路径"
```

## Parallel Example: User Story 3

```bash
# 并行补刷新/降级测试与底层实现
Task: "T025 在 DataAgentInitRunnerRefreshTest.java 和 DataAgentCapabilityDegradeTest.java 中补强制刷新与降级测试"
Task: "T026 在 reactor-tool/tests/test_embedding_proxy.py 中补上游失败映射测试"
Task: "T027 在 DataAgentInitRunner.java 和 DataAgentConfig.java 中实现 forceRefresh 分支"
Task: "T028 在 ChatModelInfoService.java 和 QdrantService.java 中实现重建与 stale cleanup"
Task: "T029 在 ColumnValueSyncService.java 和 SchemaRecallService.java 中实现 ES 重建与错误透出"
Task: "T031 在 reactor-tool/reactor_tool/api/tool.py 和 text_embedding.py 中完成 502/504 映射"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: 按 T017 验证“单套云端配置启用两条检索链路”
5. 通过后再进入兼容迁移和强制刷新

### Incremental Delivery

1. Complete Setup + Foundational → 建立统一云端向量环境的基础入口
2. Add User Story 1 → 先跑通共享云端配置闭环（MVP）
3. Add User Story 2 → 再补兼容旧环境与 override 迁移能力
4. Add User Story 3 → 最后补强制刷新、降级与远端一致性治理
5. Complete Phase 6 → 做契约对齐和最终联调

### Parallel Team Strategy

With multiple developers:

1. 一人负责 Python 共享 embedding/Qdrant 接入（T004, T005, T006, T011, T012, T018, T020, T021, T026, T031）
2. 一人负责 Java DataAgent 配置与底层服务（T003, T007, T010, T013, T014, T015, T019, T022, T025, T027, T028, T029, T030）
3. 一人负责测试、配置样例、文档与最终验收（T001, T002, T008, T016, T017, T023, T024, T032, T033, T034, T035）

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 每个用户故事都保留独立验收路径，避免“全部做完再一起验证”
- `TR_QDRANT_URL` 在本特性中继续表示外部向量召回 HTTP 服务地址，不与 Qdrant 实例地址混用
- 任务描述已写出真实文件路径，实施时不要把改动扩散到 `ui/` 或数据库结构
