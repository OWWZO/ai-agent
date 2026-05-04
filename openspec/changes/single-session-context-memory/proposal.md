## Why

当前同一 `sessionId` 下的新请求没有稳定复用执行账本里的完整历史。历史 run 虽然已经落在 `ai_agent_dialogue_run`、`ai_agent_llm_invocation`、`ai_agent_tool_invocation`、`ai_agent_artifact` 中，但后续推理链路没有把这些事实按一次次 ReAct 循环重建为可注入的 `historyDialogue`，导致多轮连续会话缺少同会话上下文记忆。

现在需要补齐这条链路，因为执行账本、工具产物和历史回放基础设施已经具备，继续让每次请求只依赖当前输入，会让 ReAct / PlanSolve 在同一会话内重复丢失上下文，也会让后续上下文压缩优化缺少稳定基线。

## What Changes

- 新增单会话上下文记忆能力，按 `sessionId` 查询历史 run，并排除当前 `requestId` 对应的正在执行 run。
- 以 `llmInvocation.id` 作为一次 ReAct 循环锚点，把 `responseText`、工具调用、工具观察结果和关联文件元信息组装成线性 `historyDialogue` 文本。
- 新增会话记忆中间模型与组装服务，统一封装 run、cycle、tool、artifact 四层结构，避免在执行策略里堆叠查询和格式化逻辑。
- 扩展执行账本 DAO / Mapper，支持按 `runIds`、`llmInvocationIds`、`toolInvocationIds` 批量查询历史事实。
- 在 `ReactAgentExecuteStrategy` 与 `PlanSolveAgentExecuteStrategy` 执行前注入单会话历史记忆，保持现有 `historyDialogue` 注入链路不变。
- 明确本期不做上下文压缩、不裁剪思考内容、不读取 artifact 文件正文，只保留文件元信息。

## Capabilities

### New Capabilities
- `session-context-memory`: 同一 `sessionId` 下的新请求必须能从执行账本重建历史 ReAct 循环，并在进入 ReAct / PlanSolve 推理前注入 `historyDialogue`。

### Modified Capabilities
- 无

## Impact

- 影响 `ai-agent-station-study-domain` 中的执行账本 DAO、会话记忆模型、会话记忆服务以及 ReAct / PlanSolve 执行策略。
- 影响 `ai-agent-station-study-app` 中的 MyBatis Mapper XML 与相关测试夹具、单元测试、集成测试。
- 不新增数据库表，不改前端协议，不改 `BaseAgent.injectHistoryDialogue(...)` 的既有使用方式。
