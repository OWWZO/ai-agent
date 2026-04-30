# Contract: Execution Ledger Recorder

## Purpose

定义执行账本在领域层的统一写入契约，屏蔽 MyBatis、表结构和主键细节，让 ReAct/PlanSolve 执行链路只面向稳定的领域接口编排生命周期事件。

## Interface

```java
public interface AgentExecutionRecorder {

    Long createRun(DialogueRunStartRecord record);

    void finishRun(DialogueRunFinishRecord record);

    Long createLlmInvocation(LlmInvocationStartRecord record);

    void finishLlmInvocation(LlmInvocationFinishRecord record);

    Map<String, Long> createToolInvocations(ToolInvocationBatchStartRecord record);

    void finishToolInvocation(ToolInvocationFinishRecord record);

    void recordArtifacts(List<ArtifactRecordCommand> records);
}
```

## Lifecycle Rules

1. `createRun`
   - 在 ReAct `RootNode` 或 PlanSolve `Step1SopRecallAndPrepareNode` 创建 `AgentContext` 时调用
   - 成功后把 `runId/runUid` 写入 `AgentRunState`
2. `createLlmInvocation`
   - 在 `LLM.ask()` / `LLM.askTool()` 真正调用模型前调用
   - `AgentRunState.nextLlmInvocationSeq` 必须先分配顺序号
3. `finishLlmInvocation`
   - 在模型返回文本或异常时调用
   - `askTool()` 必须写 `toolCallCount`
4. `createToolInvocations`
   - 仅在 `BaseAgent.executeTools()` 主线程调用一次
   - 输入是同一次 `askTool()` 返回的完整 `toolCalls` 列表，必须按原始顺序传入
   - 返回 `toolCallId -> toolInvocationId` 映射，供工作线程完成回写
5. `finishToolInvocation`
   - 每个工具线程独立调用
   - 即便工具失败，也必须写终态和错误信息
6. `recordArtifacts`
   - 输入文件在 run 创建后立即写入
   - 输出文件在工具完成后按 `toolCallId` 收口写入

## Fail-Open Rules

- 任一 recorder 方法抛错时，调用方必须捕获并记录日志/指标，不能直接中断用户主流程
- 领域层需要统一的 recorder wrapper 或 support，避免每个调用点各自拼装 try/catch

## Record Shapes

### DialogueRunStartRecord

| Field | Required | Notes |
|--------|----------|-------|
| `runUid` | yes | 首期等于 `requestId` |
| `requestId` | yes | 单次请求 ID |
| `sessionId` | yes | 会话 ID |
| `entryAgent` | yes | `react` / `plan_solve` |
| `queryText` | yes | 用户查询 |
| `startedAt` | yes | 开始时间 |

### LlmInvocationStartRecord

| Field | Required | Notes |
|--------|----------|-------|
| `runId` | yes | 父 run |
| `invocationSeq` | yes | run 内顺序 |
| `agentName` | yes | 当前 agent |
| `stepNo` | no | 当前步号 |
| `callKind` | yes | `ask` / `askTool` |
| `streaming` | yes | 是否流式 |
| `modelName` | no | 当前模型 |
| `startedAt` | yes | 开始时间 |

### ToolInvocationBatchStartRecord

| Field | Required | Notes |
|--------|----------|-------|
| `runId` | yes | 父 run |
| `llmInvocationId` | yes | 来源 LLM |
| `agentName` | yes | 当前 agent |
| `stepNo` | no | 当前步号 |
| `items` | yes | 按原始 tool call 顺序排列的列表 |

每个 `items[]` 元素至少包含：

- `toolCallId`
- `dispatchIndex`
- `toolName`
- `toolProvider`
- `inputJson`
- `startedAt`

## Observability Expectations

- `create*` / `finish*` / `recordArtifacts` 都要记录成功/失败计数
- `finishRun`、`finishLlmInvocation`、`finishToolInvocation` 要记录耗时分布
- log 至少包含 `requestId`、`runId`、`toolCallId` 等关键身份字段
- 当前实现通过 `AgentExecutionRecorderImpl` 内存累计 `successCounters`、`failureCounters`、`durationTotals`
- fail-open 错误日志额外输出 `successRate`，便于快速判断某个账本写入场景是否持续退化
