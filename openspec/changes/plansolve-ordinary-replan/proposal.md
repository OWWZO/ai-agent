## Why

当前 `PlanSolve` 在 `close_update=0` 下并不能稳定完成普通 replan：运行时 `planning` schema 与 Planner prompt 语义错位，计划创建后缺少统一的首步激活规则，计划推进逻辑分散在多个类中，历史回放还依赖推测式补偿。结果是该模式名义上支持动态重规划，实际上容易空转、丢状态，并且无法稳定审计 replan 前后的计划变化。

现在需要把这条链路收口，因为项目已经有较成熟的 Tool Output / Replay 基础设施，继续依赖零散 if 和隐式顺推语义，只会让 `PlanSolve` 主链路、planning 工具账本和历史回放进一步分叉，后续每次修改都需要重复修补多处。

## What Changes

- 为 `close_update=0` 建立可稳定运行的普通 replan 语义，明确 `create`、`update`、`mark_step`、`finish` 四类 planning 命令的后端行为。
- 引入统一的计划生命周期服务，集中处理首步激活、步骤完成后的自动推进、剩余计划重排、计划结束判定和异常修复。
- 为 `planning` 工具新增独立的结构化明细账本，记录每次命令的 `before/after` 计划快照、当前步骤、自动推进与自动结束结果。
- 调整 replay / projector 链路，优先基于 planning 明细账本恢复 `plan`、`task` 与 finish 语义，不再依赖 `stepPlan()` 的推测式补偿作为主路径。
- 收敛 `application-dev.yml` 中的 planning tool schema 与 Planner prompt，使普通 replan 运行时真正暴露完整命令集，同时补齐单测、持久化测试和集成回放回归。

## Capabilities

### New Capabilities
- `plansolve-ordinary-replan`: `PlanSolve` 在 `close_update=0` 下必须支持稳定的动态重规划、自动推进当前步骤和自动进入总结阶段。
- `planning-tool-ledger-replay`: `planning` 工具必须持久化结构化 planning 明细，并让历史回放优先基于该明细恢复计划与任务状态。

### Modified Capabilities
- 无

## Impact

- 影响 `ai-agent-station-study-domain`：`Plan`、`PlanningTool`、`PlanningAgent`、`PlanningPrompt`、新增 `PlanLifecycleService`，以及 planning tool output 模型与 projector。
- 影响 `ai-agent-station-study-infrastructure`：tool output 的 writer / reader、planning DAO / PO / Mapper、planning 明细表读取与写入。
- 影响 `ai-agent-station-study-app`：`application-dev.yml`、`schema.sql`、planning MyBatis XML 与相关测试装配。
- 影响回放与审计链路：`PlanningToolInvocationProjector`、tool output 读取契约、PlanSolve 集成回归与 replay 回归。
- 本期不改前端协议，不新增新的 Planner 工具，不覆盖 `application-test.yml` / `application-prod.yml`。
