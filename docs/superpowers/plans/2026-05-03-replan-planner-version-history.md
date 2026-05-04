# Replan Planner Version History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `replan` 模式补齐 planner 多版本历史能力，让实时对话与历史回显都能查看思考内容与总计划的历史版本，同时保证任务进度始终按最新计划版本展示。

**Architecture:** 采用前后端协同方案。后端投影链路需要保留单次请求内的全部 planner 版本事件，并在历史回放中恢复与实时一致的版本顺序和子任务归档。前端在保留 `multiAgent.plan` / `multiAgent.plan_thought` 最新态别名的同时，新增 `plannerRounds` 历史账本，`Dialogue` 基于固定卡片 + 独立版本游标展示思考与计划历史。

**Tech Stack:** Java 17, Spring Boot 3.4.3, React 19, TypeScript 5, Vitest

---

## File Structure

- Modify: `ui/src/types/message.ts`
  - 扩展 `MESSAGE.MultiAgent` 与 planner 版本历史类型定义。
- Modify: `ui/src/types/chat.ts`
  - 扩展 `CHAT.ChatItem` / 展示层所需的 planner 历史快照类型。
- Modify: `ui/src/utils/chat.ts`
  - 实时 SSE 聚合入口，新增 `plannerRounds` 维护、`task.messageType=plan` 规范化恢复、最新态别名同步。
- Modify: `ui/src/components/Dialogue/index.tsx`
  - 固定思考卡片和固定总计划卡片增加 `<` / `>` 独立版本切换器，并区分最新版与历史版展示。
- Modify: `ui/src/utils/conversationHistory.ts`
  - 历史回放继续复用 `combineData()`，但要确保 `plannerRounds` 与子任务归档一起被恢复。
- Modify: `ui/src/utils/chat.test.ts`
  - 补实时两轮 `replan` 聚合与渲染衍生数据测试。
- Modify: `ui/src/utils/conversationHistory.test.ts`
  - 补历史回放两轮 `replan` 与子任务归档一致性测试。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java`
  - 调整 mixed history / llm history / tool history 投影顺序与 planner 版本 frame 还原策略。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/ToolInvocationProjectorRegistry.java`
  - 为 planning 相关投影保留当前任务组复用能力，避免子任务错组。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/PlanningToolInvocationProjector.java`
  - 保留本次请求全部 plan 版本，并确保每个 planner 版本与其后续 task 一一对应。
- Add or Modify: `ai-agent-station-study-app/src/test/java/...`
  - 如后端已有 replay projector 测试，补 planner 多版本历史用例；若没有，新增最小回归测试类。

## Invariants

- `multiAgent.plan` 与 `multiAgent.plan_thought` 继续存在，始终代表最新版本。
- 新增 `multiAgent.plannerRounds` 后，前端不得再通过覆盖单值字段丢失旧 planner 版本。
- 思考卡片与总计划卡片使用固定组件，各自独立维护版本游标，默认指向最新版本。
- 最新计划版本保留状态展示；历史计划版本只展示静态快照，不显示完成数、勾选、进行中态。
- 时间线、工作区、任务完成进度始终以最新计划版本为准，不因查看旧版本而回退。
- 历史回显布局与实时布局一致。
- 同一个子任务标题下的 `tool_thought / tool_result / file / markdown / deep_search / result` 必须归到同一个子任务容器中。

## Task 1: 定义 Planner 版本历史模型

**Files:**
- Modify: `ui/src/types/message.ts`
- Modify: `ui/src/types/chat.ts`
- Test: `ui/src/utils/chat.test.ts`

- [ ] **Step 1: 写失败测试，表达前端需要保留多个 planner 版本**

在 `ui/src/utils/chat.test.ts` 新增一个用例，构造：
- 第一轮 `plan_thought`
- 第一轮 `plan`
- 第二轮 `plan_thought`
- 第二轮 `task.messageType=plan`

断言：
- `currentChat.multiAgent.plannerRounds.length === 2`
- `currentChat.multiAgent.plan_thought` 指向第二轮 thought
- `currentChat.multiAgent.plan` 指向第二轮 plan
- 第一轮与第二轮内容都能从历史数组读到

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd ui && npm test -- --runInBand src/utils/chat.test.ts`
Expected: FAIL，报 `plannerRounds` 缺失或长度不匹配。

