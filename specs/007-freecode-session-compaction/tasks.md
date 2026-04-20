# Tasks: 对齐 free-code 的请求前会话压缩

**Input**: Design documents from `/specs/007-freecode-session-compaction/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: 本特性明确要求补齐自动化回归，因此每个用户故事都包含对应测试任务。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., [US1], [US2], [US3])
- 任务描述必须包含真实文件路径

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 锁定本次实现涉及的入口、配置、持久化和测试落点，避免跨层误改

- [X] T001 盘点并确认本特性主链路涉及的入口与核心文件：`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryCompactionService.java`
- [X] T002 明确本次持久化与配置联动文件：`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentSessionMemory.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentSessionMemoryDao.java`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_session_memory_mapper.xml`、`ai-agent-station-study-app/src/main/resources/db/schema.sql`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java`
- [X] T003 [P] 明确本次测试基线与新增用例落点：`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryCompactionServiceTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionWorkingMemoryAssemblerTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryReopenResumeTest.java`、`ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/AgentStreamPersistServiceSessionGuardTest.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 先完成版本化快照、配置绑定和公共运行时模型，后续三个用户故事都依赖这层基础设施

- [X] T004 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java` 演进 `session-memory.*` 配置，补齐 `hard-limit-tokens`、`recent-window-max-tokens`、`recent-window-min-messages`、`max-consecutive-failures` 等请求前压缩参数
- [X] T005 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentSessionMemory.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentSessionMemoryDao.java` 演进快照版本化模型与 DAO 语义，提供 `insert`、`queryLatestBySessionId`、`queryHistoryBySessionId`
- [X] T006 [P] 在 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_session_memory_mapper.xml` 与 `ai-agent-station-study-app/src/main/resources/db/schema.sql` 移除 `session_id` 唯一覆盖写语义，改为 append-only 版本快照和最新/历史查询索引
- [X] T007 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IAgentSessionMemoryService.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java` 增加请求前 `prepareForRequest` 能力与公共返回模型，统一承载 compaction decision、working memory 和拒绝原因
- [X] T008 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionWorkingMemoryAssembler.java` 与新增的 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/sessionmemory/` 运行时模型中落地“最新快照 + 边界后消息/事件”的统一重建骨架
- [X] T009 明确 Foundational 阶段回归入口，在 `specs/007-freecode-session-compaction/quickstart.md` 对齐 `mvn test -pl ai-agent-station-study-app -DskipTests=false` 与请求前压缩验收路径

**Checkpoint**: 版本化快照、请求前准备接口和 working memory 重建骨架准备完成，用户故事可开始实现

---

## Phase 3: User Story 1 - 请求开始前自动判断并压缩 (Priority: P1) 🎯 MVP

**Goal**: 让 `REACT / PLAN_SOLVE` 在真正插入占位消息和发起主执行前完成 compaction decision，并在失败时按规则降级或拒绝

**Independent Test**: 构造达到阈值的会话，验证第二轮请求在主执行前先做 compaction；压缩失败时未超硬上限则继续，超限则拒绝且不写占位消息

### Tests for User Story 1

