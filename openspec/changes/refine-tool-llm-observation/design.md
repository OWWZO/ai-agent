## Context

当前工具执行链路里，`BaseAgent.executeToolInternal(...)` 先拿到工具返回字符串，再尝试推导 `outputText` / `outputJson`，并在 `executeTools(...)` 阶段立即调用 `finishToolInvocation(...)` 写账本。随后 `ReactImplAgent`、`ExecutorAgent`、`PlanningAgent` 才会对该结果继续做面向主智能体的后处理，例如：

- 根据 `maxObserve` 截断超长内容；
- 追加工具产物摘要（`关联文件`）；
- 写入 `Message.toolMessage(...)`，作为下一轮主智能体消费的 observation。

这导致当前账本里的 `output_text` 与主智能体真正看到的工具结果并不总是一致。`deep_search` 的问题更明显：它返回的是完整结构化 JSON 字符串，主智能体会直接收到整包 `stages` 数据，噪音大、重点不稳定，也不利于模型理解“实际搜到了哪些来源”。

这次变更同时涉及：

- 数据库字段语义调整；
- 工具执行账本写入时机；
- ReAct / Executor / Planning 三条工具消费链的统一；
- `deep_search` observation 生成策略。

因此需要单独的设计收口，而不是只做字段名替换。

## Goals / Non-Goals

**Goals:**

- 为工具调用账本提供明确的 `llm_oberserve` / `llmObservation` 语义，专门表示“回传给主智能体的 observation 文本”。
- 保证账本里的 `llmObservation` 与实际写入主智能体 `tool` message 的内容完全一致。
- 保留 `output_json` 作为工具最终结构化结果，不再让它与主智能体 observation 语义混用。
- 让 `deep_search` 生成精简、稳定、可被主智能体直接消费的检索摘要，而不是继续回传完整阶段 JSON。
- 统一单工具执行与批量工具执行的后处理逻辑，避免 Planning / ReAct / Executor 行为漂移。

**Non-Goals:**

- 不修改现有前端实时 SSE 协议与 `deep_search` 对前端展示的阶段事件结构。
- 不把 `ai_agent_tool_invocation.output_json` 改造成对话历史的唯一 canonical 数据源。
- 不重做所有工具的返回 DTO；本次只要求它们能稳定产出 LLM observation 与结构化结果。
- 不新增前端页面或新的对外 API。

## Decisions

### Decision 1: 将账本字段语义拆成“主智能体 observation”与“结构化结果”两层

采用方案：

- 数据库层把 `output_text` 更名为 `llm_oberserve`，语义固定为“最终写入主智能体 `tool` message 的 observation 文本”。
- Java/DTO/VO/Mapper 层使用规范命名 `llmObservation`，通过字段映射连接到数据库列 `llm_oberserve`。
- `output_json` 保留为工具的最终结构化结果，不再承担“主智能体实际看到的文本”语义。

原因：

- 用户已经明确要求该字段表达“工具传给主智能体的执行结果”。
- 数据库列名按需求落地，代码层保留正确英文语义，可以避免把拼写问题继续扩散到 Java API。
- 将两类输出拆开后，账本、调试与后续分析都更容易判断“模型看到了什么”和“工具完整产出了什么”。

备选方案：

- 方案 A：只改注释不改列名。问题是旧名会持续误导调用方。
- 方案 B：统一只留 `output_json`。问题是会丢失主智能体真实 observation 这一独立事实。

### Decision 2: 在 BaseAgent 中收口 observation 的最终形态，再持久化与写入 memory

采用方案：

- 引入统一的 observation 终态组装逻辑，例如 `finalizeLlmObservation(toolCallId, rawObservation)`：
  - 应用 `maxObserve` 截断；
  - 追加工具产物摘要；
  - 产出最终 observation 字符串。
- `executeTool()` 与 `executeTools()` 都复用该逻辑，得到同一份 canonical observation。
- `finishToolInvocation(...)` 写入的 `llmObservation`，必须使用这份最终 observation，而不是工具原始返回值。
- `Message.toolMessage(...)` 也使用这份最终 observation，确保主智能体看到的内容与账本一致。

原因：

- 当前账本更新发生在 agent 后处理之前，天然会与真实 tool message 脱节。
- 单工具路径（Planning）与批量路径（ReAct / Executor）目前存在行为差异，需要统一收口。

备选方案：

- 方案 A：保持当前 `executeTools()` 提前落库，再在 agent 层补写第二次。问题是写入链路复杂，且容易出现双源不一致。
- 方案 B：各个 agent 自己分别拼装 observation。问题是重复逻辑多，后续维护容易继续漂移。

### Decision 3: deep_search 拆分“前端结构化结果”和“主智能体检索摘要”

