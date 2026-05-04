## MODIFIED Requirements

### Requirement: Planning replay SHALL reconstruct plan and task state from structured planning outputs
历史回放在存在 planning 明细账本时，必须优先使用该结构化事实恢复每次 planning invocation 对应的 planner round、该轮 `plan`、该轮派发的 `task` 与 finish 语义，并保证这些事件的归组结果与实时运行一致，而不是只保留 latest plan。

#### Scenario: Replay restores one planner round per planning invocation
- **WHEN** 同一 run 中存在多次 planning invocation，且每次 invocation 都有 planning 明细记录
- **THEN** replay 必须为每次 invocation 生成独立的 planner round frame，并保持真实发生顺序
- **THEN** 每个 planner round frame 都必须输出稳定的 `plannerRoundId`，且该值必须等于对应 invocation 的 `toolInvocationId`

#### Scenario: Replay restores the plan and dispatched task for the same planner round
- **WHEN** 第 `N` 轮 planning 明细记录存在 `after_plan_json` 与 `current_step`
- **THEN** replay 必须使用该轮的 `after_plan_json` 恢复第 `N` 轮 `plan`
- **THEN** 该轮之后产生的 `task`、`tool_thought`、`tool_result`、`file` 与 `result` 事件必须归入第 `N` 轮 planner 派发的 task 容器，且不得回写到第 `N-1` 轮任务组

#### Scenario: Replay derives finish semantics from completed planning state
- **WHEN** planning 明细记录表明该轮计划已全部完成，或显式记录了自动结束结果
- **THEN** replay 必须恢复与实时链路一致的 finish / summary 语义
- **THEN** replay 不得再依赖 `stepPlan()` 的推测式补偿作为主路径
