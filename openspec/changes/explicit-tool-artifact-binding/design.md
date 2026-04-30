## Context

当前后端主链路中，文件产物的归属关系是分裂的：

- `Message.toolMessage(...)` 只记录工具文本结果和 `toolCallId`，不携带文件引用。
- 各类产文件工具会直接把 `File` 追加到 `AgentContext.productFiles` / `taskProductFiles`。
- 主智能体下一轮思考和 `SummaryAgent` 最终总结拿到的是扁平文件集合，只能依赖文件名、描述和顺序去猜测来源。
- `report_tool`、`deep_search`、`code_interpreter` 等工具存在异步回调写文件的路径，来源信息不能只靠同步线程本地变量传递。

用户已经明确废弃旧 transcript/turn/block 体系，因此本次设计不能把问题继续转移到历史账本或新持久化模型上，而应当直接在当前 ReAct / PlanSolve 运行时链路内解决“工具调用 -> 文件产物”的显式绑定问题。

## Goals / Non-Goals

**Goals:**

- 在当前请求运行期为每个生成文件建立明确的工具来源绑定，至少覆盖 `sessionId`、`requestId`、`toolCallId`、`toolName` 与文件引用的关系。
- 为同步工具和异步工具提供统一的产物登记入口，避免继续散落在各工具内部直接写 `productFiles` / `taskProductFiles`。
- 让主智能体下一轮消费工具结果时，能够精确知道本次工具调用生成了哪些文件。
- 让 `SummaryAgent` 在选择最终产物文件时优先使用显式绑定关系，而不是继续用文件名模糊匹配整个会话文件池。
- 保持当前对外返回的 `fileList` 结构、前端消费方式和 SSE 协议不变。

**Non-Goals:**

- 不恢复、兼容或重建已经废弃的 transcript/turn/block 体系。
- 不新增数据库表、字段或新的持久化账本。
- 不修改前端渲染逻辑，不新增“来源工具”展示能力。
- 不调整工具对模型返回的入参协议，也不重写整个工具框架接口。

## Decisions

### Decision 1: 引入独立的运行时工具产物绑定模型，而不是继续把来源字段塞进 `File`

**方案**

- 在 `ai-agent-station-study-domain` 内新增独立的运行时绑定模型，例如 `ToolArtifactBinding` / `ToolArtifactSource`。
- `ToolArtifactBinding` 作为当前请求内的事实记录，至少包含：
  - `sessionId`
  - `requestId`
  - `toolCallId`
  - `toolName`
  - `file`
- `AgentContext` 新增绑定集合与查询能力，例如：
  - 按 `toolCallId` 查询本次工具调用生成的文件
  - 查询全部非内部交付文件
  - 查询某次任务总结可见的绑定集合

**原因**

- `File` 当前既承担用户上传文件、会话恢复文件、工具产出文件三类职责，继续往里塞来源字段会让 DTO 语义进一步混乱。
- 独立绑定模型可以把“文件本体”和“来源关系”拆开，后续如果要加持久化或更细粒度归属，也更容易扩展。

**备选方案**

- 直接给 `File` 增加 `toolCallId/toolName`：拒绝。会污染通用文件模型，也无法清晰表达“同一个文件对象在不同视角下的来源关系”。
- 只在 `SummaryAgent` 里临时推断来源：拒绝。主智能体下一轮仍然无法稳定感知文件来源，问题没有闭环。

### Decision 2: 使用“可显式传递的来源快照 + 统一注册器”，兼容异步工具回调

**方案**

- 新增统一的 `ToolArtifactRegistrar`，负责登记文件产物与来源关系。
- `BaseAgent.executeTool(...)` 在执行单个 `ToolCall` 时先构造不可变的 `ToolArtifactSource` 快照，包含 `sessionId/requestId/toolCallId/toolName`。
- 同步工具可以通过当前执行上下文直接登记文件。
- 异步工具在发起异步请求前必须先捕获这份 `ToolArtifactSource`，并在回调线程里显式传回注册器。

**原因**

- `report_tool`、`deep_search` 等工具的文件登记发生在 OkHttp 回调线程中，只依赖同步线程上下文会丢失 `toolCallId`。
- 用不可变快照显式传递来源，比单纯依赖共享可变字段或线程本地变量更稳定，也更利于测试。

**备选方案**

- 只在 `AgentContext` 放一个“当前 toolCallId”字段：拒绝。多工具并行和异步回调下会互相覆盖。
- 改造 `BaseTool.execute` 签名，让所有工具都显式接收 `ToolCall` 元数据：本期不选。改动面更大，且会扩散到所有工具实现。

### Decision 3: 所有产文件工具通过统一注册入口写入兼容视图

**方案**

- 统一由注册器完成以下动作：
  - 写入显式 `ToolArtifactBinding`
  - 维护 `productFiles`
  - 维护 `taskProductFiles`
