## 1. 前端 Planner 历史模型与实时聚合

- [x] 1.1 扩展 `ui/src/types/message.ts` 与 `ui/src/types/chat.ts`，定义 `PlannerRound` / `plannerRounds`，并保留 `plan`、`plan_thought` 的最新态别名语义
- [x] 1.2 调整 `ui/src/utils/chat.ts`，统一归一化顶层 `plan_thought`、顶层 `plan` 与 `task.messageType=plan`，按稳定 round 维度 upsert planner 历史
- [x] 1.3 补充 `ui/src/utils/chat.test.ts`，覆盖多轮 `replan`、latest alias 同步以及 task-wrapped plan 恢复场景

## 2. 前端展示与历史回放一致性

- [x] 2.1 改造 `ui/src/components/Dialogue/index.tsx`，为思考卡片和总计划卡片增加独立版本游标，并默认定位最新版本
- [x] 2.2 区分最新计划与历史计划的展示语义，确保旧版计划只展示静态快照，不影响当前任务进度 UI
- [x] 2.3 保持 `ui/src/utils/conversationHistory.ts` 继续复用 `combineData()`，并在 `ui/src/utils/conversationHistory.test.ts` 覆盖多轮 replay 与任务归档一致性

## 3. 后端 Replay 保留全部 Planner Versions

- [x] 3.1 调整实时 planning frame 装配，统一透出 `plannerRoundId = toolInvocationId`，并让同一轮 `plan_thought`、`plan` 与 `task.messageType=plan` 使用同一个 round 标识
- [x] 3.2 调整 `PlanningToolInvocationProjector`，为每次 planning invocation 产出独立 planner round frame 和同一 `plannerRoundId`
- [x] 3.3 调整 `ReplayProjector` 与 `ToolInvocationProjectorRegistry` 的顺序和分组策略，确保第 `N` 轮 planner 后续派发的 task / tool / result 固定归入第 `N` 轮 task 容器
- [x] 3.4 补充 app / domain replay 回归测试，覆盖多轮 planning、任务归组、稳定 round 标识和旧 run fallback 兼容

## 4. 回归验证与验收

- [x] 4.1 运行前端定向测试，验证实时聚合、版本切换和历史回放场景
- [ ] 4.2 运行后端 replay / planning 定向测试，确认 planner 多版本历史不会回退既有行为
  当前受工作区既有 `ai-agent-station-study-app` `testCompile` 缺类问题阻塞，无法在本次变更内独立完成。
- [ ] 4.3 手工验证 live / replay 两条链路下查看旧版计划不会影响最新任务进度展示
