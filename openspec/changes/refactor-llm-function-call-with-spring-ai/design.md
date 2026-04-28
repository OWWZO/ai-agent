## Context

当前 `LLM.java` 在 `askTool(..., function_call, stream=false)` 链路中自行完成以下职责：

- 将领域层 `Message` / `ToolCollection` 转成 OpenAI 兼容请求结构
- 通过 OkHttp 发送 `/v1/chat/completions` 请求
- 解析响应中的 `message.content` 与 `tool_calls`
- 将结果回填为当前项目使用的 `ToolCallResponse`

与此同时，项目已经在其他链路中引入 Spring AI：

- Armory 节点已经能装配 `OpenAiApi` / `OpenAiChatModel`
- MCP 工具已经有 Spring AI 原生 `ToolCallback` 运行时缓存
- 领域层之外的部分能力已经在使用 `OpenAiChatModel`

但 Reactor Agent 侧的 `LLM` 仍由 `llm.settings` 驱动，且由上层 Agent 明确掌控“模型产出工具调用指令 -> Agent 手动执行工具 -> 工具结果回写记忆”这一执行闭环。这个闭环不能在本次小步替换中被改变。

本次设计聚焦于最小闭环：

```text
当前阶段
┌────────────────────────────────────────────┐
│ askTool(function_call, stream=false)       │ 迁移到 Spring AI
├────────────────────────────────────────────┤
│ askTool(function_call, stream=true)        │ 保持旧实现
├────────────────────────────────────────────┤
│ askTool(struct_parse, all modes)           │ 保持旧实现
├────────────────────────────────────────────┤
│ ask()/普通对话链路                         │ 保持旧实现
└────────────────────────────────────────────┘
```

## Goals / Non-Goals

**Goals:**

- 用 Spring AI `OpenAiChatModel.call()` 替换 `function_call` 非流式链路中的手写 HTTP 请求与基础响应解析。
- 提取可复用的消息转换、工具定义转换、模型选项组装逻辑，为后续流式迁移预留稳定边界。
- 保持 `LLM.askTool(...)` 方法签名、`ToolCallResponse` 结构和上层 Agent 工具执行主循环不变。
- 复用现有 MCP `ToolCallback` 运行时能力，避免再造一套 MCP Spring AI 适配层。
- 在兼容性不满足时保留回退空间，避免一次性替换导致 Planning / ReAct / Executor 主链路失稳。

**Non-Goals:**

- 不迁移 `function_call` 流式分支。
- 不迁移 `struct_parse` 链路。
- 不重写 `ask()` 普通对话链路。
- 不删除现有 OkHttp 相关旧实现。
- 不引入 Spring AI 内部自动工具执行。
- 不要求本阶段与 Armory 的 DB 驱动模型 Bean 完全统一。

## Decisions

### 1. 只替换 `askTool(function_call, stream=false)`，其余链路继续复用旧实现

**Decision**

在 `LLM.askTool(...)` 内按 `functionCallType=function_call && stream=false` 切到新的 Spring AI 执行器；其余分支继续走现有代码。

**Rationale**

- 这是当前收益最高、协议复杂度最低的分支。
- 上层 Agent 仍然是同步等待完整 `ToolCallResponse`，不涉及“边流边执行工具”的新行为。
- 可以先验证消息转换、工具定义转换和返回结构映射是否稳定，再考虑流式分支。

**Alternatives considered**

- 一次性替换 `LLM.java` 全部分支：改动面过大，排错成本高，和当前“小步替换”目标冲突。
- 先迁移流式分支：流式分支仍包含推送节奏、chunk 聚合和供应商差异，风险高于非流式。

### 2. 第一阶段不强依赖 Armory 模型 Bean，改为基于 `LLMSettings` 的 Spring AI 模型工厂

**Decision**

新增一个 Spring 管理的 `LlmSpringAiModelFactory`（命名可在实现时微调），输入为当前 `LLMSettings`，输出为可复用的 `OpenAiChatModel`。该工厂优先服务 Reactor `LLM` 侧的 `llm.settings` 配置，不把第一阶段绑定到 Armory 的 `modelId -> bean` 装配关系。

**Rationale**

- 当前 `LLM` 的调用入口是 `new LLM(modelName, ...)`，直接依赖 `llm.settings` 和 `ReactorConfig`，并不掌握 `modelId`。
- Armory 的 `OpenAiChatModel` Bean 是按 `modelId` 注册，若第一阶段强行扫描 `modelName -> bean`，会引入 DB 配置前提、同名模型歧义和多环境不一致问题。
- 这次目标是替换 HTTP 与解析逻辑，而不是完成全局模型装配统一。

**Alternatives considered**

- 扫描 Spring 容器中所有 `OpenAiChatModel`，按 `defaultOptions.model` 匹配：存在同名模型歧义，且过度依赖运行时装配数据。
- 找不到 Bean 时再兜底新建：会形成“有时走 Armory，有时走本地工厂”的隐式双源，行为不够清晰。

### 3. 明确关闭 Spring AI 内部工具执行，保留 Agent 自己的工具循环

**Decision**

在构造 `OpenAiChatOptions` 时显式设置 `internalToolExecutionEnabled(false)`，仅让模型返回 tool calls，不让 Spring AI 代为调用工具。

**Rationale**

- 当前 Agent 主链路依赖 `ToolCallResponse.toolCalls` 结果，由 `BaseAgent.executeTools(...)` 统一执行工具、记录日志并回写记忆。
- 如果开启 Spring AI 内部工具执行，`LLM` 的职责边界会改变，Agent 对工具执行时机、并发和观测的控制也会被破坏。

**Alternatives considered**

- 直接使用 Spring AI 自动工具执行：和现有 Reactor Agent 架构冲突，不属于本 change 的小步替换范围。