- [ ] **Step 3: 扩展前端类型定义**

在 `ui/src/types/message.ts` 增加类似以下结构：

```ts
interface PlannerRound {
  roundId: string
  taskId?: string
  thoughtMessageId?: string
  planMessageId?: string
  planThought?: string
  plan?: Plan
  createdAt?: string
}

interface MultiAgent {
  tasks: Task[][]
  plan?: Plan
  plan_thought?: string
  plannerRounds?: PlannerRound[]
}
```

在 `ui/src/types/chat.ts` 增加展示层可复用别名，避免组件直接依赖原始协议对象。

- [ ] **Step 4: 运行前端测试确认类型层改动未引入额外错误**

Run: `cd ui && npm test -- --runInBand src/utils/chat.test.ts`
Expected: 仍然 FAIL，但失败点收敛到聚合逻辑未实现。

- [ ] **Step 5: Commit**

```bash
git add ui/src/types/message.ts ui/src/types/chat.ts ui/src/utils/chat.test.ts
git commit -m "feat: add planner round history types"
```

## Task 2: 实时聚合层维护 plannerRounds 与最新态别名

**Files:**
- Modify: `ui/src/utils/chat.ts`
- Test: `ui/src/utils/chat.test.ts`

- [ ] **Step 1: 写失败测试，锁定实时 replan 的覆盖问题**

在 `ui/src/utils/chat.test.ts` 新增用例：
- 使用 `combineData()` 依次喂入两轮 planner 事件
- 第二轮 `plan` 走 `eventData.messageType = "task"` 且 `eventData.resultMap.messageType = "plan"`

断言：
- `plannerRounds` 中两轮都存在
- 第二轮 `task.plan` 被正确恢复
- `handleTaskData()` 输出的 `currentChat.plan` 为最新 plan
- 第一轮历史 plan 仍可从 `plannerRounds[0]` 读到

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd ui && npm test -- --runInBand src/utils/chat.test.ts`
Expected: FAIL，说明当前 `handlePlanMessage()` / `handleNonStreamingMessage()` 仍在覆盖单值字段或未恢复 `task.plan`。

- [ ] **Step 3: 在 `chat.ts` 实现 planner 历史 upsert**

要求：
- 为 `plan_thought` 与 `plan` 事件生成稳定 `roundId`
- 首选后端显式 round id；若暂未提供，则以 `taskId` 为主键，必要时结合 `messageId`
- `handlePlanThoughtMessage()`：
  - 追加/更新对应 round 的 `planThought`
  - 同步 `multiAgent.plan_thought = latest.planThought`
  - 同步 `currentChat.thought`
- `handlePlanMessage()`：
  - 顶层 `plan` 事件写入 round 历史
  - 同步 `multiAgent.plan = latest.plan`
- `handleNonStreamingMessage()`：
  - 遇到 `resultMap.messageType === "plan"` 时，把 payload 标准化为 `task.plan`
  - 同时更新 `plannerRounds` 中对应轮次的 plan 快照

- [ ] **Step 4: 最小实现下让测试转绿**

重点不是重构整个 `chat.ts`，而是在不破坏现有 `deep_search`、`html`、`tool_thought` 聚合的前提下：
- 引入小型辅助函数，例如：
  - `ensurePlannerRounds()`
  - `upsertPlannerRoundThought()`
  - `upsertPlannerRoundPlan()`
  - `normalizePlanTask()`

- [ ] **Step 5: 运行相关前端测试**

Run: `cd ui && npm test -- --runInBand src/utils/chat.test.ts`
Expected: PASS，包含新增两轮 replan 用例。

- [ ] **Step 6: Commit**

```bash
git add ui/src/utils/chat.ts ui/src/utils/chat.test.ts
git commit -m "feat: preserve planner round history in chat aggregation"
```

## Task 3: 对话卡片支持独立版本切换

**Files:**
- Modify: `ui/src/components/Dialogue/index.tsx`
- Modify: `ui/src/types/chat.ts`
- Test: `ui/src/utils/chat.test.ts`

- [ ] **Step 1: 写失败测试，定义历史计划快照与最新计划状态的差异**

在 `ui/src/utils/chat.test.ts` 新增衍生数据测试，断言：
- 最新 plan 仍带状态数组供当前 UI 使用
- 历史 plan 在组件消费时需要能识别为“静态快照”

如果当前没有组件级测试，不强行引入新测试框架，至少通过 `buildConversationTaskData()` 和辅助选择器测试版本列表顺序与 latest 默认定位。

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd ui && npm test -- --runInBand src/utils/chat.test.ts`
Expected: FAIL，说明还没有版本选择器所需的衍生结构。

