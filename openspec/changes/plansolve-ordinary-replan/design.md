## Context

当前 `PlanSolve` 在 `close_update=1` 下依赖既有自动顺推逻辑勉强可用，但 `close_update=0` 的普通 replan 路径并不稳定。核心问题集中在四处：一是运行时 `planning` schema 仍被 `application-dev.yml` 限制为 `create`，与 Planner prompt 和代码回退 schema 不一致；二是 `Plan.create()` 不会自动激活首步，导致计划创建后经常没有当前任务；三是计划推进规则分散在 `Plan`、`PlanningTool`、`PlanningAgent` 多个类中，状态修复和自动推进语义不统一；四是 replay 仍依赖 `PlanningToolInvocationProjector` 的推测式补偿，缺少 planning 专属事实源。

这次设计是一个跨 domain、infrastructure、app 装配与 replay 读侧的横切改动，但约束也很明确：

- 保留现有 `Plan` 基础模型和 `Step2PlanExecuteNode` 主循环，不把本期放大成全新状态机重写。
- 保持当前前端协议与 `plan` / `task` / `result` 展示链不变。
- 继续复用现有 `ToolOutputWriter / ToolOutputReader / ToolInvocationProjector` 基础设施，为 `planning` 增加专属 structured output。
- 本次 schema / prompt 配置调整只覆盖 `application-dev.yml`，不扩散到 test / prod profile。
- `close_update=1` 必须保留现有回退路径，作为本期发布的兼容边界。

## Goals / Non-Goals

**Goals:**

- 让 `close_update=0` 下的 `PlanSolve` 支持稳定的动态 replan，并始终能够产出当前可执行步骤。
- 把首步激活、步骤完成推进、剩余计划重排、异常修复和计划完成判定集中到单一生命周期边界。
- 为 `planning` 工具建立独立的结构化明细账本，记录命令语义、计划快照、当前步骤与自动推进结果。
- 让历史回放优先读取 planning 明细账本恢复 `plan` / `task` / finish 语义，并对旧历史保留 fallback。
- 通过单测、持久化测试、集成测试和 replay 回归锁定普通 replan 的新契约。

**Non-Goals:**

- 不引入新的 `PlanV2`、DAG 或统一动态状态机。
- 不改前端计划展示协议，不新增新的 planner 工具，也不改变 `<sep>` 并行表达语义。
- 不覆盖 `application-test.yml`、`application-prod.yml` 或为旧 run 做数据回填迁移。
- 不废弃 `close_update=1`，仅保证 `close_update=0` 可稳定运行并可与原模式并存。

## Decisions

### Decision 1: 用 `PlanLifecycleService` 统一普通 replan 的状态转换

采用方案：

- 新增 `PlanLifecycleService` 作为普通 replan 的单一生命周期入口。
- 由该服务负责五类动作：创建后首步激活、当前步骤完成后的自动推进、剩余计划 update、缺失当前步骤的自动修复、全部完成判定。
- `PlanningTool` 只负责命令解析和参数校验，再把状态转换委派给该服务。

原因：

- 当前推进逻辑散落在 `Plan`、`PlanningTool`、`PlanningAgent`，每处都在做一点状态修补，才会出现空转和状态漂移。
- 用轻量领域服务收口，比继续补丁式修复更稳定，同时又不必上升到完整状态机重写。

备选方案：

- 方案 A：继续在 `Plan`、`PlanningTool`、`PlanningAgent` 分别补 if。问题是职责会继续扩散，replay 也缺乏稳定事实源。
- 方案 B：一次性改造成全新状态机。长期更优，但改动面过大，不适合作为首轮稳定化方案。

### Decision 2: 保留现有 `Plan` 模型与 `planning` 工具协议，只重定义普通 replan 下的语义

采用方案：

- 继续保留 `Plan.title + steps + stepStatus + notes` 结构，不做模型升级。
- 继续使用单一 `planning` 工具，对外仍由 `create`、`update`、`mark_step`、`finish` 四类命令表达 planner 意图。
- `stepPlan()` 仅保留给 `close_update=1` 的兼容路径；`close_update=0` 不再依赖它驱动主流程。

原因：

- 本期目标是收口普通 replan，而不是创造新的计划协议。
- 保留已有模型和工具名，可以把变更限制在行为语义与持久化层，不冲击前端和 orchestration 主循环。

备选方案：

- 方案 A：拆成多个 planner 工具。问题是会改 prompt、工具注册和历史 replay 分发，范围扩大。
- 方案 B：引入全新 Plan 数据结构。问题是会把本期从“稳定化”升级成“重构计划系统”。

### Decision 3: 为 `planning` 引入独立 structured output 表，复用现有 tool output 体系

采用方案：

- 新增 `PlanningToolOutput` 与 `ai_agent_tool_output_planning`，保存 `command`、`before_plan_json`、`after_plan_json`、`current_step`、`current_step_index`、`auto_advanced`、`auto_finished` 等字段。
- 继续保留 `ai_agent_tool_invocation` 作为主账本，通过 `tool_invocation_id`、`request_id + tool_call_id` 与 planning 明细表关联。
- `PlanningTool` 在命令执行前后捕获快照，并通过现有 `ToolOutputWriter` 落表。

