## ADDED Requirements

### Requirement: Planning tool SHALL persist structured planning facts for every lifecycle command
`planning` 工具每次处理 `create`、`update`、`mark_step`、`finish` 时，必须把该轮 planning 的结构化事实写入独立明细账本，而不是只依赖主工具账本中的通用入参和文本观察结果。

#### Scenario: Successful lifecycle commands write before/after snapshots
- **WHEN** `planning` 工具成功执行 `create`、`update`、`mark_step` 或 `finish`
- **THEN** 系统必须为该次调用写入一条独立的 planning 明细记录
- **THEN** 该记录必须包含 `command`、`before_plan_json`、`after_plan_json`、`current_step` 与 `current_step_index`

#### Scenario: Automatic plan transitions are auditable
- **WHEN** 某次 planning 命令触发了系统自动推进下一步或因全部完成而自动进入总结态
- **THEN** planning 明细记录必须标识该次自动推进或自动结束结果
- **THEN** 审计链路必须能够区分“模型显式命令”和“系统自动状态收口”

### Requirement: Planning replay SHALL reconstruct plan and task state from structured planning outputs
历史回放在存在 planning 明细账本时，必须优先使用该结构化事实恢复 `plan` 卡片、当前 `task` 和 finish 语义，保证 replay 与实时运行语义一致。

#### Scenario: Replay restores current plan and current task from planning output
- **WHEN** 某轮历史数据存在 planning 明细记录且 `after_plan_json`、`current_step` 可用
- **THEN** replay 必须使用 `after_plan_json` 恢复该轮的 `plan`
- **THEN** replay 必须使用 `current_step` 恢复该轮的 `task`

#### Scenario: Replay derives finish semantics from completed planning state
- **WHEN** planning 明细记录表明该轮计划已全部完成，或显式记录了自动结束结果
- **THEN** replay 必须恢复与实时链路一致的 finish / summary 语义
- **THEN** replay 不得再依赖 `stepPlan()` 的推测式补偿作为主路径

### Requirement: Legacy runs without planning outputs SHALL continue to replay via fallback reconstruction
对于变更前没有 planning 明细账本的旧 run，系统必须保留兼容回放路径，避免新 replay 方案让历史会话直接不可读。

#### Scenario: Replay falls back to legacy reconstruction when no planning output exists
- **WHEN** 历史 run 中不存在任何 planning 明细记录
- **THEN** 系统必须回退到旧的 `input_json` 重建逻辑
- **THEN** 系统必须尽最大努力恢复 `plan` 与 `task`，而不是直接中断回放
