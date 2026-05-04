## ADDED Requirements

### Requirement: PlanSolve ordinary replan SHALL support the full planning command set under `close_update=0`
系统在 `close_update=0` 的普通 replan 模式下，必须允许 Planner 通过 `create`、`update`、`mark_step`、`finish` 四类命令维护计划，而不是退化成只支持首轮创建计划。

#### Scenario: Supported commands are available in ordinary replan mode
- **WHEN** Planner 在 `close_update=0` 模式下提交 `create`、`update`、`mark_step` 或 `finish`
- **THEN** 系统必须接受该命令并进入统一的 planning 生命周期处理
- **THEN** 系统不得把后续轮限制成只允许 `create`

#### Scenario: Unsupported commands are rejected without mutating the plan
- **WHEN** Planner 提交未定义命令或缺失该命令所需的必要参数
- **THEN** 系统必须返回受控校验错误
- **THEN** 当前计划状态必须保持不变

### Requirement: Plan creation SHALL always produce an executable current step
系统创建计划后，必须保证存在一个可执行的当前步骤，避免 `PlanningAgent` 在普通 replan 模式下读到空任务。

#### Scenario: First executable step is activated after create
- **WHEN** Planner 成功创建一个包含至少一条步骤的计划
- **THEN** 系统必须将第一条可执行的未开始步骤置为 `in_progress`
- **THEN** `PlanningAgent.getNextTask()` 必须能够返回该当前步骤

#### Scenario: Empty plans are rejected during create
- **WHEN** Planner 尝试创建一个没有任何步骤的计划
- **THEN** 系统必须拒绝该创建请求
- **THEN** 系统必须返回明确的计划为空错误

### Requirement: Step completion SHALL auto-advance remaining work and auto-finish when all steps complete
系统在普通 replan 模式下，必须把“当前步骤完成”与“剩余步骤推进”视为后端职责，而不是要求 Planner 手工维护每一步状态切换。

#### Scenario: Completing a non-final step activates the next step
- **WHEN** Planner 对当前 `in_progress` 步骤提交 `mark_step` 完成命令，且后续仍存在未开始步骤
- **THEN** 系统必须将该步骤标记为 `completed`
- **THEN** 系统必须自动把下一条未开始步骤置为 `in_progress`

#### Scenario: Completing the final step triggers summary readiness
- **WHEN** Planner 对最后一条当前步骤提交 `mark_step`，且没有剩余未完成步骤
- **THEN** 系统必须将计划收口为“全部完成”状态
- **THEN** `PlanningAgent.getNextTask()` 必须返回 `finish` 语义以进入总结阶段

#### Scenario: Explicit finish closes remaining steps
- **WHEN** Planner 提交 `finish` 以提前结束剩余计划
- **THEN** 系统必须将剩余未完成步骤统一收口为完成态
- **THEN** 外层执行链路必须进入总结阶段

### Requirement: Replanning SHALL preserve completed work while replacing remaining steps
系统执行 `update` 时，必须冻结已完成步骤，并仅允许重排未完成的剩余计划，避免 replan 覆盖已完成事实。

#### Scenario: Completed steps remain intact during update
- **WHEN** 当前计划中已经存在若干 `completed` 步骤，Planner 提交 `update`
- **THEN** 系统必须保留这些已完成步骤的顺序、文本和完成状态
- **THEN** `update.steps` 只允许替换未完成部分的步骤列表

#### Scenario: Replanned remaining steps receive a new current step
- **WHEN** Planner 提交新的剩余步骤列表完成 replan
- **THEN** 系统必须使用“已完成步骤前缀 + 新剩余步骤列表”重建完整计划
- **THEN** 若更新后不存在 `in_progress` 步骤，系统必须自动激活第一条新的未完成步骤

### Requirement: Incomplete plans SHALL self-repair missing current steps or fail fast
系统在计划尚未完成但当前步骤缺失时，必须优先执行受控修复；若无法修复，则必须 fail fast，而不是让外层循环静默空转。

#### Scenario: Missing current step is repaired when pending work still exists
- **WHEN** 当前计划没有任何 `in_progress` 步骤，但仍存在 `not_started` 步骤
- **THEN** 系统必须自动激活第一条 `not_started` 步骤
- **THEN** 后续执行必须继续使用修复后的当前步骤

#### Scenario: Non-recoverable planning gaps stop the run
- **WHEN** 当前计划既没有 `in_progress` 步骤，也没有可激活的 `not_started` 步骤，但计划仍未满足全部完成条件
- **THEN** 系统必须抛出受控异常并终止本次 run
- **THEN** 系统不得继续返回空字符串并空转到 `max_steps`
