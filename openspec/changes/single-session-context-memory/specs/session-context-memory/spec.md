## ADDED Requirements

### Requirement: Session history dialogue SHALL be rebuilt from execution ledger facts
当新请求进入与 `sessionId` 绑定的 Agent 执行链路时，系统必须从同一 `sessionId` 下的历史执行账本中重建 `historyDialogue`，而不是只依赖当前请求输入。

#### Scenario: Build history dialogue from previous runs in the same session
- **WHEN** 一个带有 `sessionId` 的新请求进入 React 或 PlanSolve 执行策略，且该 `sessionId` 下已经存在历史 run
- **THEN** 系统必须查询该 `sessionId` 下的历史 `dialogue_run`、`llm_invocation`、`tool_invocation` 与 `artifact` 账本
- **THEN** 系统必须把这些历史事实重建为 `request.historyDialogue`

#### Scenario: Skip history rebuilding when session context is absent
- **WHEN** 请求没有有效的 `sessionId`
- **THEN** 系统必须返回空的 `historyDialogue`
- **THEN** 系统不得发起无意义的历史账本查询

### Requirement: History memory SHALL be organized by LLM invocation cycles
历史记忆必须按一次 `llmInvocation` 对应一次 ReAct 循环进行组织，每个循环下保留完整 thought、工具调用、工具观察结果和关联文件元信息。

#### Scenario: Group one LLM invocation into one ordered ReAct cycle
- **WHEN** 某个历史 run 下存在多次 `llm_invocation`
- **THEN** 系统必须按 `invocation_seq` 升序重建多个 ReAct cycle
- **THEN** 每个 cycle 必须包含该次 `response_text` 以及归属于该 `llmInvocationId` 的全部工具调用

#### Scenario: Preserve tool outputs as file metadata only
- **WHEN** 某个工具调用关联了输出 artifact
- **THEN** 系统必须保留文件名、存储键、下载地址、预览地址、类型和大小等元信息
- **THEN** 系统不得读取或拼接 artifact 文件正文

#### Scenario: Emit explicit placeholders when actions or files are absent
- **WHEN** 某个 cycle 没有工具调用，或某个工具调用没有文件输出
- **THEN** 格式化后的 `historyDialogue` 必须输出明确的 `none` 占位
- **THEN** 系统不得省略该结构导致历史语义断裂

### Requirement: Current request run SHALL be excluded from session memory injection
系统必须在构建单会话历史记忆时排除当前正在执行请求对应的 run，避免未完成账本污染当前推理。

#### Scenario: Exclude the current request by request identifier
- **WHEN** 当前请求携带 `requestId`，且同一 `sessionId` 下已存在与其对应的 run
- **THEN** 系统必须在历史查询结果中排除该 `requestId` 对应 run
- **THEN** `historyDialogue` 只允许包含当前请求之前已经完成或已存在的历史事实

### Requirement: React and PlanSolve strategies SHALL inject session history before execution
React 与 PlanSolve 两条执行策略必须在进入后续推理节点前完成单会话历史记忆注入，并继续复用现有 `historyDialogue` 注入链路。

#### Scenario: React strategy injects rebuilt session history
- **WHEN** 请求进入 `ReactAgentExecuteStrategy`
- **THEN** 策略必须先调用单会话记忆服务构建 `historyDialogue`
- **THEN** 策略必须在调用后续执行逻辑前把结果写回 `request.historyDialogue`

#### Scenario: PlanSolve strategy injects rebuilt session history
- **WHEN** 请求进入 `PlanSolveAgentExecuteStrategy`
- **THEN** 策略必须先调用单会话记忆服务构建 `historyDialogue`
- **THEN** 策略必须在原有执行流程开始前把结果写回 `request.historyDialogue`
