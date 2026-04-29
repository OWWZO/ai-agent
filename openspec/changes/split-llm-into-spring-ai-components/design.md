## Context

当前 `ai-agent-station-study-domain` 的 Reactor Agent 主链路仍依赖 [LLM.java](/D:/Java%20Code/ai-agent/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/llm/LLM.java) 直接管理消息转换、工具定义组装、OpenAI-compatible HTTP 请求、SSE 解析和流式推送节奏。`PlanningAgent`、`ReactImplAgent`、`ExecutorAgent` 通过 `askTool(...)` 获取文本和工具调用指令，再由 `BaseAgent.executeTool()/executeTools()` 手动执行工具；`SummaryAgent`、`ReActAgent.generateDigitalEmployee()`、`LlmSessionMemorySummaryGenerator` 通过 `ask(...)` 消费纯文本响应。

项目内部已经存在 Spring AI / Armory 装配链：

- Armory 能注册 `OpenAiApi` 与 `OpenAiChatModel`
- MCP 工具已经有 `RegistryBackedToolCallback`
- 其他流程中已经在消费 `ChatClient` / `Flux<ChatResponse>`

但 Reactor `LLM` 仍按 `new LLM(modelName, "")` 方式直接实例化，并且目前存在一个半迁移状态的 `askToolWithChatClientStructParse(...)` 过渡分支。这说明仓库已经尝试过 Spring AI 路线，但边界没有被正式固化，导致新旧链路长期并存。

本次设计需要满足以下约束：

- 不改变 `LLM` 的构造方式
- 不改变 `ask/askTool` 方法签名和 `ToolCallResponse` 结构
- 不改变 Agent 自己的工具执行主循环
- 不改变现有 `plan_thought` / `tool_thought` / `agent_stream` 的流式消息类型
- 允许阶段性共存新旧分支，并保留回退路径

## Goals / Non-Goals

**Goals:**

- 将 `LLM` 收敛为稳定门面，把消息转换、模型解析、选项装配、流式转发等职责从传输实现中拆开。
- 用 Spring AI 承接 OpenAI-compatible 模型调用、消息序列化和基础响应解析，逐步替换手工 HTTP / SSE 逻辑。
- 保持 `ask/askTool` 的外部契约、Agent 记忆回放语义和工具执行主循环不变。
- 显式定义阶段化迁移顺序，避免一次性替换所有分支。
- 为后续删除旧 `OkHttp` 分支创造可验证的清理路径。

**Non-Goals:**

- 不在本次设计中改写 `BaseAgent`、`PlanningAgent`、`ReactImplAgent`、`ExecutorAgent` 的核心工作流。
- 不要求本次就把 `struct_parse` 改造成完全等价的 Spring AI 原生方案。
- 不要求第一阶段直接并入 Armory 的 `modelId -> bean` 体系。
- 不修改前端协议、消息类型名称或打印器接口。
- 不承诺第一阶段就把 `LLM.java` 立即压缩到最终目标行数。

## Decisions

### 1. 将重构目标定义为“3 个主组件 + 2 个配套组件”

**Decision**

主职责面拆为：

- `DomainMessageConverter`
- `StreamResponseHandler`
- `LLM` 门面

同时引入两个配套边界：

- `LlmChatModelResolver`
- `OpenAiChatOptionsFactory`（可合并 `ToolCallbackProvider`）

**Rationale**

- 只声明 3 个类会把模型解析、参数装配、工具回调适配重新塞回 `LLM`，最终仍然形成胖门面。
- 主组件负责业务边界，配套组件负责与 Spring AI 的适配边界，职责更清晰。

**Alternatives considered**

- 仅拆出 `DomainMessageConverter` 与 `StreamResponseHandler`：`LLM` 仍会承载 resolver/options/tool adapter 胶水逻辑，不能形成稳定门面。
- 直接把所有职责拆成更多细粒度类：第一阶段理解成本过高，不利于阶段化迁移。

### 2. `LLM` 继续保持手工实例化门面，而不是改造成 Spring Bean

**Decision**

