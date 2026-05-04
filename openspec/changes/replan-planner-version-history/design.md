## Context

当前 `PlanSolve` 的普通 `replan` 已经支持多轮 planning invocation，但前端协议仍以单值 `multiAgent.plan` / `multiAgent.plan_thought` 为主，新的 planner 输出会直接覆盖旧版本。这样虽然能维持“最新态”展示，却丢失了版本历史，也让历史回放只能尽力恢复最新值，无法和实时对话保持同构。

现有实现还存在两个约束：

- 历史回放必须继续复用当前 `combineData()` 聚合链，而不是在 `conversationHistory.ts` 再维护一套特殊逻辑。
- 任务进度、工作区状态和时间线必须始终以最新计划版本为准，查看旧版 thought / plan 不能反向影响当前执行状态。
- 本期要尽量复用已有 planning structured output、projector 和前端消息协议，不引入新的表结构或新的 planner 工具。

## Goals / Non-Goals

**Goals:**

- 保留单次对话中全部 planner round 的 thought 与 plan 历史，并同时维护最新态别名。
- 让实时 SSE 聚合与历史 replay 恢复出一致的 planner 版本顺序和任务归档结构。
- 让 `Dialogue` 使用固定思考卡片和固定总计划卡片展示历史版本，并支持各自独立切换。
- 确保查看旧版计划时只看到静态快照，而任务完成数、进行中态和后续任务编排始终由最新计划驱动。

**Non-Goals:**

- 不改 `planning` 工具协议，不引入新的 plan 数据结构或新的前端消息类型。
- 不新增数据库表，不做历史数据迁移，也不改 `reactor-tool` / `reactor-client`。
- 不把 planner 历史渲染成多张堆叠卡片；仍保持固定卡片，只切换版本内容。

## Decisions

### Decision 1: 用 `plannerRounds` 作为前端 planner 历史的唯一账本，同时保留最新态别名

采用方案：

- 在 `multiAgent` 上新增 `plannerRounds` 数组，按 round 顺序保存 `planThought`、`plan`、关联 messageId / taskId 等快照。
- `multiAgent.plan` 与 `multiAgent.plan_thought` 继续保留，但只作为“当前最新版本”的别名，始终同步到 `plannerRounds` 的最后一轮。

原因：

- 现有单值字段对最新态友好，但无法表达历史版本。
- 用一个历史账本承接所有 planner 版本，比维护多套平行数组或在组件内自行拼历史更稳定，也更容易让实时和 replay 共用同一套聚合逻辑。

备选方案：

- 方案 A：继续只保留单值字段，组件层自行缓存历史。问题是历史与数据源分离，实时 / replay 更容易出现分叉。
- 方案 B：分别维护 `planHistory` 和 `thoughtHistory` 两套数组。问题是同一轮 thought / plan 的关联关系更容易错位。

### Decision 2: 把顶层 `plan`、顶层 `plan_thought` 与 `task.messageType=plan` 统一归一化到同一 round 模型

采用方案：

- 聚合层为每轮 planner 维护稳定的 round key，canonical key 固定为 `toolInvocationId`。
- replay 链路直接使用 `ToolInvocationView.id` 作为 `plannerRoundId`，并把该值写入对应 `plan` frame 与其后续派发的 `task` frame。
- 实时链路也必须输出同一个 `plannerRoundId`。为解决 `plan_thought` 先于 `plan` 到达的问题，本期需要把 planning 调用账本绑定前移到最终 `plan_thought` frame 投影之前，让最终 `plan_thought`、顶层 `plan` 与 `task.messageType=plan` 都能透出相同的 `plannerRoundId`。
- 前端统一通过 `eventData.resultMap.plannerRoundId` 归档 round；只有旧 run 或兼容路径没有该字段时，才回退到 `(taskId, messageId)` 做 best-effort 归档。
- `handlePlanThoughtMessage()`、`handlePlanMessage()` 与 `handleNonStreamingMessage()` 都只负责向 `plannerRounds` upsert，对外再同步最新态别名。

原因：

- 当前 planner 计划既可能作为顶层 `plan` 到达，也可能包在 `task.messageType=plan` 中；如果继续分开处理，第二轮 replan 仍会覆盖第一轮。
- 现有 `ai_agent_tool_output_planning` 没有单独的 round 列，但已经有稳定的 `tool_invocation_id` / `ToolInvocationView.id`，直接复用这条主键链路最稳，不需要再新增表字段或另造一套 round 编号。
- 统一归一化后，实时输入源和 replay frame 只要满足同一 round 语义，就能复用完全一致的前端恢复逻辑。