- [ ] **Step 3: 在 `Dialogue/index.tsx` 增加两个独立游标**

要求：
- 思考卡片维护 `thoughtVersionIndex`
- 计划卡片维护 `planVersionIndex`
- 初始值都指向最新版本
- 当对应历史数组长度变化时，如果当前还在 latest，则自动跟到新的 latest

- [ ] **Step 4: 为思考卡片增加版本导航**

展示要求：
- 固定卡片，不堆叠多块
- 右下角显示 `< current / total >`
- 切换只更新卡片内容
- 最新版在 `chat.loading` 时继续按流式 thought 展示

- [ ] **Step 5: 为总计划卡片增加版本导航并区分新版/旧版样式**

展示要求：
- 最新版继续保留：
  - 完成数
  - stepStatus
  - 进行中标签
- 历史版只展示：
  - 标题
  - 步骤列表
  - 无完成数
  - 无勾选/进行中态

- [ ] **Step 6: 运行前端测试**

Run: `cd ui && npm test -- --runInBand src/utils/chat.test.ts`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add ui/src/components/Dialogue/index.tsx ui/src/types/chat.ts ui/src/utils/chat.test.ts
git commit -m "feat: add independent planner history switchers to dialogue"
```

## Task 4: 统一子任务归档规则，保证实时与历史布局一致

**Files:**
- Modify: `ui/src/utils/chat.ts`
- Modify: `ui/src/utils/conversationHistory.ts`
- Modify: `ui/src/utils/conversationHistory.test.ts`

- [ ] **Step 1: 写失败测试，锁定“同一子任务标题下归档所有组件”**

在 `ui/src/utils/conversationHistory.test.ts` 增加一条两轮 `replan` 历史用例，至少包含：
- `plan`
- `plan_thought`
- `task`
- `tool_thought`
- `tool_result`
- `result`

断言：
- 回放恢复后的 `chat.tasks`
  - 每个子任务标题下包含该任务对应的工具调用与结果
- 布局结构与实时 `handleTaskData()` 产物一致

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd ui && npm test -- --runInBand src/utils/conversationHistory.test.ts`
Expected: FAIL，当前历史回放中 planner / tool 分组可能错位。

- [ ] **Step 3: 调整 `chat.ts` 的时间线容器归档规则**

要求：
- 父 `task` 事件出现后，后续相关工具事件持续归入当前容器
- `plan_thought` 与顶层 `plan` 不应破坏子任务容器
- `task.messageType=plan` 只更新 planner 版本历史，不单独拆散当前子任务组

- [ ] **Step 4: 保持 `conversationHistory.ts` 只负责 replay frame 重放**

不要在 `conversationHistory.ts` 写第二套特殊逻辑。
要求：
- 继续复用 `combineData()`
- 通过聚合层统一恢复实时/历史一致的结构

- [ ] **Step 5: 运行历史回放测试**

Run: `cd ui && npm test -- --runInBand src/utils/conversationHistory.test.ts`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add ui/src/utils/chat.ts ui/src/utils/conversationHistory.ts ui/src/utils/conversationHistory.test.ts
git commit -m "fix: align replay task grouping with realtime layout"
```

## Task 5: 后端 replay projector 保留 planner 全版本信息

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/ToolInvocationProjectorRegistry.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/PlanningToolInvocationProjector.java`
- Test: `ai-agent-station-study-app/src/test/java/...`