### 4. MCP 工具复用现有 `ToolCallback`，只为 `BaseTool` 增加轻量适配

**Decision**

- MCP 工具定义与执行直接复用 `McpRegistry` / `RegistryBackedToolCallback`
- 本地 `BaseTool` 新增一个轻量 `ToolCallback` 适配器，将 `execute(Object input)` 包装成 Spring AI 的工具接口

**Rationale**

- MCP 工具已经有缓存、schema 规范化和执行入口，不需要重复构建一套 Spring AI 适配层。
- 本地工具数量有限，单独做轻量适配即可，不会把 `ToolCollection` 重构成新的工具系统。

**Alternatives considered**

- 同时新增 `McpToolCallbackAdapter` 与 `BaseToolCallbackAdapter`：会与已有 MCP 运行时重复，增加维护面。

### 5. 领域消息转换采用“两段式转换”，补齐 tool result 的 tool name

**Decision**

新增一个仅服务于 `function_call` Spring AI 非流式链路的消息转换器，转换规则如下：

- `USER` -> `UserMessage`
- `SYSTEM` -> `SystemMessage`
- `ASSISTANT`（无工具）-> `AssistantMessage`
- `ASSISTANT`（有工具调用）-> 带 `toolCalls` 的 `AssistantMessage`
- `TOOL` -> `ToolResponseMessage`

其中 `TOOL` 消息需要先通过前序 `ASSISTANT.toolCalls` 构建 `toolCallId -> toolName` 索引，再生成 `ToolResponseMessage.ToolResponse(id, name, responseData)`。

**Rationale**

- 当前领域消息 `Message.toolMessage(...)` 只保存 `toolCallId` 与结果内容，没有直接保存 tool name。
- Spring AI 的 `ToolResponseMessage` 需要 `id/name/responseData` 三元组，因此必须在转换阶段补齐 name。
- 通过扫描同一轮历史中的 assistant tool call，可以在不改领域模型的前提下恢复所需信息。

**Alternatives considered**

- 直接给 `ToolResponseMessage` 传空 name：无法保证底层模型请求符合 Spring AI 预期。
- 修改现有领域消息结构，给 `TOOL` 消息增加 tool name：会扩大领域模型影响面，不符合本次小步替换原则。

### 6. `extParams` 采用“显式字段映射 + extraBody 透传”

**Decision**

对常见 OpenAI 兼容参数做显式映射，例如：

- `temperature`
- `max_tokens`
- `top_p`
- `frequency_penalty`
- `presence_penalty`
- `parallel_tool_calls`

剩余 Spring AI 无专属字段但又需要继续传给网关的参数，统一放入 `OpenAiChatOptions.extraBody`。

**Rationale**

- 这样可以优先利用 Spring AI 的类型能力，同时保住现有网关兼容性。
- 全量手工枚举所有供应商扩展字段收益不高，也容易遗漏。

**Alternatives considered**

- 所有 `extParams` 都放 `extraBody`：可行，但会失去 Spring AI 对标准字段的显式约束与可读性。
- 只支持少量白名单字段，不透传其他参数：会破坏现有模型网关兼容性。

### 7. 为第一阶段保留旧非流式分支作为受控回退路径

**Decision**

在新的 Spring AI 非流式执行器落地后，旧的非流式 `function_call` HTTP 分支暂不删除；若模型创建、消息转换或响应映射发生未覆盖异常，可记录日志并回退到旧分支。

**Rationale**

- 这次目标是先稳住替换收益，而不是立即做彻底清理。
- 回退路径可以降低小步上线的心理成本，也方便快速对比新旧链路。

**Alternatives considered**

- 直接删掉旧分支并强制全量切换：更“干净”，但不符合低风险演进目标。

## Risks / Trade-offs

- [Tool result 需要补齐 tool name] → 通过扫描前序 assistant tool calls 建索引；若历史不完整则记录告警并回退旧分支。
- [Spring AI 默认可能内部执行工具] → 在 `OpenAiChatOptions` 中显式关闭 `internalToolExecutionEnabled`，并为该行为补测试。
- [`llm.settings` 与 Armory 模型配置长期并存] → 本阶段先接受双轨来源，但把工厂边界收敛在 `function_call` 非流式链路，后续单独治理统一装配。
- [OpenAI-compatible 网关返回结构有差异] → 保留现有 arguments 规范化逻辑，并在响应映射异常时回退旧分支。
- [旧代码短期内不会明显变短] → 这是阶段性 trade-off，优先确保替换安全，等后续流式与其他分支迁移完成后再做清理。

## Migration Plan

1. 新增 Spring AI 非流式执行所需的模型工厂、消息转换器、工具回调适配器与响应映射器。
2. 在 `LLM.askTool(...)` 中仅为 `function_call && !stream` 分支接入新执行器。
3. 保留旧非流式 `function_call` 分支作为受控回退路径，并打出清晰日志区分新旧执行路径。
4. 补齐以下回归验证：
   - 有工具调用的非流式响应
   - 无工具调用的非流式响应
   - 含 MCP 工具与 BaseTool 混合场景
   - 多轮对话中 assistant tool call + tool result 的历史回放
5. 等该分支稳定后，再单独评估第二阶段是否迁移 OpenAI-compatible 流式分支。

**Rollback**

- 若 Spring AI 非流式链路出现兼容性问题，可直接恢复到旧的非流式 `function_call` 分支，不影响流式分支与 `struct_parse` 链路。

## Open Questions

- 第二阶段是否优先迁移 OpenAI-compatible 流式分支，而继续延后 Claude 专有流式分支。
- 在第一阶段验证完成后，是否需要再发起独立 change，把 Reactor `LLM` 的模型来源逐步并入 Armory 统一装配。