- `productFiles` / `taskProductFiles` 继续保留，但它们降级为兼容视图，不再是来源事实源。
- 下列工具必须切到统一注册入口：
  - `file_tool`
  - `deep_search`
  - `report_tool`
  - `code_interpreter`
  - `data_analysis`
  - `image_generation`
  - `ScriptRunnerTool`

**原因**

- 当前文件写入逻辑散落在多个工具里，内部文件与交付文件的判定也不一致，难以保证每条路径都带上来源信息。
- 统一入口后，来源绑定、会话文件池、任务文件池三者只维护一套写入规则。

**备选方案**

- 保持各工具各自 `add(productFiles/taskProductFiles)`，结束后再按文件名反查归属：拒绝。仍然依赖弱关联，且无法处理同名文件。

### Decision 4: 主智能体通过“每次工具响应附带该次产物摘要”感知文件来源

**方案**

- 在 `ReactImplAgent` / `ExecutorAgent` 处理单个工具结果时，根据当前 `toolCallId` 查询该次工具调用登记的文件。
- 如果存在绑定文件，则在写入 `Message.toolMessage(...)` 前，把该次工具产物摘要拼接进工具结果文本，例如附加统一格式的“关联文件”区块。
- 区块内容仅包含当前 `toolCallId` 对应的文件，不再混入全局 `productFiles`。

**原因**

- 当前主智能体的下一轮思考主要依赖工具结果消息；只修全局文件列表并不能让它知道“这个文件就是刚才哪个工具产出的”。
- 直接把每次工具调用的文件摘要附着在对应 `toolMessage` 上，可以在不改消息结构和 Spring AI 工具回放协议的前提下，建立稳定的一对一认知。

**备选方案**

- 修改 `Message.toolMessage` 结构新增文件数组：本期不选。会扩散到消息转换器和 LLM 工具回放链路。
- 只在系统提示词里继续注入全局文件列表：拒绝。来源仍然是模糊的。

### Decision 5: `SummaryAgent` 改为消费绑定感知元数据，避免纯文件名模糊匹配

**方案**

- `SummaryAgent` 构造文件上下文时，改为基于显式绑定集合输出来源感知元数据，至少包含：
  - `toolCallId`
  - `toolName`
  - `fileName`
  - `fileDesc`
  - `fileUrl`
- 总结阶段的输出协议改为优先选择带 `toolCallId` 的文件标识。
- 解析总结结果时，优先按 `toolCallId + fileName` 精确匹配；仅在唯一无歧义时允许退化为单文件直接命中。

**原因**

- 当前 `parseLlmResponse(...)` 只在全局 `productFiles` 里倒序按文件名包含关系模糊匹配，遇到同名文件或多个相近文件时不可靠。
- 改成来源感知匹配后，总结阶段既能知道文件来自哪个工具，也能稳定选中正确文件。

**备选方案**

- 保留现有 `$$$ + 文件名` 协议不变：拒绝。只能提升文件列表质量，不能从根本上解决归属歧义。

## Risks / Trade-offs

- [异步工具回调遗漏来源快照，导致仍然出现无归属文件] → 为 `report_tool`、`deep_search`、`code_interpreter` 等异步路径补专门测试，并禁止在回调里直接绕过注册器写文件列表。
- [兼容视图和绑定集合双写出现不一致] → 明确只有注册器可以更新 `productFiles` / `taskProductFiles`，工具侧直接追加文件视为违规实现并逐步清理。
- [工具结果文本附带文件摘要后，模型上下文变长] → 只附加当前 `toolCallId` 对应文件，且对描述和 URL 做长度控制，避免把全局文件重复注入。
- [总结阶段协议调整后，旧 prompt 或解析器残留造成不兼容] → 同步更新 `SummaryAgent` prompt 模板与解析逻辑，并增加多文件、同名文件、内部文件混合场景测试。
- [本期不做持久化，跨请求无法回溯文件来源] → 明确这次只解决当前运行链路；如未来需要历史可追踪，再基于独立绑定模型继续扩展持久化方案。

## Migration Plan

1. 在领域层新增运行时工具产物绑定模型与统一注册器。
2. 改造 `BaseAgent.executeTool(...)`、`ReactImplAgent`、`ExecutorAgent`，在单次工具执行生命周期内传递来源快照并回填工具结果文件摘要。
3. 逐个改造产文件工具，移除直接操作 `productFiles` / `taskProductFiles` 的散落写法，统一收敛到注册器。
4. 改造 `SummaryAgent` 的文件上下文构造、输出协议和解析逻辑，切换为来源感知匹配。
5. 补齐同步/异步工具、同名文件、多工具并行、内部文件过滤等回归测试。

**Rollback**

- 本次为纯代码内聚性改造，不涉及数据库结构。
- 如上线后发现兼容问题，可以回退到旧代码版本；运行时文件池模型仍保留，因此不会产生额外数据迁移负担。

## Open Questions

- 无。本次按“仅解决当前运行时主智能体和总结阶段识别来源”落地，不扩展到前端与历史持久化能力。
