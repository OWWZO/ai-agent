## Why

`LLM.java` 目前把消息 DTO 转换、工具定义组装、OpenAI-compatible HTTP 请求、SSE 流解析、流式推送节奏和响应回放都混在一个 1789 行的大类里，已经成为 Reactor Agent 主链路中最难维护、最难验证的单点。项目内部已经同时存在 Spring AI / Armory 装配链与 `LLM` 自建链路，两套模型调用方式长期并存，导致同一能力在消息格式、工具回放和流式处理上持续分叉。

当前仓库已有一个仅覆盖 `askTool(function_call, stream=false)` 的小步迁移 change，但它不足以承载这次已经明确的目标：把 `LLM` 收敛成稳定门面，并将消息转换、流式转发、模型装配边界从传输实现中拆出来。现在需要用新的 change 正式定义完整的组件化方案、阶段化迁移顺序和回退策略，避免后续实现继续在旧的分支结构上打补丁。

## What Changes

- 将 `LLM` 重构为“精简门面 + 独立组件”结构，门面继续保留现有构造方式以及 `ask/askTool` 对外签名。
- 新增 `DomainMessageConverter`，负责把领域 `Message` 转换为 Spring AI `SystemMessage/UserMessage/AssistantMessage/ToolResponseMessage`，替代现有 `formatMessages` 和手工 GPT/Claude 分支。
- 新增 `StreamResponseHandler`，负责 `Flux<ChatResponse>` 订阅、聚合和按 `messageInterval` 推送，替代当前 SSE 行级解析与定时发送逻辑。
- 新增模型解析与选项装配边界，用 `LLMSettings` 过渡构造 `OpenAiChatModel` / `OpenAiChatOptions`，统一承接 `extParams` 映射、`toolChoice`、`internalToolExecutionEnabled(false)` 和工具回调提供。
- 保持 `BaseAgent.executeTool()/executeTools()` 主循环不变，Spring AI 只负责生成文本和工具调用参数，不接管工具执行。
- 采用阶段化迁移顺序：先迁 `askTool(function_call, stream=false)`，再迁 `ask` 非流式与流式，最后迁 `askTool` 流式；`struct_parse` 作为兼容分支保留并在最后收尾。
- 为每个阶段补齐验证与回退路径，确保 Planning / React / Executor / Summary / Session Memory 历史回放语义不回退。

## Capabilities

### New Capabilities
- `llm-spring-ai-facade`: 定义 Reactor `LLM` 如何在不改变 `ask/askTool/ToolCallResponse` 契约的前提下，改为通过 Spring AI 完成消息调用、工具调用生成和历史消息回放。
- `llm-stream-forwarding`: 定义 `LLM` 在迁移到 `Flux<ChatResponse>` 后，如何保持现有的流式消息类型、推送节奏、最终聚合结果和工具调用聚合语义。

### Modified Capabilities
- None.

## Impact

- 主要影响 `ai-agent-station-study-domain` 下的 `reactor/agent/llm`、`reactor/agent/tool`、`reactor/agent/agent` 相关链路。
- 需要复用并衔接现有 Spring AI `OpenAiChatModel` / `OpenAiChatOptions`、Armory `OpenAiApi/OpenAiChatModel` 装配能力、MCP `RegistryBackedToolCallback` 缓存能力。
- 不修改 Agent 构造方式、`LLM.ask/askTool` 方法签名、`ToolCallResponse` 结构和前端消费协议。
- 需要补充覆盖文本非流式、文本流式、工具调用非流式、工具调用流式、assistant/tool 历史回放、回退旧分支等场景的回归验证。