原因：

- 现有 rich tool 已经通过 `ai_agent_tool_output_*` 承接结构化终态，planning 继续把复杂语义塞在通用账本里只会让 replay 依赖补丁。
- 独立表既能支持审计，也能为 replay 提供唯一事实源。

备选方案：

- 方案 A：继续只写 `ai_agent_tool_invocation.input_json` / `output_json`。问题是字段含义松散，难以稳定表达 before / after 计划和自动推进结果。
- 方案 B：在 projector 中继续推测状态。问题是实时与历史会继续分叉，且无法审计 replan 细节。

### Decision 4: replay 优先读取 planning 明细账本，旧数据再走 fallback

采用方案：

- `PlanningToolInvocationProjector` 优先读取 planning 明细表中的 `after_plan_json` 与 `current_step`。
- 当 planning 明细存在时，直接恢复 `plan` 和 `task`，并基于 `auto_finished` 或“全部 completed”判定 finish 语义。
- 仅当旧 run 没有 planning 明细行时，才回退到基于 `input_json` 的旧恢复逻辑。

原因：

- replay 应该复用运行时实际落下的 planning 事实，而不是重新猜测系统当时是如何推进步骤的。
- 通过“新链路优先、旧链路兼容”可以避免一次性清洗历史数据。

备选方案：

- 方案 A：强制所有历史都迁移到新表。问题是成本高，且旧语义不一定能可靠回填。
- 方案 B：继续完全依赖 projector 补偿。问题是历史和实时永远没有同构的事实源。

### Decision 5: 配置和兼容策略限定在 `dev` profile 与 `close_update=1` 双轨并存

采用方案：

- 仅调整 `application-dev.yml` 中的 planning tool schema 与 prompt 装配，让普通 replan 真实暴露完整命令集。
- `close_update=1` 继续沿用现有自动顺推路径，作为兼容与回退边界。
- `PlanningAgent.getNextTask()` 明确承担“返回当前任务或抛出受控异常”的职责，不再用空字符串表达非恢复错误。

原因：

- 本期要先把问题最大的 dev 运行路径稳定下来，避免扩大 profile 差异治理范围。
- 保留 `close_update=1` 可以显著降低发布风险。

备选方案：

- 方案 A：同步改 test / prod profile。问题是扩大变更面，而且当前需求没有要求。
- 方案 B：强制所有模式都改走新语义。问题是现有顺推路径会失去稳定回退能力。

## Risks / Trade-offs

- [`PlanLifecycleService` 收口后，`Plan` 与 `PlanningTool` 的旧隐式语义可能仍残留] → 通过单测和集成测试覆盖 create/update/mark_step/finish 全路径，明确 `close_update=0` 不再依赖 `stepPlan()`。
- [新增 planning 明细表会扩展 reader / writer / mapper 的复杂度] → 复用现有 `ToolOutputWriter/Reader` 路由模式，并补 persistence / mapper 回归测试。
- [replay 切换事实源后，新旧历史可能表现不完全一致] → 新 run 强制优先走 planning 明细表；旧 run 保留 fallback，兼容范围明确限定为 best-effort。
- [`application-dev.yml` 与其他 profile 行为差异加大] → 在 proposal / design / tasks 中显式记录“仅 dev profile”边界，并把跨 profile 收口留作后续工作。
- [全部步骤完成后的自动 summary 语义若与旧 prompt 不一致，可能导致 Planner 多余调用 `finish`] → 在 Planner prompt 中明确“全部步骤完成后系统自动总结”，并在 tool 层对显式 `finish` 保持幂等收口。

## Migration Plan

1. 先扩展 domain 层生命周期服务与 planning output 模型，锁定普通 replan 的状态转换语义。
2. 再扩展 schema.sql、DAO、Mapper、writer / reader，让 `planning` 可以写入和读取独立明细账本。
3. 调整 `PlanningPrompt`、`PlanningTool`、`PlanningAgent` 与 `application-dev.yml`，把运行时命令集和自动 summary 语义对齐。
4. 切换 `PlanningToolInvocationProjector` 到“优先读 planning 明细、无明细再 fallback”的 replay 路径。
5. 补齐单测、持久化测试、PlanSolve 集成测试和 replay 回归后，再作为普通 replan 稳定化方案交付。

回滚策略：

- 代码回滚即可恢复旧的普通 replan / replay 逻辑。
- 若 schema 已新增 planning 明细表，回滚后允许保留该表和已写入数据，不要求做反向清理。
- `close_update=1` 保持未废弃，可作为运行时应急回退路径。

## Open Questions

- 本期没有阻塞性开放问题；但后续需要单独评估是否将 `application-test.yml` / `application-prod.yml` 同步到普通 replan 新语义。
- 若 planning 审计需求继续扩大，后续可再评估是否把 `before_plan_json / after_plan_json` 进一步拆成更细粒度的规范化结构。
