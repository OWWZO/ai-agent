# PlanSolve 普通 Replan 跑通方案

日期：2026-05-01

## 1. 背景

当前 `PlanSolve` 链路支持两种语义：

- `autobots.autoagent.planner.close_update=1`：首轮创建计划，后续由代码自动顺推下一步，Planner 不再真正参与重规划。
- `autobots.autoagent.planner.close_update=0`：期望 Planner 在执行中动态更新计划，但现状无法稳定运行。

当前不稳定的根因主要有三类：

1. `planning` 工具的配置 schema 与 Planner prompt 不一致。Prompt 要求后续轮使用 `mark_step` / `finish`，但当前配置只暴露 `create`。
2. 计划创建后没有统一的“首步激活”规则。`Plan.create()` 会把全部步骤初始化为 `not_started`，而关闭自动顺推后，当前步骤经常为空。
3. 计划推进规则分散在 `Plan`、`PlanningTool`、`PlanningAgent` 多处，依赖 prompt 与零散 if 共同维持，导致空转、索引漂移、更新后状态丢失等问题。

本方案不做完整动态状态机重写，而是在保留当前 `PlanSolve` 主链路、前端协议和 `Plan` 数据结构的前提下，把 `close_update=0` 收口成一套可稳定运行的普通 replan 方案。

## 2. 目标

### 2.1 目标

- 让 `close_update=0` 下的 `PlanSolve` 真正支持动态重规划。
- 保持当前用户侧体验基本不变，继续复用 `plan`、`task`、`plan_thought`、`result` 等 SSE 事件。
- 保持 `Step2PlanExecuteNode` 的主循环结构不变。
- 保留现有 `Plan` 模型：`title + steps + stepStatus + notes`。
- 把计划推进规则从散落的分支判断中抽离出来，集中到一个轻量领域服务中。

### 2.2 非目标

- 本期不引入全新的 `PlanV2` / DAG / 阶段状态机。
- 本期不改前端计划展示协议。
- 本期不新增 planner 工具，不拆分成多工具协议。
- 本期不改变 `<sep>` 表达并行任务的既有语义。
- 本期不废弃 `close_update=1`，仅保证 `close_update=0` 稳定可用。

## 3. 方案选择

本次采用“增量式普通 replan 收口”方案。

对比：

- 补丁式修复：改动最少，但计划推进逻辑会继续散落，后续维护成本高。
- 增量式普通 replan 收口：保留现有模型和协议，仅集中整理推进规则，风险可控，适合近期上线。
- 一步到位动态状态机：长期最优，但改动面大，不适合作为首轮 replan 稳定化方案。

结论：采用增量式普通 replan 收口。

## 4. 当前问题拆解

### 4.1 运行时 Schema 与 Prompt 错位

`PlanningTool` 的代码内置回退 schema 已支持：

- `create`
- `update`
- `mark_step`
- `finish`

但运行时并不会直接使用这份回退 schema。`PlanningTool.toParams()` 会优先读取配置中的 `autobots.autoagent.tool.plan_tool.params`；当前 `application-dev.yml` 中该配置只暴露 `command=create`，因此模型在真实运行时看到的仍然是受限 schema。

与此同时，Planner 的后续轮 prompt 也没有清晰说明：

- 何时使用 `mark_step`
- 何时使用 `update`
- 何时使用 `finish`
- 系统会自动推进下一步，而不是要求模型自己维护所有状态

因此真实问题不是“代码回退 schema 缺命令”，而是“运行时配置 schema 受限 + prompt 语义不完整”的双重错位。这会导致 `close_update=0` 下，Planner 在第二轮开始缺少稳定可走的动作路径。

### 4.2 首步未激活

`Plan.create()` 会把所有步骤状态设为 `not_started`。在 `close_update=1` 下，这个问题被 `stepPlan()` 掩盖；在 `close_update=0` 下，计划创建后常常没有 `in_progress` 步骤，`getCurrentStep()` 返回空串，外层循环可能空转。

