# Contract: Execution Ledger Internal Query

## Purpose

定义本期内部排障与治理使用的最小查询契约。该契约只服务于领域服务、测试和 SQL 验证，不新增正式 HTTP API。

## Query Service

```java
public interface ExecutionLedgerQueryService {

    ExecutionRunDetail queryRunDetail(String requestId);

    List<ToolInvocationView> queryRecentToolInvocations(String toolName, int limit);

    List<DialogueRunView> queryRecentSessionRuns(String sessionId, int limit);
}
```

## Query 1: Run Detail

### Input

| Field | Required | Notes |
|--------|----------|-------|
| `requestId` | yes | 单次执行唯一标识 |

### Output

`ExecutionRunDetail`

| Field | Type | Notes |
|--------|------|-------|
| `run` | `DialogueRunView` | run 总账 |
| `llmInvocations` | `List<LlmInvocationView>` | 按 `invocationSeq ASC` |
| `toolInvocations` | `List<ToolInvocationView>` | 按 `llmInvocationId ASC, dispatchIndex ASC` |
| `artifacts` | `List<ArtifactView>` | 按 `createTime ASC` |

### Semantics

- 找不到 `requestId` 时返回空结果或显式 not-found，由调用方决定
- 该查询必须能支撑“单次执行完整链路排查”

## Query 2: Recent Tool Invocations

### Input

| Field | Required | Notes |
|--------|----------|-------|
| `toolName` | yes | 工具名 |
| `limit` | yes | 最近 N 条，默认不超过 100 |

### Output

`List<ToolInvocationView>`

| Field | Notes |
|--------|-------|
| `requestId` / `sessionId` | 便于回溯上下文 |
| `toolCallId` | 稳定工具调用身份 |
| `dispatchIndex` | 模型原始顺序 |
| `status` / `durationMs` | 稳定性分析关键指标 |
| `inputJson` / `outputText` / `outputJson` | 原始排障正文 |
| `artifactCount` | 可选聚合字段 |

### Semantics

- 排序规则：`createTime DESC`
- 该查询必须能支撑“某个工具最近都收到了什么参数、表现如何”

## Query 3: Recent Session Runs

### Input

| Field | Required | Notes |
|--------|----------|-------|
| `sessionId` | yes | 会话 ID |
| `limit` | yes | 最近 N 条 |

### Output

`List<DialogueRunView>`

| Field | Notes |
|--------|-------|
| `runUid` / `requestId` | 稳定身份 |
| `entryAgent` | `react` / `plan_solve` |
| `status` | 终态 |
| `queryText` | 原始问题 |
| `finalSummaryText` | 结果摘要 |
| `llmCallCount` / `toolCallCount` / `artifactCount` | 执行概览 |
| `startedAt` / `durationMs` | 时间线 |
| `artifactSummaries` | 最近产物摘要，按产物创建顺序挂载 |

### Semantics

- 排序规则：`createTime DESC`
- `limit <= 0` 时按 20 处理，最大不超过 100
- 该查询必须能支撑“这个会话最近跑了哪些执行”

## DAO Expectations

### `DialogueRunLedgerDao`

- `insertRun`
- `updateRunFinish`
- `queryByRequestId`
- `queryRecentBySessionId`

### `LlmInvocationLedgerDao`

- `insertLlmInvocation`
- `updateLlmInvocationFinish`
- `queryByRunId`

### `ToolInvocationLedgerDao`

- `insertToolInvocation`
- `updateToolInvocationFinish`
- `queryByRunId`
- `queryRecentByToolName`

### `ArtifactLedgerDao`

- `batchInsertArtifacts`
- `queryByRunId`

## Non-Goals

- 不提供面向普通用户或后台产品化页面的接口
- 不做分页筛选器、复杂搜索 DSL 或跨 run 聚合报表
- 不在本期提供数据清理、导出或归档接口