备选方案：

- 方案 A：按到达顺序硬编码“第 N 个 thought 对应第 N 个 plan”。问题是异步流式和补发场景会错配。
- 方案 B：仅靠 message type 分支恢复，不建立 round key。问题是 task-wrapped plan 与顶层 plan 仍然难以关联。

### Decision 3: `Dialogue` 使用固定卡片 + 独立游标展示历史版本，最新进度语义与历史快照语义分离

采用方案：

- 思考卡片与总计划卡片分别维护独立版本游标，默认都指向最新 round。
- 最新计划版本继续展示完成数、step status 和进行中态。
- 历史计划版本只展示静态标题与步骤快照，不再展示会误导用户的最新进度状态。

原因：

- 用户需要对照 thought 历史与 plan 历史，但它们的浏览节奏不一定一致，共用一个游标会限制使用。
- 如果历史计划继续显示动态进度，会让“这是一张旧快照”与“这是当前执行状态”混在一起，语义不清。

备选方案：

- 方案 A：把每轮计划都堆叠成多张卡片。问题是对话会迅速膨胀，破坏当前布局。
- 方案 B：thought 和 plan 共用一个版本切换器。问题是无法单独核对某轮 thought 与另一轮最新计划的关系。

### Decision 4: replay 链路按 planning invocation 保留全部版本，并且把后续事件固定归到该轮派发的 task 容器

采用方案：

- `PlanningToolInvocationProjector` 为每次 planning invocation 产出独立 planner round frame，而不是只保留 latest 语义。
- `ReplayProjector` 与 `ToolInvocationProjectorRegistry` 调整 replay 顺序与分组策略，保证 planner thought、planner plan 和其后续 task / tool / result 的顺序与实时 SSE 语义一致。
- 第 `N` 轮 planner 产出的 `task`、`tool_thought`、`tool_result`、`file`、`markdown`、`deep_search` 与 `result`，都必须归入第 `N` 轮 planner 派发出来的 task 容器，不能回写到第 `N-1` 轮任务组，也不能被后续 planner frame 打断。
- `conversationHistory.ts` 不再追加特殊补偿，而是继续复用 `combineData()`，通过统一聚合规则恢复实时 / 历史一致结构。

原因：

- planner 历史本质上是运行时真实发生过的多次 planning 事件，replay 应该把这些事件完整投影出来，而不是只猜最后一版。
- 如果 planner frame 会打断当前任务容器，历史回放就会出现“计划和工具结果分散在不同组”的结构性错误。

备选方案：

- 方案 A：只在前端追加历史缓存，不改 replay。问题是历史会话仍然只能恢复最新值。
- 方案 B：在 `conversationHistory.ts` 再单独补 replay 特判。问题是实时和历史将长期维护两套规则。

## Risks / Trade-offs

- [旧 run 可能没有显式 `plannerRoundId`] → 前端与 replay 保留基于 `(taskId, messageId)` 的兼容归档策略，并为旧数据补回退测试。
- [保留完整 planner 历史后，前端状态分支增多] → 把 round upsert 和历史选择逻辑收敛为小型辅助函数，避免分散在多个分支中。
- [replay 顺序调整可能误伤非 planning 工具归组] → 仅对 planning projector 和 registry 分组策略做最小变更，并补充回归测试锁定非 planning 场景。
- [`plan_thought` 先发、`plan` 后发可能导致同一轮 key 对不齐] → 统一要求实时 planning frame 在投影阶段透出同一个 `plannerRoundId`，不允许前端靠时间顺序猜配对关系。
- [历史计划使用静态展示后，部分用户可能希望看到该版本当时的状态] → 本期优先保证“历史快照不污染最新进度”的主语义，后续再单独评估是否需要补充时间点状态视图。

## Migration Plan

1. 扩展前端类型和实时聚合逻辑，先让 `plannerRounds` 成为统一账本。
2. 调整 `Dialogue` 与 `conversationHistory` 消费方式，确保历史版本展示与实时聚合结构对齐。
3. 调整 replay projector，保留全部 planning 版本并补稳定 round 标识。
4. 运行前后端定向回归，并人工验证 live / replay 两条链路下的版本切换与任务进度语义。

回滚策略：

- 代码回滚即可恢复当前“只展示 latest”的实现。
- 本期不引入新表和数据迁移，因此不需要额外的数据回滚步骤。

## Open Questions

- 本期没有阻塞性开放问题。
- 后续可再评估是否需要在 UI 上额外暴露版本时间戳、轮次标签或“该历史版本对应的当前步骤”辅助信息。
