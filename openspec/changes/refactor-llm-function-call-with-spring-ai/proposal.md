## Why

`LLM.java` 当前在 `function_call` 非流式链路中同时承担消息格式转换、工具定义组装、HTTP 请求发送和响应解析职责，导致实现臃肿、重复逻辑多，并且已经和项目现有的 Spring AI / Armory 模型装配体系分叉。现在先对低风险链路做小步替换，可以在不改动 Agent 工具执行主循环的前提下，先收敛最重的请求发送与基础解析逻辑。

## What Changes

- 将 `LLM.askTool(..., function_call, stream=false)` 的底层模型调用从手写 OkHttp 请求切换为 Spring AI `OpenAiChatModel.call()`。
- 提取并复用 `function_call` 链路的公共消息转换、工具定义转换和模型选项组装逻辑，减少 `LLM.java` 内部重复分支。
- 保留现有 Agent 侧“LLM 产出 tool calls，Agent 手动执行工具，再回填记忆”的控制模式，不引入 Spring AI 内部自动工具执行。
- 保留现有 `struct_parse` 链路不变。
- 保留现有 `function_call` 流式链路不变，本 change 不处理 OpenAI-compatible 流式和 Claude 专有流式迁移。
- 为小步替换后的非流式 `function_call` 链路补齐回归验证，覆盖消息转换、工具调用返回和兼容边界。

## Capabilities

### New Capabilities
- `llm-function-call-execution`: 定义 `LLM` 在 `function_call` 非流式场景下如何基于 Spring AI 完成模型调用，同时保持现有工具调用控制权和结果契约不变。

### Modified Capabilities
- None.

## Impact

- 影响代码主要位于 `ai-agent-station-study-domain` 的 `reactor/agent/llm` 包及其相关工具适配层。
- 会复用现有 Spring AI `OpenAiChatModel` 装配能力，以及现有 MCP `ToolCallback` 运行时缓存能力。
- 不修改上层 Agent API、前端协议和 `struct_parse` 行为。
- 需要补充 `askTool(function_call, stream=false)` 相关测试，确保回包内容、tool calls 结构和工具执行主循环兼容。
