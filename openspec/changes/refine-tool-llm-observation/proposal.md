## Why

当前 `ai_agent_tool_invocation.output_text` 的语义不清晰。实际运行时里，这个字段对应的是“工具执行后回传给主智能体继续推理的 observation 文本”，而不是泛化的文本输出；与此同时，`deep_search` 目前会把整包结构化结果作为工具结果回喂给主智能体，信息噪音高、重点不稳定，也不利于模型明确理解“检索了哪些来源、拿到了哪些内容”。

现在需要把“工具对主智能体的可观察结果”收敛成明确契约，并让 `deep_search` 提供更适合后续推理消费的精简搜索摘要。

## What Changes

- 将 `ai_agent_tool_invocation.output_text` 的语义收敛为 `llm_observe`（代码层对应 `llmObservation`），明确该字段只存“工具回传给主智能体的执行结果”。
- 调整工具执行账本与运行时结果模型，使写入账本的 `llmObservation` 与实际追加到主智能体 `tool` message 的内容保持一致。
- 为 `deep_search` 拆分“给前端展示的结构化结果”和“给主智能体消费的 observation”，不再把整包最终 `output_json` 直接回传给主智能体。
- 将 `deep_search` 的 observation 精简为可稳定消费的搜索摘要，至少包含查询拆解、命中的来源标题/链接，以及文档内容摘要，让主智能体知道搜索了哪些来源和得到哪些关键信息。
- 同步调整相关 Mapper、账本查询视图和调试/回放所依赖的字段命名，避免新旧语义混用。

## Capabilities

### New Capabilities
- `tool-llm-observation`: 规范工具执行结果中供主智能体消费的 observation 生成、持久化与 `deep_search` 精简摘要回传行为。

### Modified Capabilities

## Impact

- 数据结构：`ai_agent_tool_invocation` 表字段命名与对应实体/Mapper/View。
- 后端运行时：`BaseAgent`、`ToolExecutionOutcome`、`ReactImplAgent`、`ExecutorAgent`、`PlanningAgent` 的工具结果写回链路。
- 工具实现：重点影响 `deep_search`，并统一所有工具写入 `llmObservation` 的口径。
- 查询与调试：`AgentExecutionRecorderImpl`、`ExecutionLedgerQueryServiceImpl`、账本相关 DTO / VO / Mapper XML。
- 历史与前端：不改变现有前端实时 SSE 协议；`deep_search` 仍保留现有结构化展示数据，但与主智能体 observation 解耦。
