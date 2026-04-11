# Tasks: Fix 模式 AI 角色库

**Input**: Design documents from `/specs/001-fix-role-library/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this belongs to (`US1`, `US2`, `US3`)
- 每条任务都写出准确文件路径，便于直接开工

## Phase 1: Setup (Shared Preparation)

**Purpose**: 准备默认角色配置和本地联调入口，避免后续实现时反复返工

- [ ] T001 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java` 与 `ai-agent-station-study-app/src/main/resources/application.yml`、`ai-agent-station-study-app/src/main/resources/application-dev.yml`、`ai-agent-station-study-app/src/main/resources/application-test.yml`、`ai-agent-station-study-app/src/main/resources/application-prod.yml` 中补充默认 chat 角色配置读取与示例值
- [ ] T002 在 `ai-agent-station-study-app/src/main/resources/db/schema.sql` 明确会话表新增字段的初始化脚本与注释，保证本地和测试环境可以直接建表验证

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 完成角色绑定和角色库能力的基础模型，阻塞后续所有用户故事

**⚠️ CRITICAL**: 本阶段完成前，不要开始用户故事实现

- [ ] T003 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentConversation.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentConversationDao.java`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_conversation_mapper.xml` 中增加 `aiAgentId` / `aiAgentNameSnapshot` 的实体映射、插入与查询更新逻辑
- [ ] T004 [P] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/req/GptQueryReq.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/req/AgentRequest.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/MessageSendReqVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationCreateReqVO.java` 中增加 `aiAgentId` 请求字段，打通角色参数传递基础通道
- [ ] T005 [P] 新增 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/model/valobj/FixRoleVO.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/model/valobj/ConversationRoleVO.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/IFixRoleService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/role/FixRoleService.java`，沉淀 Fix 角色列表、默认角色和会话角色摘要的领域模型
- [ ] T006 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/adapter/repository/IAgentRepository.java`、`ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/repository/AgentRepository.java`、`ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/IAiAgentDao.java`、`ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/IAiAgentFlowConfigDao.java`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_mapper.xml`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_flow_config_mapper.xml` 中补充 Fix 角色库专用查询与可用性校验所需 DAO 能力
- [ ] T007 在 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationRoleRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/FixRoleRespVO.java` 中新增前端可复用的角色响应对象，统一会话摘要与角色列表输出结构

**Checkpoint**: 角色绑定字段、角色请求通道、角色库领域模型和 repository 查询入口已就绪

---

## Phase 3: User Story 1 - 选择角色开始对话 (Priority: P1) 🎯 MVP

**Goal**: 用户能在 chat 模式选择角色并真实走对应 Fix 策略开始新对话

**Independent Test**: 进入 chat 模式后选择任一角色发送首条消息，返回结果体现所选角色的 Fix 流程配置；不选角色时默认走第一角色

### Tests for User Story 1

- [ ] T008 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/FixedAgentExecuteStrategyTest.java` 增加“按会话绑定角色执行、默认角色回退、不再写死 agentId=1”的测试场景
- [ ] T009 [P] [US1] 新增 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/FixRoleServiceTest.java`，覆盖默认角色排序、无可用角色、角色不可用时的领域校验

### Implementation for User Story 1

