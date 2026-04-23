# Tasks: 修复 React/PlanSolve 最终总结 Markdown 展示

**Input**: Design documents from `/specs/008-fix-summary-markdown/`  
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: 本特性未要求 TDD；验证通过 `ui/package.json` 中的 `lint/build` 脚本和 `specs/008-fix-summary-markdown/quickstart.md` 的手工验收路径完成。  

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., [US1], [US2], [US3])
- 任务描述必须包含真实文件路径

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 锁定实现边界、验收样本和内部契约，避免开发过程中把修复范围扩散到非目标场景

- [X] T001 在 `specs/008-fix-summary-markdown/quickstart.md` 固化 `###1）`、`##你如果想要...`、`-计划玩几天` 的复现场景和非目标场景回归步骤，作为实现期间的统一验收基线
- [X] T002 在 `specs/008-fix-summary-markdown/contracts/structured-summary-rendering.md` 与 `specs/008-fix-summary-markdown/contracts/summary-normalization-rules.md` 对齐“只允许结构化总结入口启用增强修复”的实现边界

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 建立结构化总结专用的渲染边界和共享规范化入口，后续所有用户故事都依赖这层基础设施

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 在 `ui/src/utils/markdown.ts` 定义结构化总结专用的规范化选项、scope 边界和共享入口函数，保持默认渲染路径不变
- [X] T004 [P] 在 `ui/src/components/ActionPanel/MarkdownRenderer.tsx` 扩展渲染参数，支持显式传入结构化总结 scope，同时保留现有 `MessageResponse/ReactMarkdown` 默认行为
- [X] T005 [P] 在 `ui/src/components/Dialogue/index.tsx` 提取最终总结渲染所需的 scope 计算和总结文本解析辅助逻辑，为实时、完成态和历史回放提供统一入口

**Checkpoint**: 结构化总结专用渲染边界已建立，用户故事可开始实现

---

## Phase 3: User Story 1 - 结构化模式最终总结能正确排版 (Priority: P1) 🎯 MVP

**Goal**: 让 `REACT / PLAN_SOLVE` 的最终总结在实时和完成态都能把近似 Markdown 展示为标题、段落和列表

**Independent Test**: 按 `specs/008-fix-summary-markdown/quickstart.md` 触发一条结构化总结，确认实时流式和完成态都不再裸露 `##`、`###`、`-` 等原始语法

### Implementation for User Story 1

- [X] T006 [P] [US1] 在 `ui/src/utils/markdown.ts` 实现结构化总结专用的标题补空格、列表补空格和中文句尾断行修复规则
- [X] T007 [US1] 在 `ui/src/components/Dialogue/index.tsx` 将 `REACT / PLAN_SOLVE` 的 `ConclusionSection` 接入结构化总结 scope，并把 `agent_stream/task_summary` 统一送入该入口
- [X] T008 [US1] 在 `ui/src/components/ActionPanel/MarkdownRenderer.tsx` 让 streaming 与 final render 两条分支复用同一份结构化总结规范化结果，避免总结完成后显示退化
- [ ] T009 [US1] 按 `specs/008-fix-summary-markdown/quickstart.md` 执行 `REACT / PLAN_SOLVE` 最终总结实时与完成态手工验收，并修正 `ui/src/utils/markdown.ts` 或 `ui/src/components/Dialogue/index.tsx` 中暴露的问题

**Checkpoint**: User Story 1 已形成 MVP，结构化模式最终总结可以独立正确排版

---

## Phase 4: User Story 2 - 历史回放后的最终总结保持相同展示效果 (Priority: P2)

**Goal**: 让刷新页面、重进会话和历史 fallback 场景下的最终总结与实时展示保持一致

**Independent Test**: 生成一条结构化会话后刷新页面或重进会话，确认历史回放中的最终总结仍显示为与实时一致的标题、段落和列表结构

### Implementation for User Story 2

- [X] T010 [P] [US2] 在 `ui/src/services/agentConversation.ts` 校准 `buildHistoryConclusionFallback` 输出，使其继续满足结构化总结 source 约定而不引入新的数据契约
- [X] T011 [US2] 在 `ui/src/components/Dialogue/index.tsx` 让历史回放恢复出的总结对象与实时 `task_summary` 共享同一结构化总结 scope 和渲染入口
- [ ] T012 [US2] 按 `specs/008-fix-summary-markdown/quickstart.md` 执行刷新页面、重进会话和历史详情场景验收，并修正 `ui/src/services/agentConversation.ts` 或 `ui/src/components/Dialogue/index.tsx` 的回放差异

**Checkpoint**: User Story 2 完成后，实时与历史回放都能独立得到一致展示结果

---

## Phase 5: User Story 3 - 修复展示问题时不破坏合法 Markdown 与代码内容 (Priority: P3)

**Goal**: 在修复近似 Markdown 的同时，保护代码块、合法 Markdown 和非目标场景不回归

**Independent Test**: 用同时包含 fenced code block、合法 Markdown、普通段落和普通 `CHAT` 回复的样本验收，确认只有结构化总结入口启用新规则