### 4.3 状态推进规则分散

当前推进逻辑分布如下：

- `Plan.stepPlan()`：顺推下一步
- `PlanningTool.markStep()`：单步改状态
- `PlanningAgent.getNextTask()`：读取当前步并发送前端事件

这套设计在自动顺推模式下还能工作，但在 replan 场景下，缺少以下统一规则：

- 什么时候自动激活下一步
- `update` 时如何保留已完成步骤
- 当前步骤为空时如何修复
- 何时判定计划结束

## 5. 总体设计

### 5.1 核心思路

保留现有 `Plan` 模型和 `planning` 工具，对外协议不变；新增一个轻量领域服务统一维护计划生命周期，把 Planner 从“直接操纵细碎状态”降级为“发出高层计划动作”。

### 5.2 新增组件

新增领域服务：

- `PlanLifecycleService`

职责限定为五类操作：

1. 创建计划后标准化状态并激活首步
2. 标记当前步骤完成并自动推进下一步
3. 更新剩余计划，同时冻结已完成步骤
4. 修复“计划未完成但没有当前步骤”的异常状态
5. 判断计划是否已完成

### 5.3 协作边界

为避免职责继续扩散，本方案明确按以下边界切分：

- `PlanLifecycleService`
  - 只负责计划数据本身的状态转换
  - 不负责 LLM 调用
  - 不负责 SSE 事件推送
  - 不直接感知前端协议

- `PlanningTool`
  - 作为 `PlanningAgent` 与 `PlanLifecycleService` 之间的衔接层
  - 负责命令解析与参数校验
  - 将 `create / update / mark_step / finish` 委派给 `PlanLifecycleService`

- `PlanningAgent`
  - 负责调用 LLM、消费 planner tool call、读取当前任务
  - 负责向前端发送 `plan` / `task` / `plan_thought`
  - 不负责维护计划数据状态机

- `Plan`
  - 保留轻量数据对象职责
  - 保留基础读写能力
  - `stepPlan()` 仅作为 `close_update=1` 兼容路径的历史辅助方法保留
  - `close_update=0` 的普通 replan 路径不再依赖 `stepPlan()`

### 5.4 现有组件职责调整

- `Plan`
  - 保留数据结构和基础读写能力
  - 不再承担完整生命周期治理职责

- `PlanningTool`
  - 继续作为唯一 planner 工具入口
  - 负责解析 `command`
  - 具体生命周期行为委派给 `PlanLifecycleService`

- `PlanningAgent`
  - 继续负责调用 LLM、收集 tool call、返回当前任务
  - 不再承担首步激活、异常修复、隐式推进等规则

## 6. 领域规则

### 6.1 `create`

Planner 首轮创建计划时调用 `create`。

后端执行规则：

1. 创建 `Plan`
2. 若不存在任何 `in_progress` 步骤，则自动将第一条 `not_started` 步骤置为 `in_progress`
3. 若创建出的步骤列表为空，则拒绝创建并返回明确错误

效果：

- 首轮 planning 完成后，一定能拿到当前可执行任务
- 不依赖 `stepPlan()` 掩盖初始状态缺陷

### 6.2 `mark_step`

Planner 在某轮执行结果足以完成当前步骤时调用 `mark_step`。

后端执行规则：

1. 优先校验目标步骤是否为当前 `in_progress`
2. 将该步骤标记为 `completed`
3. 若后续仍存在 `not_started` 步骤，则自动将下一条未开始步骤激活为 `in_progress`
4. 若不存在剩余未开始步骤，则计划进入“待 finish”状态，等待 Planner 调用 `finish`

效果：

- Planner 只需表达“当前步已完成”
- 下一步推进由代码保证，不再依赖 prompt 让模型手动切状态

### 6.3 `update`

Planner 在需要重排剩余计划时调用 `update`。

后端执行规则：

