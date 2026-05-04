## 1. 收敛普通 Replan 生命周期

- [x] 1.1 新增 `PlanLifecycleService`，统一 `create`、`update`、`mark_step`、`finish` 的状态转换、首步激活、自动推进、自动结束与异常修复规则
- [x] 1.2 调整 `Plan`、`PlanningTool`、`PlanningAgent`，让 `close_update=0` 走新的生命周期服务，`close_update=1` 保持既有兼容路径
- [x] 1.3 调整 `PlanningPrompt` 与 `application-dev.yml` 的 planning schema / prompt 配置，确保普通 replan 运行时暴露完整命令集并明确自动总结语义

## 2. 补齐 Planning 结构化账本

- [x] 2.1 新增 `PlanningToolOutput`、planning DAO / PO / Mapper 与 `ai_agent_tool_output_planning` 表定义，承接 `command`、`before/after` 计划快照、当前步骤和自动推进标记
- [x] 2.2 扩展 `ToolOutputNames`、`AgentExecutionRecorderImpl`、`ToolOutputWriterImpl`、`ToolOutputReaderImpl`，把 planning 明细表接入现有 tool output 写入与读取链路
- [x] 2.3 补充 planning 明细表持久化测试，覆盖 `create`、`update`、`mark_step`、`finish` 四类命令以及 `auto_advanced`、`auto_finished` 等核心字段

## 3. 对齐 Replay 与兼容恢复

- [x] 3.1 重构 `PlanningToolInvocationProjector`，优先基于 planning 明细账本恢复 `plan`、`task` 与 finish 语义，无明细时再回退旧 `input_json` 兼容逻辑
- [x] 3.2 补充 replay 回归测试，覆盖 `planning.create`、`planning.update`、`planning.mark_step` 自动推进、全部完成自动总结和旧 run fallback 恢复

## 4. 完成回归与验收闭环

- [x] 4.1 补充 `PlanLifecycleService` 与 `PlanningTool` 单元测试，覆盖空步骤拒绝、已完成步骤冻结、缺失当前步骤修复和非法命令/参数错误
- [x] 4.2 补充 `PlanSolve` 集成回归，验证 `close_update=0` 首轮建计划、中途 replan、最后一步自动 summary，以及 `close_update=1` 兼容路径不回退
- [x] 4.3 运行后端定向 Maven 回归，覆盖 domain / app 层测试与 replay 相关用例，确认普通 replan 与 planning 账本链路可稳定通过