### Implementation for User Story 3

- [X] T013 [P] [US3] 在 `ui/src/utils/markdown.ts` 加固 fenced code block 分段保护和合法 Markdown no-op 行为，避免修正规则误改代码内容
- [X] T014 [P] [US3] 在 `ui/src/components/ActionPanel/ActionPanel.tsx`、`ui/src/components/ActionPanel/FileRenderer.tsx`、`ui/src/components/ActionPanel/HTMLRenderer.tsx` 保持 default scope，不让非总结调用点默认启用结构化总结修复
- [X] T015 [US3] 在 `ui/src/components/Dialogue/index.tsx` 保持普通 `CHAT` 回复和复制逻辑走默认规范化路径，不把本期修复扩散到非结构化聊天回复
- [ ] T016 [US3] 按 `specs/008-fix-summary-markdown/quickstart.md` 执行代码块保护、合法 Markdown 和非目标场景回归验收，并修正 `ui/src/utils/markdown.ts`、`ui/src/components/ActionPanel/MarkdownRenderer.tsx`、`ui/src/components/Dialogue/index.tsx` 中的边界问题

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 收敛最终注释、构建校验和验收文档，确保交付闭环

- [X] T017 [P] 在 `ui/src/utils/markdown.ts` 与 `ui/src/components/ActionPanel/MarkdownRenderer.tsx` 清理重复逻辑并补足中文注释，确保规则边界易于维护
- [ ] T018 在 `ui/package.json` 对应的前端工程目录执行 `npm run lint` 与 `npm run build`，并按 `specs/008-fix-summary-markdown/quickstart.md` 完成最终全链路验收

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1: Setup**: 无依赖，可立即开始
- **Phase 2: Foundational**: 依赖 Phase 1，完成前阻塞所有用户故事
- **Phase 3: User Story 1**: 依赖 Phase 2，是 MVP 主路径
- **Phase 4: User Story 2**: 依赖 Phase 2，可在共享渲染边界完成后独立推进
- **Phase 5: User Story 3**: 依赖 Phase 2，可与 US2 并行，但需要复用同一结构化总结 scope
- **Phase 6: Polish**: 依赖全部已选用户故事完成

### User Story Dependencies

- **User Story 1 (P1)**: 仅依赖 Foundational 阶段，无其他故事级前置依赖
- **User Story 2 (P2)**: 仅依赖 Foundational 阶段，应通过共享总结入口实现与 US1 相同的展示语义
- **User Story 3 (P3)**: 仅依赖 Foundational 阶段，负责边界保护与非目标场景回归

### Within Each User Story

- 先完成共享入口/规则，再做故事级接线
- 先修目标链路，再执行该故事的独立验收
- 每个故事完成后都按 `specs/008-fix-summary-markdown/quickstart.md` 跑对应手工验证

### Parallel Opportunities

- `T004` 与 `T005` 可并行推进，因为分别聚焦渲染组件和总结入口
- `T006` 可与 `T007` 并行准备，最终在 `T008` 汇合
- `T010` 可与 `T011` 并行推进，因为分别位于历史数据恢复和展示入口
- `T013` 与 `T014` 可并行推进，因为分别聚焦规范化规则保护和非目标调用点隔离
- `T017` 可在代码稳定后与最终验收准备并行进行

---

## Parallel Example: User Story 3

```bash
# 可以并行推进的 P3 任务
Task: "在 ui/src/utils/markdown.ts 加固 fenced code block 保护和合法 Markdown no-op 行为"
Task: "在 ui/src/components/ActionPanel/ActionPanel.tsx、ui/src/components/ActionPanel/FileRenderer.tsx、ui/src/components/ActionPanel/HTMLRenderer.tsx 保持 default scope，不默认启用总结修复"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational
3. 完成 Phase 3: User Story 1
4. **STOP and VALIDATE**: 按 `specs/008-fix-summary-markdown/quickstart.md` 验证结构化总结实时与完成态
5. 若 MVP 稳定，再继续 US2 / US3

### Incremental Delivery

1. 先建立结构化总结专用 scope 和共享规范化入口
2. 交付 User Story 1，解决最直观的最终总结排版问题
3. 再交付 User Story 2，补齐历史回放一致性
4. 最后交付 User Story 3，收紧代码块保护和非目标场景边界
5. 每个故事都能在不依赖后续故事的前提下单独验收

### Parallel Team Strategy

With multiple developers:

1. 团队先完成 Setup + Foundational
2. 完成后可并行分工：
   - Developer A: User Story 1
   - Developer B: User Story 2
   - Developer C: User Story 3
3. 最后合流执行 Polish 与全量验收

---

## Notes

- [P] 任务表示文件不冲突且不依赖未完成任务
- 每个用户故事都必须能按 `specs/008-fix-summary-markdown/quickstart.md` 独立验收
- `ui/package.json` 中的 `lint/build` 是本期唯一强制自动化校验入口
- 不允许把本期修复扩散到后端协议、数据库或非目标 Markdown 展示场景
