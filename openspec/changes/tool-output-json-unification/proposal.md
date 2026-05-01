## Why

当前 `ai_agent_tool_invocation.output_json` 在不同工具之间语义不稳定：有的为空，有的直接落前端展示字段，有的只是临时字符串结果。这会让历史恢复、调试分析和后续 projector 重放都缺少稳定事实源，也让 `llmObservation` 与 `output_json` 的职责边界再次变得模糊。现在需要把 `output_json` 收敛成统一的“工具原生结果 JSON”契约，并让历史展示改为按工具投影，而不是把前端事件结构直接写库。

## What Changes

- 为 `ai_agent_tool_invocation.output_json` 定义统一约束：只保存工具原生结果事实，禁止直接保存 `taskId`、`taskOrder`、`messageOrder`、`renderKind`、`eventData` 等前端展示态字段。
- 新增统一 `ToolOutputJsonBuilder`，保证所有工具调用都能稳定落库 `output_json`，并为纯文本、失败结果和 rich tool 提供统一 schemaVersion/fallback 结构。
- 调整 `BaseAgent` 与工具结果模型，明确 `llmObservation` 只代表主智能体继续推理所看到的 observation，`output_json` 则独立保存工具最终原生结果。
- 为 `deep_search`、`file_tool`、`code_interpreter`、`report_tool`、`data_analysis`、`multimodalagent_tool`、`image_generation_tool`、`script_runner_tool` 等重点工具补齐稳定的原生 JSON shape。
- 引入 `ToolInvocationProjector` 注册表，由历史重放层基于 `tool_name + input_json + output_json + artifact` 解析出前端现有 `eventData`，不再要求 `output_json` 自身长成前端卡片结构。
- 同步更新 replay 相关设计与测试，锁定“每条工具调用都有 tool-native output_json，历史与实时通过 projector 保持同构”这一契约。

## Capabilities

### New Capabilities
- `tool-output-json-projection`: 定义工具原生 `output_json` 的持久化契约，以及历史重放按工具解析 `output_json` 的投影规则。

### Modified Capabilities

## Impact

- 后端运行时：`BaseAgent`、`ToolResultPayload`、重点工具实现与工具调用账本写入链路。
- 历史重放：`ReplayProjector` 及其按工具分发的 projector registry / parser 实现。
- 调试与查询：工具调用视图、回放测试、运行时账本断言。
- 文档与规格：需要同步对齐现有会话历史 replay 方案，避免两套 `output_json` 语义并存。
