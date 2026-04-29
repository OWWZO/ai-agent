## 1. 抽离 Spring AI 适配边界

- [x] 1.1 新增 `LlmChatModelResolver`，基于 `LLMSettings` 构造并复用 `OpenAiApi/OpenAiChatModel`，保持 `new LLM(modelName, "")` 调用方式不变
- [x] 1.2 新增 `OpenAiChatOptionsFactory`，显式映射标准请求参数并将剩余 `extParams` 透传到 `extraBody`
- [x] 1.3 新增本地 `BaseTool` 的 `ToolCallback` 适配器，并复用现有 MCP `RegistryBackedToolCallback` 组装统一工具回调提供器

## 2. 抽离消息转换与响应映射

- [x] 2.1 新增 `DomainMessageConverter`，完成 `SYSTEM/USER/ASSISTANT` 到 Spring AI Message 的转换
- [x] 2.2 在 `DomainMessageConverter` 中实现 assistant tool call 扫描与 `toolCallId -> toolName` 索引恢复，支持 `TOOL` 消息回放为 `ToolResponseMessage`
- [x] 2.3 新增 `ChatResponse` 到纯文本结果与 `ToolCallResponse` 的映射逻辑，保留现有 arguments 规范化和响应异常兜底点

## 3. 迁移 `askTool(function_call, stream=false)`

- [x] 3.1 在 `LLM` 中接入新的 `function_call && !stream` 非流式路径，保持 `ToolCallResponse` 契约和 Agent 工具执行主循环不变
- [x] 3.2 为新非流式工具调用路径保留受控回退到旧 HTTP 分支的能力，并补充区分新旧链路的日志
- [ ] 3.3 补齐非流式工具调用回归验证，覆盖无工具响应、有工具响应、BaseTool/MCP 混合、assistant/tool 历史回放

## 4. 迁移 `ask(stream=false)`

- [x] 4.1 将 `ask(stream=false)` 切换到共享的 Spring AI `call()` 路径，复用消息转换与选项装配组件
- [ ] 4.2 验证 `ReActAgent.generateDigitalEmployee()` 与 `LlmSessionMemorySummaryGenerator` 在新非流式文本路径下保持原有输出与配置兼容

## 5. 迁移流式文本与流式工具调用

- [x] 5.1 新增 `StreamResponseHandler`，实现文本 `Flux<ChatResponse>` 的聚合、`messageInterval` 推送与最终 flush
- [x] 5.2 将 `ask(stream=true)` 纯文本路径切换到 `StreamResponseHandler`，验证 `SummaryAgent` 的 `agent_stream` 推送语义不变
- [x] 5.3 在 `StreamResponseHandler` 中补齐工具调用流的文本增量与最终工具调用聚合，并切换 `askTool(function_call, stream=true)` 路径
- [ ] 5.4 补齐流式回归验证，覆盖 `plan_thought` / `tool_thought` / `agent_stream` 推送、最终内容聚合、缓冲区末尾 flush 和工具调用返回

## 6. 兼容分支收尾与旧代码清理

- [x] 6.1 保留并梳理 `struct_parse` 兼容分支、阶段性回退路径和相关说明，确保未迁移能力不被误删
- [ ] 6.2 在所有迁移阶段验证通过后，删除不再需要的手写 HTTP / SSE 解析逻辑，并将 `LLM` 收敛为稳定门面