- [X] T010 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/AgentStreamPersistServiceSessionGuardTest.java` 增加“请求前 compaction preflight 在占位消息插入前执行”的守卫测试
- [X] T011 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/AgentStreamPersistWorkingMemoryMessagesTest.java` 增加达到阈值时先压缩再构建请求消息的回归测试
- [X] T012 [P] [US1] 新增 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryPreparationServiceTest.java`，覆盖 `BYPASS`、`COMPACTED`、`DEGRADED_CONTINUE`、`REJECTED`、`SKIPPED_CIRCUIT_OPEN` 决策分支

### Implementation for User Story 1

- [X] T013 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 前移非 `CHAT` 会话的 session memory preflight，确保拒绝路径发生在占位消息插入之前
- [X] T014 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java` 实现请求前 compaction 决策、失败节流和拒绝原因封装，并移除 `persistTurnAndEvents()` 完成后立即刷新快照的主路径
- [X] T015 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryCompactionService.java` 增加基于真实 working memory 体量的阈值判断、硬上限回退与 circuit breaker 逻辑
- [X] T016 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionWorkingMemoryAssembler.java` 打通 preflight 结果到最终 `AgentRequest` 所需工作记忆的装配，保证 `CHAT` 模式维持现状
- [X] T017 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 与 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java` 补齐请求前压缩日志与失败原因记录
- [X] T018 [US1] 运行 `mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=AgentStreamPersistServiceSessionGuardTest,AgentStreamPersistWorkingMemoryMessagesTest,SessionMemoryPreparationServiceTest` 验证 P1 闭环

**Checkpoint**: 请求前 compaction 已成为主路径，且拒绝/降级行为独立可验证

---

## Phase 4: User Story 2 - 会话压缩摘要升级为结构化工作记忆 (Priority: P2)

**Goal**: 用 free-code 风格结构化 session memory 替换旧的字符串拼接摘要，并以追加写入方式保存每次压缩结果

**Independent Test**: 触发压缩后，验证 `summary_text` 出现固定 sections，新的压缩结果新增一条 snapshot 记录，运行时默认读取最新版本

### Tests for User Story 2

- [X] T019 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryCompactionServiceTest.java` 增加结构化 session memory 生成与旧摘要兼容测试
- [X] T020 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/ConversationHistoryPersistenceTest.java` 增加 `ai_agent_session_memory` append-only 快照写入与最新版本查询测试
- [X] T021 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryReopenResumeTest.java` 增加“历史重开默认使用最新 snapshot version”的回归测试

### Implementation for User Story 2

- [X] T022 [US2] 新增 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemorySummaryGenerator.java` 与配套 prompt builder，复用现有 LLM 装配生成 free-code 风格结构化 memory
- [X] T023 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryPromptFormatter.java` 改造结构化 memory 注入格式，并兼容老 `summary_text`
- [X] T024 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryCompactionService.java` 用“旧 memory + 新增 completed transcript + 稳定 artifact refs”替换 `SessionMemorySummaryBuilder` 的旧摘要算法
- [X] T025 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentSessionMemoryDao.java`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_session_memory_mapper.xml` 上切换到“压缩成功新增 snapshot、运行时读取最新 snapshot”的存储语义
- [X] T026 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemorySummaryBuilder.java` 清理或降级旧拼接逻辑，避免与新的结构化生成路径双轨并存
- [X] T027 [US2] 运行 `mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=SessionMemoryCompactionServiceTest,ConversationHistoryPersistenceTest,SessionMemoryReopenResumeTest` 验证 P2 闭环

**Checkpoint**: 结构化 session memory 与 append-only snapshot 已独立生效，旧快照仍可兼容读取

---

## Phase 5: User Story 3 - 压缩后仍保留最近真实上下文与调用关系 (Priority: P3)

**Goal**: 在压缩后保留按 token 预算裁剪的最近真实上下文窗口，并维持工具调用链和关键片段完整性

**Independent Test**: 构造含重复工具调用和长输出的会话，验证压缩后仍能正确保留最近窗口、工具顺序和稳定引用

### Tests for User Story 3

