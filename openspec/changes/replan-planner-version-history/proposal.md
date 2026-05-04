## Why

当前普通 `replan` 已经补齐 planning 命令集和结构化账本，但实时聚合与历史回放仍只保留最新一版 planner 思考和总计划。结果是用户在第二轮及以后重规划时，无法回看每轮 thought / plan 的演进，也无法确认历史回放是否与实时对话一致。

现在需要收口这条链路，因为前端 `Dialogue`、实时 SSE 聚合和后端 replay 已经具备结构化基础；如果继续沿用“覆盖最新值”的实现，实时展示、历史回显和任务归档会持续分叉，后续每次调整 replan 都要重复补洞。

## What Changes

- 前端新增 `multiAgent.plannerRounds` 历史账本，同时保留 `multiAgent.plan` 与 `multiAgent.plan_thought` 作为最新态别名。
- `combineData()` 与相关聚合逻辑统一恢复顶层 `plan`、`plan_thought` 与 `task.messageType=plan`，按稳定 round 维度归档 planner 历史，不再覆盖旧版本。
- `Dialogue` 固定展示一张思考卡片和一张总计划卡片，二者分别支持版本切换；查看旧版本时只展示静态快照，不回退最新任务进度。
- 后端 replay / projector 保留单次 run 内全部 planning 版本事件，按实时语义恢复 planner thought、plan、task、tool 与 result 的顺序和归组关系。
- 补齐前后端回归测试，覆盖两轮及以上 `replan` 的实时聚合、历史回放、任务归档与版本切换行为。

## Capabilities

### New Capabilities
- `planner-version-history`: `replan` 对话必须保留 planner 多版本历史，并让实时链路与历史回放都能按版本查看 thought 与 plan。

### Modified Capabilities
- `planning-tool-ledger-replay`: `planning` replay 必须从“恢复 latest plan / task”扩展为“按 invocation 回放全部 planner rounds”，并为每轮输出稳定 round 标识与正确的任务归组语义。

## Impact

- 影响 `ui/src/types/message.ts`、`ui/src/types/chat.ts`、`ui/src/utils/chat.ts`、`ui/src/utils/conversationHistory.ts` 与 `ui/src/components/Dialogue/index.tsx` 的 planner 聚合与展示语义。
- 影响 `ReplayProjector`、`ToolInvocationProjectorRegistry`、`PlanningToolInvocationProjector` 的 replay 顺序、round 归档与任务分组规则。
- 影响前后端回归测试，重点覆盖多轮 planning、`task.messageType=plan` 恢复以及实时/历史布局一致性。
- 本期不新增数据库表，不改 planning tool 协议，不调整 `reactor-tool` / `reactor-client`。