- [ ] T010 [P] [US1] 在 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentRoleLibraryController.java` 中新增 `GET /api/agent/role-library/list` 接口，并用 `FixRoleRespVO` 输出角色库列表
- [ ] T011 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/fixed/FixedAgentExecuteStrategy.java` 中移除写死的 `repository.queryAiAgentClientsByAgentId(\"1\")`，改为按会话/请求解析后的 `aiAgentId` 加载 Fix 流程配置
- [ ] T012 [US1] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentMessageController.java` 中实现 chat 首发消息的角色解析、默认角色兜底、消息错误态输出与 Fix 执行参数透传
- [ ] T013 [P] [US1] 在 `ui/src/services/agentConversation.ts`、`ui/src/types/chat.ts` 中补充角色库 API、会话角色字段和前端本地角色类型
- [ ] T014 [P] [US1] 新增 `ui/src/components/ChatRoleSelector/index.tsx`，实现 chat 模式角色库按钮、角色列表弹层和默认角色展示
- [ ] T015 [US1] 在 `ui/src/components/GeneralInput/index.tsx`、`ui/src/pages/Home/index.tsx`、`ui/src/components/ChatView/index.tsx` 中接入 `ChatRoleSelector`，让欢迎页与会话输入区都能选择角色
- [ ] T016 [US1] 在 `ui/src/hooks/useAgentConversation.ts`、`ui/src/utils/querySSE.ts` 中透传 `aiAgentId`，确保首发消息和新建 chat 草稿都能带着角色发送
- [ ] T017 [US1] 执行 `mvn test -pl ai-agent-station-study-app -Dtest=FixedAgentExecuteStrategyTest,FixRoleServiceTest`、`cd ui && pnpm lint`、`cd ui && pnpm build`，并按 `specs/001-fix-role-library/quickstart.md` 完成“选角开始对话/默认角色开始对话”冒烟验证

**Checkpoint**: 用户已经可以在 chat 模式选角色并开始一段真实的 Fix 对话，这是首个可演示 MVP

---

## Phase 4: User Story 2 - 基于现有智能体记录维护角色库 (Priority: P2)

**Goal**: 角色库继续从现有 `ai_agent` 与相关配置自动投影出来，不新增平行角色主数据

**Independent Test**: 调整现有智能体启用状态或 Fix 相关配置后，角色库列表会自动变化，无需额外维护角色表

### Tests for User Story 2

- [ ] T018 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/dao/AiAgentDaoTest.java` 中增加 Fix 角色库查询、默认角色排序和停用角色过滤的 DAO 验证
- [ ] T019 [P] [US2] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/dao/AiAgentFlowConfigDaoTest.java` 中增加“缺少 FlowConfig / FlowConfig 不可用时不入库”的查询验证

### Implementation for User Story 2

- [ ] T020 [US2] 在 `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/AiAgent.java`、`ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/AiAgentFlowConfig.java`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_mapper.xml`、`ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_flow_config_mapper.xml` 中补齐 Fix 角色可用性判定所需字段映射和查询结果
- [ ] T021 [US2] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/IArmoryService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/ArmoryService.java`、`ai-agent-station-study-app/src/main/java/org/wwz/ai/config/AiAgentAutoConfiguration.java` 中明确区分“通用可用智能体查询”和“Fix 角色库查询”，避免角色库逻辑污染现有装配链路
- [ ] T022 [US2] 在 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/AiAgentController.java` 与 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentRoleLibraryController.java` 中保持 `/query_available_agents` 兼容不变，同时让新角色库接口只暴露可真正执行的 Fix 角色
- [ ] T023 [US2] 按 `specs/001-fix-role-library/contracts/role-library.md` 手工验证“启用角色自动入库、停用角色自动退出、默认角色始终第一”的角色库维护路径

**Checkpoint**: 角色库已经完全建立在现有智能体记录与关联配置之上，运营维护不需要双份数据

---

## Phase 5: User Story 3 - 会话中持续保留所选角色 (Priority: P3)

**Goal**: 一段会话绑定一个角色，刷新、历史恢复和继续对话都能沿用该角色；失效角色也有稳定回退提示

**Independent Test**: 打开已有角色会话刷新后仍能恢复角色；在原会话中试图换角色会被阻止并引导新建会话

### Tests for User Story 3

- [ ] T024 [P] [US3] 新增 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentConversationRoleBindingTest.java`，覆盖会话创建绑定角色、历史会话默认回退、会话内切角拒绝、角色失效提示
- [ ] T025 [P] [US3] 在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/FixRoleServiceTest.java` 中补充“历史会话角色已失效但名称快照仍可展示”的场景

### Implementation for User Story 3

- [ ] T026 [P] [US3] 在 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationListRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationDetailRespVO.java`、`ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationController.java` 中返回会话角色摘要 `role`
- [ ] T027 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IAgentConversationService.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentConversationServiceImpl.java` 中实现角色绑定创建、角色摘要组装、历史 chat 会话默认回退与名称快照写回
- [ ] T028 [US3] 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 中加入 `roleUnavailable`、`roleSwitchRejected`、`noAvailableChatRole` 终态错误处理，并保证错误消息也能按现有持久化链路落库
- [ ] T029 [P] [US3] 在 `ui/src/services/agentConversation.ts`、`ui/src/hooks/useAgentConversation.ts` 中恢复历史会话角色字段，并把角色摘要同步到本地会话状态
- [ ] T030 [US3] 在 `ui/src/pages/Home/index.tsx`、`ui/src/components/ChatView/index.tsx`、`ui/src/components/ChatRoleSelector/index.tsx` 中实现“已有消息会话切换角色时自动新建会话、历史角色失效时提示不可继续发送”的交互
- [ ] T031 [US3] 按 `specs/001-fix-role-library/quickstart.md` 完成“刷新恢复角色、历史会话兼容、失效角色阻断继续发送”的端到端验证

**Checkpoint**: 角色已经稳定绑定到会话，历史恢复和错误回退都可独立验证

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 收尾清理、统一验证与文档对齐

- [ ] T032 [P] 清理 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java`、`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/fixed/FixedAgentExecuteStrategy.java`、`ui/src/components/GeneralInput/index.tsx`、`ui/src/components/ChatView/index.tsx` 中与角色相关的重复逻辑，并补充必要中文注释
- [ ] T033 [P] 复核 `specs/001-fix-role-library/plan.md`、`specs/001-fix-role-library/research.md`、`specs/001-fix-role-library/data-model.md`、`specs/001-fix-role-library/contracts/` 与最终实现是否一致，如有偏差同步更新文档
- [ ] T034 执行完整回归：`mvn test -pl ai-agent-station-study-app -Dtest=FixedAgentExecuteStrategyTest,FixRoleServiceTest,AgentConversationRoleBindingTest,AiAgentDaoTest,AiAgentFlowConfigDaoTest`、`cd ui && pnpm lint`、`cd ui && pnpm build`，并按 quickstart 完成最终验收

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: 可立即开始
- **Phase 2: Foundational**: 依赖 Setup，且阻塞所有用户故事
- **Phase 3: US1**: 依赖 Foundational 完成，是 MVP 主链路
- **Phase 4: US2**: 依赖 Foundational；建议在 US1 可演示后继续
- **Phase 5: US3**: 依赖 Foundational；可在 US1 后并行推进
- **Phase 6: Polish**: 依赖所有目标用户故事完成