- [X] T028 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionWorkingMemoryAssemblerTest.java` 增加 token 预算窗口、最小消息窗口与工具链完整性测试
- [X] T029 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionTranscriptBlockAssemblerTest.java` 增加长输出裁剪为关键结果与稳定引用的测试
- [X] T030 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryReopenResumeTest.java` 增加“在线续聊与历史重开共享同一最近窗口语义”的测试

### Implementation for User Story 3

- [X] T031 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionWorkingMemoryAssembler.java` 实现以 token 预算为主的最近窗口选择，并保证最小真实消息窗口
- [X] T032 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryCompactionService.java` 与相关 transcript 组装支持类中加入 `tool_use / tool_result` 不拆断和关键片段连续性保护
- [X] T033 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java` 统一在线续聊与历史重开的“最新 snapshot + 最近窗口”重建语义
- [X] T034 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryPromptFormatter.java` 优化超长结果的关键结论与稳定引用注入，避免全文回灌
- [X] T035 [US3] 运行 `mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=SessionWorkingMemoryAssemblerTest,SessionTranscriptBlockAssemblerTest,SessionMemoryReopenResumeTest` 验证 P3 闭环

**Checkpoint**: 压缩后的最近窗口、工具链完整性与重开一致性已独立可验证

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 收敛跨故事清理、文档和全量验证

- [X] T036 [P] 更新 `specs/007-freecode-session-compaction/quickstart.md` 与必要的说明注释，确保实现后的手工验收路径与真实行为一致
- [X] T037 清理 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryCompactionService.java` 中重复逻辑并补足中文注释
- [X] T038 [P] 运行 `mvn test -pl ai-agent-station-study-app -DskipTests=false` 完整回归本期相关测试
- [X] T039 校验 `ai-agent-station-study-app/src/main/resources/db/schema.sql`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_session_memory_mapper.xml` 与运行时 DAO/服务实现的一致性

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: 无依赖，可立即开始
- **Phase 2: Foundational**: 依赖 Phase 1，完成前阻塞所有用户故事
- **Phase 3: User Story 1**: 依赖 Phase 2，是 MVP 主路径
- **Phase 4: User Story 2**: 依赖 Phase 2，可复用 US1 的 preflight 主链路
- **Phase 5: User Story 3**: 依赖 Phase 2，并建立在最新 snapshot + 结构化 memory 能力之上
- **Phase 6: Polish**: 依赖全部已选用户故事完成

### User Story Dependencies

- **US1 (P1)**: 无故事级前置依赖，但必须等待 Foundational 完成
- **US2 (P2)**: 依赖 Foundational，可与 US1 的部分测试并行，但实现上会复用 US1 已建立的 preflight 链路
- **US3 (P3)**: 依赖 Foundational，且需要复用 US1/US2 已建立的 snapshot 与 working memory 语义

### Within Each User Story

- 先补测试，再落实现
- 先改运行时模型/服务，再改入口时序
- 先改持久化语义，再依赖最新快照实现重开与续聊
- 完成每个故事后都先跑该故事对应回归

### Parallel Opportunities

- `T003` 可与 `T001-T002` 并行
- `T005-T006` 可并行推进，因为分别落在领域持久化模型和 app 层资源
- 每个用户故事中的测试任务可并行编写
- `T036` 与 `T038` 可并行准备，但最终需在完整实现后统一确认

---

## Parallel Example: User Story 1

```bash
# 可以并行准备的 P1 测试
Task: "在 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/AgentStreamPersistServiceSessionGuardTest.java 增加 preflight 守卫测试"
Task: "在 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/AgentStreamPersistWorkingMemoryMessagesTest.java 增加请求前压缩消息装配测试"
Task: "新增 ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/sessionmemory/SessionMemoryPreparationServiceTest.java 覆盖决策分支"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational
3. 完成 Phase 3: User Story 1
4. 验证请求前 compaction、拒绝路径不插入占位消息
5. 若主路径稳定，再继续 US2 / US3

### Incremental Delivery

1. 先交付“请求前压缩决策”主链路
2. 再交付“结构化 session memory + append-only snapshot”
3. 最后交付“最近窗口 token 预算与工具链完整性”
4. 每完成一个阶段都跑对应测试，避免把问题堆到末尾

### Notes

- 所有数据库与 Mapper 改动都要与 DAO / 服务实现同步
- `CHAT` 模式保持现状，不应被本次任务误伤
- 压缩失败拒绝路径必须保证无占位消息、无新 snapshot 脏数据
- 旧 snapshot 保留用于分析，因此严禁回退到覆盖写