1. 已完成步骤冻结，不允许被改写或删除
2. `update.steps` 只表达“剩余未完成步骤”的目标列表，不要求模型重新传回已完成步骤
3. 后端使用“已完成步骤前缀 + 新的未完成步骤列表”重建完整计划，避免依赖索引位置识别“同一个步骤”
4. 当前正在执行但尚未完成的步骤视为未完成集合的一部分，允许在 replan 时被替换或细化
5. 对新增的剩余步骤统一初始化为 `not_started`
6. 若更新后不存在 `in_progress`，则自动激活第一条未完成步骤
7. 若更新后全部步骤都已完成，则允许进入“待 finish”状态

效果：

- 支持真正意义上的 replan
- 已完成事实不会被新计划覆盖
- 避免因步骤 reorder 导致状态按索引错位重置

### 6.4 `finish`

Planner 在整体任务可进入总结阶段时调用 `finish`。

后端执行规则：

1. 将剩余未完成步骤统一收口为完成态，或按配置允许直接结束
2. Planner 返回 `finish`
3. `Step2PlanExecuteNode` 进入 `SummaryAgent` 收口逻辑

效果：

- 结束条件明确
- 不再依赖“全部步骤自动顺推完毕”这一条单一路径

### 6.5 异常修复规则

若计划未完成，但当前不存在 `in_progress` 步骤，执行以下修复：

1. 若存在 `not_started` 步骤，则自动激活第一条 `not_started`
2. 若既无 `in_progress`、也无 `not_started`，但仍未满足完成条件，则抛出受控异常，终止本次 run

效果：

- 避免 `PlanningAgent.getNextTask()` 返回空串后外层循环静默空转到 `max_steps`

### 6.6 非恢复异常的抛出位置

本方案不把“计划生命周期异常”挂在 `PlanningAgent.think()` 的 catch 语义上，因为该方法当前对异常是“记录日志后继续返回 true”，不适合作为计划状态一致性的 fail-fast 出口。

因此本期约束为：

1. `PlanLifecycleService` 先执行自动修复
2. 修复失败后，由 `PlanningAgent.getNextTask()` 或其上层调用路径抛出受控异常
3. `PlanningAgent` 不允许再用“返回空字符串”表达非恢复性计划错误
4. 该异常需沿 `act() -> BaseAgent.run() -> Step2PlanExecuteNode` 向外传播，由外层统一结束 run

这样可以避免错误被 `think()` 的日志吞掉后继续空转。

## 7. Prompt 与 Schema 调整

### 7.1 Tool Schema 调整

保留工具名 `planning`，恢复运行时配置中的完整命令集：

- `create`
- `update`
- `mark_step`
- `finish`

本期不新增工具，也不拆分工具协议。

### 7.2 Planner Prompt 调整

将 planner prompt 拆成两类语义：

#### 首轮 planning prompt

目标：

- 根据用户需求创建计划
- 调用 `create`

#### 后续轮 planning prompt

目标：

- 基于执行结果判断下一步动作
- 仅允许在 `mark_step`、`update`、`finish` 中选择

同时明确写入以下约束：

- `mark_step` 表示当前步骤已完成，下一步由系统自动推进
- `update` 仅用于调整未完成步骤
- `finish` 仅在整体任务已具备总结条件时使用

本期不引入 `blocked`，避免在普通方案中扩大状态空间。

## 8. 运行时流程

`close_update=0` 下的目标执行流程：

1. `PlanningAgent` 首轮调用 `planning.create`
2. `PlanLifecycleService` 自动激活首步
3. `PlanningAgent.getNextTask()` 返回当前任务，并发送 `plan` / `task`
4. `ExecutorAgent` 执行当前任务
5. `PlanningAgent` 基于执行结果调用：
   - `mark_step`
   - 或 `update`
   - 或 `finish`
6. `PlanLifecycleService` 推进或重排计划
7. 重复执行，直到返回 `finish`
8. `SummaryAgent` 进行最终总结

该流程保持：