`LLM` 继续保留 `new LLM(modelName, "")` 的构造方式，通过 `SpringContextHolder` 获取 resolver、converter、stream handler 等依赖。

**Rationale**

- 现有 Agent 构造函数全部直接 `new LLM(...)`，例如 `PlanningAgent`、`ReactImplAgent`、`SummaryAgent` 和 `LlmSessionMemorySummaryGenerator`。
- 先稳定行为边界，再讨论实例生命周期，风险更低。

**Alternatives considered**

- 直接把 `LLM` 改成 Spring Bean 并向上改 Agent 构造链：会扩大影响面，不符合本次“先稳主链”的目标。

### 3. 第一阶段优先使用 `LLMSettings -> OpenAiApi/OpenAiChatModel` 解析器，而不是强绑 Armory Bean

**Decision**

`LlmChatModelResolver` 第一阶段优先从 `LLMSettings` 构造 `OpenAiApi` / `OpenAiChatModel`，并以 `modelName + llm settings` 为稳定输入。Armory 注册的模型 Bean 在后续阶段再评估并轨。

**Rationale**

- 当前 Reactor `LLM` 的输入是 `modelName`，并不掌握 Armory `modelId`。
- 现有 `AiClientApiNode` 目前没有显式装配 `completionsPath/interfaceUrl`，而旧 `LLM` 仍依赖 `interfaceUrl`。
- 先用 `LLMSettings` 保持行为兼容，再推进统一模型来源更稳。

**Alternatives considered**

- 直接扫描 Spring 容器中的 `OpenAiChatModel`：存在同名模型歧义，并且强依赖运行时装配数据。
- 找不到 Armory Bean 时再兜底新建：会形成不透明的双源路径，行为不可预期。

### 4. `DomainMessageConverter` 采用“两段式转换”，显式恢复 tool result 的 `toolName`

**Decision**

`DomainMessageConverter` 不是简单逐条映射器，而是：

1. 先扫描 assistant tool calls，建立 `toolCallId -> toolName` 索引
2. 再把 `TOOL` 消息转换成 `ToolResponseMessage.ToolResponse(id, name, responseData)`

**Rationale**

- 当前领域 `Message.toolMessage(...)` 只有 `toolCallId` 和内容，没有 `toolName`。
- Spring AI `ToolResponseMessage` 需要完整的 `id/name/responseData` 三元组。
- 不修改领域消息结构就能兼容历史回放，影响面最小。

**Alternatives considered**

- 直接修改领域 `Message` 模型，给 `TOOL` 增加 `toolName`：会扩大上下游改动。
- 给 `ToolResponseMessage` 传空 name：不满足底层工具回放要求。

### 5. 工具定义与执行分离：Spring AI 负责声明，Agent 负责执行

**Decision**

- `OpenAiChatOptions` 显式设置 `internalToolExecutionEnabled(false)`
- MCP 工具直接复用现有 `RegistryBackedToolCallback`
- 本地 `BaseTool` 只增加轻量 `ToolCallback` 适配

**Rationale**

- 当前 Agent 主链路的控制点在 `BaseAgent.executeTools(...)`
- 工具执行时机、并发和日志观测都由 Agent 保持控制
- 现有 MCP 运行时已经有成熟缓存和执行入口，无需重复建模

**Alternatives considered**

- 直接使用 Spring AI 内部工具执行：会破坏现有 Agent 控制闭环。
- 把 `ToolCollection` 整体重构成新的工具系统：收益不足，改动过大。

### 6. `OpenAiChatOptions` 采用“显式字段映射 + extraBody 透传”

**Decision**

对 `temperature/maxTokens/topP/frequencyPenalty/presencePenalty/parallelToolCalls/toolChoice` 等标准字段做显式映射，剩余 `extParams` 进入 `extraBody`。

**Rationale**

- 这样既保住现有网关兼容性，也避免把新的参数装配退化成无类型 Map。
- `OpenAiChatOptions` 已经提供 `internalToolExecutionEnabled`、`toolCallbacks`、`extraBody` 等承载点，足够覆盖当前需要。

**Alternatives considered**

