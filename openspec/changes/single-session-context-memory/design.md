## Context

当前仓库已经把一次 Agent 执行的核心事实落入执行账本：`ai_agent_dialogue_run` 记录 run 级信息，`ai_agent_llm_invocation` 记录每轮大模型调用，`ai_agent_tool_invocation` 记录工具动作，`ai_agent_artifact` 记录输入输出文件。但后续请求进入执行策略前，还没有一层稳定的 request enrich 去按同一 `sessionId` 重建历史上下文。

现有约束已经比较明确：

- 记忆源只允许使用执行账本，不回接旧 `ai_agent_message*` 历史表。
- 记忆组织单位必须是“一次 LLM 调用对应的一次 ReAct 循环”，而不是简单按 run 拼接。
- 本期只补齐同会话历史记忆注入，不做 token 压缩、摘要裁剪、文件正文读取或前端展示改造。
- 现有 `BaseAgent.injectHistoryDialogue(...)` 和后续 `AgentContext` 注入链路保持不变，新增能力应尽量局限在执行前 enrichment。

## Goals / Non-Goals

**Goals:**

- 为同一 `sessionId` 的新请求重建可复用的 `historyDialogue`。
- 把历史记忆的查询、组装、格式化收口到独立服务中，保持执行策略职责清晰。
- 按 `llmInvocation` 维度稳定组装 `Thought -> Tool Calls -> Files` 的历史循环文本。
- 只读取 artifact 元信息，并明确排除当前正在执行请求对应的 run。
- 在 React 与 PlanSolve 两条主执行链路上统一接入这份历史记忆。

**Non-Goals:**

- 不为 AutoAgent 或 workflow 链路新增 `historyDialogue` 注入。
- 不做上下文压缩、摘要提炼、思考裁剪或 token 控制。
- 不读取 artifact 文件正文，不新增文件内容解析逻辑。
- 不新增数据库表、外部依赖或新的历史存储模型。

## Decisions

### 1. 在执行策略入口前新增 `SessionContextMemoryService`

新增独立的 `SessionContextMemoryService`，由它负责：

- 按 `sessionId` 查询历史 run
- 过滤当前 `requestId`
- 批量查询 llm / tool / artifact 账本
- 组装中间记忆模型
- 格式化为最终 `historyDialogue`

这样可以把“历史查询 + 结构装配 + 文本格式化”从 `ReactAgentExecuteStrategy` / `PlanSolveAgentExecuteStrategy` 中剥离出来，避免执行策略直接演变成大杂烩。

备选方案是把查询和拼接逻辑直接写进两个策略类。拒绝原因是两条链路会重复实现同样逻辑，后续一旦需要调整格式或过滤规则，会同时污染多个入口。

### 2. 使用显式中间模型承接 run / cycle / tool / file 四层结构

新增以下模型：

- `SessionHistoryMemory`
- `RunHistoryMemory`
- `ReactCycleMemory`
- `ToolCallMemory`
- `FileArtifactMemory`

这样做的原因有两个：

- 执行账本的行记录需要先恢复为语义化层级结构，再安全格式化为 prompt 文本。
- 有了中间模型后，后续若单独做压缩、裁剪或不同格式输出，可以复用同一份聚合结果，而不需要重新查询数据库。

备选方案是查询后直接边遍历边拼字符串。拒绝原因是逻辑难测、顺序约束隐蔽、后续扩展成本高。

### 3. 以 `llmInvocation.id` 作为 ReAct 循环锚点

每次 `ai_agent_llm_invocation` 代表一次完整的模型思考输出，因此本期把它定义为历史记忆的最小循环单位。一个循环下再挂载对应的多个 `toolInvocation` 与输出文件。

这样可以保证：

- 思考内容与工具动作是一一归属的
- 同一 run 内多轮调用顺序稳定
- PlanSolve 与 ReAct 可以共享同一组装语义

备选方案是按 run 汇总所有工具调用再整体拼接。拒绝原因是会打散 thought 与 action 的对应关系，历史可读性和复用价值都明显下降。

### 4. 只保留 artifact 元信息，不读取文件正文

本期文件信息只读取：

- `fileName`
- `storageKey`
- `downloadUrl`
- `previewUrl`
- `mimeType`
- `fileSize`

原因是当前目标是恢复“做过什么、产出了什么”，而不是把文件内容本身塞进上下文。读取正文会显著增加复杂度和 token 成本，也会把一次简单的历史增强变成文件检索问题。

备选方案是对文本文件直接读取正文并拼入上下文。拒绝原因是超出本期范围，且会引入编码、大小限制和安全边界问题。

### 5. 当前请求通过 `requestId` 对应的 run 进行排除

记忆服务按 `DialogueRun.requestId` 过滤当前正在执行的 run，避免把本轮未完成账本再次注入本轮推理。当前实现前提是首期里 `runUid` 复用该请求标识，这与现有执行账本约束一致。

备选方案是完全依赖“最新 run 不参与注入”的时间顺序过滤。拒绝原因是并发或补写场景下不稳定，无法精确识别当前请求。

### 6. 仅在 React / PlanSolve 接入，不覆盖 Auto / workflow

本期只改：

- `ReactAgentExecuteStrategy`
- `PlanSolveAgentExecuteStrategy`

不改 AutoAgent 和 workflow，原因是：

- AutoAgent 当前已有独立 chat memory 机制，且 `execute(AgentRequest, SseEmitter)` 并不是本期主路径。
- workflow 通过 Spring AI chat memory 路径工作，不消费 `historyDialogue`。

这样可以把变更控制在明确受益、且已有注入点的主执行链路里。

## Risks / Trade-offs

- [历史账本查询跨 run / llm / tool / artifact 多层关联，顺序或映射处理错误会导致上下文错乱] → 通过中间模型、稳定排序规则和定向单元测试固定装配顺序。
- [完整保留 `responseText`、`inputJson`、`llmObservation` 会让上下文快速膨胀] → 本期接受这项成本，后续若要压缩，基于本次建立的中间模型单独起变更处理。
- [执行前多一次账本查询会增加少量延迟] → 采用批量 DAO 查询，避免循环逐条读取，把开销控制在可接受范围。
- [当前 run 过滤依赖 `requestId` 与 run 标识一致] → 通过集成测试固定该约束，若后续 run 标识策略变化，再独立调整过滤逻辑。

## Migration Plan

1. 为 llm / tool / artifact 三类 DAO 与 Mapper 增加批量查询能力，并同步更新测试夹具。
2. 新增会话记忆中间模型与 `SessionContextMemoryService` 实现，完成历史事实到 `historyDialogue` 的组装。
3. 在 React 与 PlanSolve 执行策略进入主逻辑前调用记忆服务并回填 `request.historyDialogue`。
4. 补齐单元测试和集成测试，覆盖排序、过滤、格式化与入口注入行为。

回滚策略：

- 若本次能力需要回滚，只需回退策略入口接入和会话记忆服务实现；本期未引入新表结构，不涉及数据迁移回滚。

## Open Questions

- 当前没有阻塞性开放问题。后续若要控制上下文长度，需要单独起变更定义压缩与裁剪规则，而不是在本期里临时混入。