- `Step2PlanExecuteNode` 主循环结构不变
- 前端可继续使用现有展示链

### 8.1 PlanningAgent 生命周期说明

`close_update=0` 下，`PlanningAgent` 不是一个持续运行的长生命周期计划状态机，而是被 `Step2PlanExecuteNode` 外层循环按轮次重复驱动的。

具体表现为：

1. 每次 `planning.run(...)` 都是一次独立的 Planner 轮次
2. 当 `getNextTask()` 成功返回当前任务时，`PlanningAgent` 会将自身状态置为 `FINISHED`
3. 该 `FINISHED` 仅表示“本轮 planner 决策已完成”，不表示整个用户任务结束
4. 真正的多轮协作由 `Step2PlanExecuteNode` 负责：`planner -> executor -> planner -> summary`

因此本方案不会把 `PlanningAgent` 直接改造成持续自驱的完整状态机，而是在保留现有外层 orchestration 的前提下，修复每轮 planner 的计划状态一致性。

## 9. 代码改造范围

### 9.1 必改文件

- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/dto/Plan.java`
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/PlanningTool.java`
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/PlanningAgent.java`
- `ai-agent-station-study-app/src/main/resources/application-dev.yml`

### 9.2 新增文件

- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/plan/PlanLifecycleService.java`

如需解耦实现细节，可补：

- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/plan/PlanLifecycleResult.java`
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/plan/PlanUpdateNormalizer.java`

但本期以最小闭环为准，不强制拆更多对象。

## 10. 兼容性

### 10.1 对 `close_update=1` 的兼容

本期不废弃 `close_update=1`。

建议：

- `close_update=1` 继续沿用既有顺推模式
- `close_update=0` 使用新的普通 replan 规则

这样可以降低一次性发布风险，并保留回退能力。

### 10.2 对前端协议的兼容

保持以下协议不变：

- `plan`
- `task`
- `plan_thought`
- `tool_thought`
- `result`

前端无需同步修改。

## 11. 测试方案

### 11.1 单元测试

新增或补充 `PlanLifecycleService` 测试，覆盖：

- `create` 后首步自动激活
- `mark_step` 后下一步自动推进
- `update` 时已完成步骤冻结
- 计划未完成但当前步骤为空时自动修复

### 11.2 工具层测试

补充 `PlanningTool` 测试，覆盖：

- `create / update / mark_step / finish` 的入参校验
- 运行时配置 schema 与回退 schema 的装配优先级
- 非法索引、非法状态、空步骤列表等异常路径

### 11.3 集成测试

补充 `PlanSolve` 集成回归，覆盖：

- `close_update=0` 下首轮创建计划并执行
- 中途 `update` 后继续执行
- 不会因为 `currentStep` 为空而空转到 `max_steps`
- 最终能进入 `SummaryAgent`

## 12. 验收标准

满足以下条件即可视为本方案完成：

1. `close_update=0` 时，Planner 能在执行中动态更新后续计划
2. 计划创建后一定存在当前可执行步骤
3. 当前步骤完成后，后端能自动推进下一步
4. 已完成步骤不会因 `update` 被覆盖
5. 不会因空当前步骤导致外层循环 silent loop
6. 最终仍能正常进入总结阶段并返回结果
7. 前端展示协议不变
8. `close_update=1` 的既有顺推用例回归通过

## 13. 风险与后续演进

### 13.1 本期风险

- `Plan` 仍然是字符串步骤模型，长期表达能力有限
- `step_index` 仍保留在协议中，对模型不够友好
- 并行步骤语义仍主要依赖 `<sep>` 约定

### 13.2 后续演进方向

若普通 replan 跑稳并验证有效，下一阶段再演进到统一动态状态机方案：

- 去掉 `step_index` 直控语义
- 把计划收口为结构化阶段/任务模型
- 将“并行批次”“阻塞”“跳过”“恢复执行”等能力正式纳入状态机

本期不提前引入这些重构，以降低发布风险。
