---

description: "Task list template for feature implementation"
---

# Tasks: [FEATURE NAME]

**Input**: Design documents from `/specs/[###-feature-name]/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: The examples below include test tasks. Tests are OPTIONAL - only include them if explicitly requested in the feature specification.

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

<!-- 
  ============================================================================
  IMPORTANT: The tasks below are SAMPLE TASKS for illustration purposes only.
  
  The /speckit.tasks command MUST replace these with actual tasks based on:
  - User stories from spec.md (with their priorities P1, P2, P3...)
  - Feature requirements from plan.md
  - Entities from data-model.md
  - Endpoints from contracts/
  
  Tasks MUST be organized by user story so each story can be:
  - Implemented independently
  - Tested independently
  - Delivered as an MVP increment
  
  DO NOT keep these sample tasks in the generated tasks.md file.
  ============================================================================
-->

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 明确本次需求影响的模块、入口、配置与验证范围
- [ ] T002 建立或补充本特性的规格文档、研究结论和数据/契约草稿
- [ ] T003 [P] 准备本次开发所需的测试数据、配置项或联调依赖

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

Examples of foundational tasks (adjust based on your project):

- [ ] T004 调整共享领域对象、DTO、枚举或常量，补齐本特性的基础模型
- [ ] T005 [P] 如涉及持久化，先补齐 PO、DAO、Mapper XML、初始化 SQL 或配置绑定
- [ ] T006 [P] 如涉及 Agent/Tool/MCP 扩展，先补齐注册入口、策略工厂或元数据装配
- [ ] T007 建立共用错误处理、日志、事件记录或流式输出基础能力
- [ ] T008 校准受影响模块的装配关系，避免跨层依赖污染
- [ ] T009 明确回归测试入口和最小可验证闭环

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - [Title] (Priority: P1) 🎯 MVP

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 1 (OPTIONAL - only if tests requested) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T010 [P] [US1] 在 `ai-agent-station-study-app/src/test/java/...` 或对应模块下补充用例，先覆盖主链路失败场景
- [ ] T011 [P] [US1] 如涉及接口/工具协议，在契约或集成测试中验证请求、响应和异常分支

### Implementation for User Story 1

- [ ] T012 [P] [US1] 在明确模块内新增或调整核心模型/DTO/VO/枚举，写出准确文件路径
- [ ] T013 [P] [US1] 在 `domain` / `infrastructure` / `ui` / `reactor-tool` 中实现本故事最小闭环所需能力
- [ ] T014 [US1] 打通入口层到核心能力的调用链，保持层次边界清晰
- [ ] T015 [US1] 补齐验证、异常处理、事件记录或日志
- [ ] T016 [US1] 如涉及配置、数据库、Mapper、提示词或工具清单，同步完成配套修改
- [ ] T017 [US1] 执行对应回归验证并记录结果

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - [Title] (Priority: P2)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 2 (OPTIONAL - only if tests requested) ⚠️

- [ ] T018 [P] [US2] 为该故事增加独立可验证的测试或冒烟脚本
- [ ] T019 [P] [US2] 验证与 P1 故事的兼容性，不破坏既有能力

### Implementation for User Story 2

- [ ] T020 [P] [US2] 在对应模块扩展第二优先级故事所需的模型、服务或页面
- [ ] T021 [US2] 集成或复用 US1 已完成能力，避免重复实现
- [ ] T022 [US2] 补齐入口、持久化、配置或工具注册的联动修改
- [ ] T023 [US2] 完成该故事的独立验收与兼容验证

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - [Title] (Priority: P3)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 3 (OPTIONAL - only if tests requested) ⚠️

- [ ] T024 [P] [US3] 为低优先级故事补充所需测试、构建或冒烟验证
- [ ] T025 [P] [US3] 验证跨模块副作用与回退路径

### Implementation for User Story 3

- [ ] T026 [P] [US3] 完成剩余增强项或配套能力
- [ ] T027 [US3] 处理跨故事的共享逻辑抽取与清理
- [ ] T028 [US3] 完成最终回归与文档同步

**Checkpoint**: All user stories should now be independently functional

---

[Add more user story phases as needed, following the same pattern]

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] TXXX [P] 更新 `docs/`、`CLAUDE.md`、配置说明或运维文档
- [ ] TXXX 清理重复逻辑并补中文注释
- [ ] TXXX 评估性能、超时、重试、限流或缓存策略
- [ ] TXXX [P] 补充遗漏的单测/集成测试/构建校验
- [ ] TXXX 校验数据库、配置、工具协议和兼容性风险
- [ ] TXXX 按 quickstart 或手工路径完成最终验收

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - May integrate with US1/US2 but should be independently testable

### Within Each User Story

- Tests (if included) MUST be written and FAIL before implementation
- Models before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- All tests for a user story marked [P] can run in parallel
- Models within a story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together (if tests requested):
Task: "Contract test for [endpoint] in tests/contract/test_[name].py"
Task: "Integration test for [user journey] in tests/integration/test_[name].py"

# Launch all models for User Story 1 together:
Task: "Create [Entity1] model in src/models/[entity1].py"
Task: "Create [Entity2] model in src/models/[entity2].py"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2
   - Developer C: User Story 3
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- 任务描述必须写出真实文件路径，不允许只写“修改后端逻辑”这类泛化表达
- 涉及数据库、Mapper、配置、工具注册、提示词时，任务中要显式列出联动文件
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