### User Story Dependencies

- **US1**: 无故事级前置依赖，是首个 MVP
- **US2**: 复用 Foundational 的角色库基础能力，与 US1 后端角色查询存在协同
- **US3**: 复用 Foundational 的会话角色绑定模型，并依赖 US1 的角色发送主链路

### Parallel Opportunities

- T004 与 T005 可并行
- T008 与 T009 可并行
- T013 与 T014 可并行
- T018 与 T019 可并行
- T024 与 T025 可并行
- T029 可与 T026/T027 并行推进
- T032 与 T033 可并行

---

## Implementation Strategy

### MVP First

1. 完成 Phase 1-2，先把角色绑定字段、默认角色配置和角色库 service 搭好
2. 完成 Phase 3，让 chat 模式可以选角色并真实开始 Fix 对话
3. 先做一次 MVP 演示与冒烟，再进入 US2 / US3

### Incremental Delivery

1. **MVP**: US1 交付“能选、能发、真生效”
2. **运营可维护**: US2 交付“基于现有 ai_agent 自动维护角色库”
3. **体验闭环**: US3 交付“会话记住角色、刷新可恢复、失效有提示”

### Notes

- `[P]` 任务只表示文件集可并行，不代表可以跳过前置验证
- 如实现过程中发现 `ai_agent_flow_config` 的真实字段映射与当前 mapper 不一致，先在 Foundational 阶段补齐，再继续故事开发
- 每个故事完成后都先按 quickstart 做独立验收，避免把问题积压到最后一起排查
