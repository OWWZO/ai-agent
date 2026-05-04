## ADDED Requirements

### Requirement: Live conversations SHALL preserve every planner round during replan
系统在单次对话发生多轮 `replan` 时，必须保留每轮 planner 的 thought 与 plan 快照，而不是只保留最新一版。

#### Scenario: New planner round appends history and updates latest aliases
- **WHEN** 前端先后收到第一轮和第二轮 planner `plan_thought` / `plan` 事件
- **THEN** 每一轮 thought 与 plan 事件都必须暴露稳定的 `plannerRoundId`
- **THEN** `multiAgent.plannerRounds` 必须按真实发生顺序保留这两轮记录
- **THEN** `multiAgent.plan_thought` 与 `multiAgent.plan` 必须始终指向最新一轮的 thought 与 plan

#### Scenario: Task-wrapped plan updates the same planner history model
- **WHEN** 后续 planner 计划通过 `task.messageType=plan` 回传，而不是顶层 `plan`
- **THEN** 系统必须把该计划按相同的 `plannerRoundId` 标准化写入对应 planner round
- **THEN** 已存在 round 的历史 thought 与 plan 快照不得被覆盖或丢失

### Requirement: Dialogue SHALL allow independent browsing of planner thought and plan history
系统必须使用固定的思考卡片与总计划卡片展示 planner 历史，并允许用户分别切换两个卡片的版本。

#### Scenario: Version cursors default to the latest round
- **WHEN** 对话存在多轮 planner history
- **THEN** 思考卡片与总计划卡片必须各自显示独立的版本计数与切换控件
- **THEN** 两个卡片的默认版本都必须定位到最新 round

#### Scenario: Viewing an old plan does not roll back live progress semantics
- **WHEN** 用户切换到旧版总计划
- **THEN** 卡片只可展示该版本的静态标题与步骤快照
- **THEN** 时间线、任务完成数和进行中状态仍必须以最新计划版本为准