- 全部参数直接 `putAll(extParams)`：延续旧问题，缺少类型边界。
- 只支持显式白名单字段、不透传剩余参数：会破坏现有网关兼容性。

### 7. `StreamResponseHandler` 只负责订阅与转发，不再承担 SSE 协议解析

**Decision**

流式处理统一迁移为 `Flux<ChatResponse>` 订阅，`StreamResponseHandler` 只负责：

- 聚合文本
- 按 `messageInterval` 推送
- 收尾 flush
- 生成最终 `String` 或 `ToolCallResponse`

SSE 行级解析、`data: ...` 过滤、供应商 chunk JSON 反序列化不再由业务代码处理。

**Rationale**

- 协议层应该由 Spring AI / provider client 吸收
- 业务层只保留推送语义和最终聚合语义

**Alternatives considered**

- 把 `Flux<ChatResponse>` 再手动还原成字符串 SSE 流：没有价值，只是把协议问题重新搬回来。

### 8. 按调用契约分阶段迁移，而不是按类一次性替换

**Decision**

迁移顺序定义为：

1. Phase 0：抽边界，不切主链
2. Phase 1：`askTool(function_call, stream=false)`
3. Phase 2：`ask(stream=false)`
4. Phase 3：`ask(stream=true)` 纯文本
5. Phase 4：`askTool(function_call, stream=true)`
6. Phase 5：`struct_parse` 收尾

**Rationale**

- `ExecutorAgent` 已经固定使用 `askTool(..., stream=false)`，最适合做第一阶段验证。
- `SummaryAgent` 固定走 `ask(..., stream=true)`，说明纯文本流式不是边角料，必须在工具流式前稳定。
- `askTool` 流式是最复杂的分支，应该最后处理。

**Alternatives considered**

- 一次性替换 `LLM` 全部分支：排障面太大。
- 先做流式：比非流式复杂，回归成本高。

## Risks / Trade-offs

- [领域 `TOOL` 消息缺少 `toolName`] → 通过 assistant tool call 索引恢复，缺失时告警并回退旧分支。
- [Armory Bean 与 `LLMSettings` 语义不一致] → 第一阶段优先使用 `LLMSettings` 解析器，后续单独治理模型来源统一。
- [流式 tool call 在 `Flux<ChatResponse>` 中仍需额外归并] → 在 `StreamResponseHandler` 中预留最终聚合能力，并用真实流式场景验证。
- [迁移期 `LLM.java` 不会立刻缩到最终目标行数] → 接受阶段性共存，待流式和兼容分支稳定后再做清理。
- [`struct_parse` 与 Spring AI 抽象不完全同构] → 保持兼容分支，最后收尾，不在前期强求完全统一。

## Migration Plan

1. 抽出 `DomainMessageConverter`、`LlmChatModelResolver`、`OpenAiChatOptionsFactory`、`StreamResponseHandler`，先不切换主调用分支。
2. 落地 `askTool(function_call, stream=false)` 新链路，并保留旧分支回退开关。
3. 用同样的消息转换与选项装配边界迁移 `ask(stream=false)`。
4. 迁移 `ask(stream=true)`，确保 `SummaryAgent` 和 `agent_stream` 推送语义不变。
5. 迁移 `askTool(function_call, stream=true)`，验证文本增量和工具调用聚合。
6. 视验证结果决定是否将 `struct_parse` 迁移到底层 Spring AI 调用，或长期保留兼容路径。

**Rollback**

- 每一阶段都保留对应旧 `OkHttp` 分支作为受控回退路径。
- 只要出现模型装配异常、消息回放异常、工具调用映射异常或流式聚合异常，即刻回切旧分支，不阻断 Agent 主链路。

## Open Questions

- `struct_parse` 最终是继续保留兼容分支，还是只替换底层传输层而保留提示词协议？
- 当 `LLMSettings` 与 Armory 模型配置最终统一时，是否需要再发一个独立 change 清理双轨模型来源？
- `Flux<ChatResponse>` 在当前网关下返回的 tool call 颗粒度是否足够稳定，能否直接减少现有 tool-call merge 逻辑？