- [ ] **Step 1: 写失败测试，锁定历史 replay 丢失旧 plan 版本的问题**

新增后端测试，构造：
- 同一 run 下多次 planning tool invocation
- 每次 invocation 产出不同 `afterPlan`

断言：
- `projectHistoryFrames()` 输出多条 `plan` 相关 frame
- 顺序与真实发生顺序一致
- 后续 task 仍然与对应的 planning 轮次保持一一对应

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=*Replay* -DskipTests=false`
Expected: FAIL，说明当前 projector 只保证 latest 语义或 task 归组错位。

- [ ] **Step 3: 调整 `PlanningToolInvocationProjector` 的版本恢复策略**

要求：
- 每次 planning invocation 都产出一条独立 `plan` 事件
- 该条 `plan` 事件不能覆盖前一条历史 frame
- 若当前版本仍有 current step，则继续按当前版本产出后续 `task` 事件
- 为前端提供稳定版本聚合所需标识；优先在 `resultMap` 中补入 planner round 标识字段

- [ ] **Step 4: 调整 `ToolInvocationProjectorRegistry` 的分组策略**

要求：
- planning 相关 projector 在需要时复用当前任务组，避免把“先 planner、后 task”的关联拆断
- 非 planning 工具仍保持现有隔离策略，避免影响其他历史回放

- [ ] **Step 5: 调整 `ReplayProjector` 的 mixed history 顺序**

要求：
- planner llm thought
- planning tool output
- 子任务 task / tool / result

这条链路在 history 中恢复后，要和实时 SSE 到达顺序语义一致。

- [ ] **Step 6: 运行后端相关测试**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=*Replay* -DskipTests=false`
Expected: PASS，若仓库存在既有无关编译错误，记录阻塞点但保留新增测试通过证据。

- [ ] **Step 7: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/ToolInvocationProjectorRegistry.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/PlanningToolInvocationProjector.java ai-agent-station-study-app/src/test/java
git commit -m "feat: replay all planner versions for replan history"
```

## Task 6: 端到端回归验证与清理

**Files:**
- Modify: `ui/src/utils/chat.test.ts`
- Modify: `ui/src/utils/conversationHistory.test.ts`
- Modify: `ai-agent-station-study-app/src/test/java/...`

- [ ] **Step 1: 补齐验收用例矩阵**

前端至少覆盖：
- 实时两轮 `replan`，思考与计划都可读历史
- 顶层 `plan` + `task.messageType=plan` 混合输入
- 切换旧计划不影响 latest progress
- 历史回显与实时布局一致

后端至少覆盖：
- replay 输出全部 planner 版本
- 子任务标题与 tool/result 归档不乱序

- [ ] **Step 2: 运行前端全量相关测试**

Run: `cd ui && npm test -- --runInBand src/utils/chat.test.ts src/utils/conversationHistory.test.ts`
Expected: PASS

- [ ] **Step 3: 运行后端相关测试**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=*Replay*,*Planning* -DskipTests=false`
Expected: PASS 或仅受既有无关编译问题阻塞，并在结果说明中明确列出绝对文件路径与错误行号。

- [ ] **Step 4: 手工验证清单**

至少人工检查：
- 新开一轮 `replan` 时，思考卡片版本数递增
- 总计划卡片版本数递增
- 两个卡片互相独立切换
- 切旧计划时无勾选/完成数
- 历史会话打开后布局与实时一致

- [ ] **Step 5: Commit**

```bash
git add ui/src/utils/chat.test.ts ui/src/utils/conversationHistory.test.ts ai-agent-station-study-app/src/test/java
git commit -m "test: cover replan planner version history"
```

## Self-Review

- 本计划覆盖了：
  - planner 多版本历史保留
  - 思考/计划固定卡片独立切换
  - 计划旧版本静态展示
  - 任务进度始终按最新版本
  - history / realtime 布局一致
  - 同一子任务标题下工具调用与结果归档
- 计划中未留 `TODO/TBD` 占位。
- 前端与后端都要求先写失败测试，再最小实现转绿。

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-03-replan-planner-version-history.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