采用方案：

- 保留当前 `DeepSearchStructuredResultBuilder` 的职责：为 `output_json` 生成完整结构化结果，继续承载 `extend/search/report` 三阶段信息。
- 为 `deep_search` 增加一份独立的 LLM observation 构建逻辑，生成紧凑 JSON 文本或等价的稳定结构化字符串，至少包含：
  - 原始 query；
  - 拆解后的子查询列表；
  - 每个子查询命中的文档列表；
  - 每条文档的 `title`、`link`、内容摘要；
  - 可选的最终回答摘要。
- 主智能体消费这份精简 observation，而不是完整 `output_json`。

原因：

- 前端需要完整阶段结果用于展示，但主智能体只需要“搜到了什么、来源是什么、内容大意是什么”。
- 保留两套结果能避免把前端展示协议强绑到主智能体上下文上。

备选方案：

- 方案 A：继续让主智能体消费完整 `output_json`。问题是 token 噪音高，且阶段信息对推理帮助不大。
- 方案 B：只返回最终 answer 文本。问题是主智能体失去来源级证据，无法知道检索覆盖面与命中文档。

### Decision 4: 对 deep_search observation 做确定性的裁剪与摘要

采用方案：

- 对 `searchResult.docs` 做确定性裁剪，例如限制每个子查询保留前 N 条文档、每条文档内容摘要截断到固定长度。
- observation 里保留 `title`、`link`、内容摘要，不直接灌入全文。
- 当 deep_search 超时或失败时，`llmObservation` 退化为错误/超时说明文本，不强行生成伪结构化结果。

原因：

- 用户希望主智能体知道检索来源和内容，但如果不裁剪，deep_search observation 很容易再次膨胀成大 JSON。
- 确定性裁剪能稳定 token 规模，也更利于测试。

备选方案：

- 方案 A：全文透传文档内容。问题是与“精简化”目标冲突。
- 方案 B：只保留标题和链接不带摘要。问题是主智能体很难理解命中文档的实际信息量。

### Decision 5: 保持现有前端协议不变，output_json 仍然只代表工具最终结构化结果

采用方案：

- `deep_search` 对前端仍继续发送当前阶段型 SSE 事件。
- `output_json` 继续存最终完整结构化结果，供账本调试和后续分析使用。
- 不把前端所有事件轨迹塞进 `output_json`，避免工具账本与展示事件模型耦死。

原因：

- 这次需求的核心是主智能体 observation 和 deep_search tool result，不是历史回放协议重构。
- 现有前端展示链路已经依赖 `deep_search` 分阶段事件，保持稳定更安全。

## Risks / Trade-offs

- [Planning 单工具执行路径与 ReAct/Executor 行为不一致] → 将 observation 收口与账本写入逻辑下沉到 `BaseAgent` 共享层，避免继续在各 agent 手写分支。
- [数据库列名按需求使用 `llm_oberserve`，存在拼写不标准] → 代码层统一使用 `llmObservation`，通过 Mapper 显式映射隔离拼写问题。
- [deep_search observation 过度裁剪，导致主智能体丢失关键信息] → 固定保留 query、title、link 与内容摘要，并为摘要长度和文档条数提供集中配置或常量。
- [structured output 与 llmObservation 双写后出现不一致] → 由统一的 tool outcome 模型一次性产出两份内容，并在同一后处理链路内写账本与 memory。
- [历史调试脚本仍读取旧 `output_text` 字段] → 一并调整 Mapper、QueryService 与相关视图对象，避免保留旧语义别名造成混淆。

## Migration Plan

1. 数据库迁移：将 `ai_agent_tool_invocation.output_text` 重命名为 `llm_oberserve`，更新注释。
2. 更新实体、Mapper XML、账本查询视图与服务层字段命名，使代码层统一使用 `llmObservation`。
3. 重构 `ToolExecutionOutcome` 与 `BaseAgent` 工具后处理链路，先收口最终 observation，再统一写账本和 memory。
4. 改造 `deep_search`：同时产出完整 `output_json` 与精简 `llmObservation`。
5. 补齐领域层 / app 层验证，覆盖普通工具、deep_search、超时/失败、Planning 单工具路径。
6. 上线后通过账本查询检查 `llm_oberserve` 是否与实际 tool message 一致；若需要回滚，可恢复旧字段名并回退 observation 构建逻辑。

## Open Questions

- `deep_search` observation 最终采用紧凑 JSON 字符串还是更偏 Markdown 的文本块；本次默认推荐紧凑 JSON，便于模型稳定提取字段。
- 是否需要把 deep_search observation 的裁剪参数做成配置项；若当前未发现频繁调优需求，可先用常量实现，后续再外提配置。
