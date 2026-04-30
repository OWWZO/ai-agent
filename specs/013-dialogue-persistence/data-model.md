# Data Model: 对话执行持久化账本

## 1. DialogueRun

### Purpose

表示一次对话执行的聚合根，统一承接该次请求的身份、入口类型、总状态、聚合指标和最终摘要。

### Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `id` | `BIGINT` | yes | 数据库自增主键 |
| `runUid` | `VARCHAR(64)` | yes | 首期直接复用 `requestId` 作为稳定对外身份 |
| `requestId` | `VARCHAR(64)` | yes | 单次请求唯一标识 |
| `sessionId` | `VARCHAR(64)` | yes | 会话维度归属 |
| `entryAgent` | `VARCHAR(32)` | yes | 首期仅 `react`、`plan_solve` |
| `status` | `TINYINT` | yes | `0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT,4=STOPPED` |
| `queryText` | `MEDIUMTEXT` | no | 用户原始请求 |
| `finalSummaryText` | `MEDIUMTEXT` | no | 最终结果摘要或总结 |
| `llmCallCount` | `INT` | yes | 聚合指标 |
| `toolCallCount` | `INT` | yes | 聚合指标 |
| `artifactCount` | `INT` | yes | 聚合指标 |
| `promptTokensTotal` | `INT` | yes | LLM 输入总 token |
| `completionTokensTotal` | `INT` | yes | LLM 输出总 token |
| `totalTokensTotal` | `INT` | yes | LLM 总 token |
| `errorCode` | `VARCHAR(64)` | no | 失败/停止原因编码 |
| `errorMsg` | `TEXT` | no | 失败信息 |
| `startedAt` | `DATETIME(3)` | yes | run 开始时间 |
| `finishedAt` | `DATETIME(3)` | no | run 结束时间 |
| `durationMs` | `BIGINT` | no | 总耗时 |
| `createTime` / `updateTime` / `deleted` | meta | yes | 标准审计字段 |

### Validation & Uniqueness

- `runUid` 唯一
- `requestId` 唯一
- `entryAgent` 首期仅允许 `react` 或 `plan_solve`
- `status` 必须遵守下文状态流转

### State Transitions

`RUNNING -> SUCCESS | FAILED | TIMEOUT | STOPPED`

run 一旦进入终态，不允许再回到 `RUNNING`。

## 2. LlmInvocation

### Purpose

表示 run 内一次独立模型调用，保存调用顺序、类型、完整文本响应、工具数、状态、耗时与 token 指标。

### Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `id` | `BIGINT` | yes | 主键 |
| `runId` | `BIGINT` | yes | FK -> `DialogueRun.id` |
| `invocationSeq` | `INT` | yes | run 内从 1 递增 |
| `agentName` | `VARCHAR(32)` | yes | 如 `react`、`planning`、`executor`、`summary` |
| `stepNo` | `INT` | no | 执行步骤号 |
| `callKind` | `VARCHAR(16)` | yes | `ask` 或 `askTool` |
| `streaming` | `TINYINT(1)` | yes | 是否流式 |
| `modelName` | `VARCHAR(128)` | no | 当前模型名 |
| `responseText` | `MEDIUMTEXT` | no | 完整文本响应；`askTool` 为思考文本 |
| `toolCallCount` | `INT` | yes | 本次下发工具数 |
| `promptTokens` | `INT` | yes | 输入 token |
| `completionTokens` | `INT` | yes | 输出 token |
| `totalTokens` | `INT` | yes | 总 token |
| `finishReason` | `VARCHAR(32)` | no | `stop/tool_calls/length/error/...` |
| `status` | `TINYINT` | yes | `0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT` |
| `errorMsg` | `TEXT` | no | 错误信息 |
| `startedAt` / `finishedAt` / `durationMs` | timing | yes/no | 生命周期字段 |
| `createTime` / `updateTime` / `deleted` | meta | yes | 标准审计字段 |

### Validation & Uniqueness

- `(run_id, invocation_seq)` 唯一
- `callKind` 仅允许 `ask` 或 `askTool`
- `toolCallCount >= 0`

### State Transitions

`RUNNING -> SUCCESS | FAILED | TIMEOUT`

## 3. ToolInvocation

### Purpose

表示一次模型决策下发的真实工具执行，保留来源 LLM、原始分发顺序、输入、输出、状态和耗时。

### Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `id` | `BIGINT` | yes | 主键 |
| `runId` | `BIGINT` | yes | FK -> `DialogueRun.id` |
| `llmInvocationId` | `BIGINT` | yes | FK -> `LlmInvocation.id` |
| `toolCallId` | `VARCHAR(128)` | yes | 模型返回的 `toolCallId` |
| `dispatchIndex` | `INT` | yes | 同一次 `askTool()` 返回列表中的原始顺序 |
| `agentName` | `VARCHAR(32)` | yes | 如 `react`、`executor` |
| `stepNo` | `INT` | no | 规划/执行步号 |
| `toolName` | `VARCHAR(128)` | yes | 工具名 |
| `toolProvider` | `VARCHAR(64)` | no | `local/mcp/skill/...` |
| `inputJson` | `JSON` | yes | 工具入参 |
| `outputText` | `MEDIUMTEXT` | no | 字符串型输出 |
| `outputJson` | `JSON` | no | 结构化输出 |
| `status` | `TINYINT` | yes | `0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT` |
| `errorMsg` | `TEXT` | no | 错误信息 |
| `startedAt` / `finishedAt` / `durationMs` | timing | yes/no | 生命周期字段 |
| `createTime` / `updateTime` / `deleted` | meta | yes | 标准审计字段 |

### Validation & Uniqueness

- `(run_id, tool_call_id)` 唯一
- `(llm_invocation_id, dispatch_index)` 唯一
- `dispatchIndex` 从 1 递增，代表模型原始顺序，不代表真实执行时间

### State Transitions

`RUNNING -> SUCCESS | FAILED | TIMEOUT`

## 4. ArtifactRecord

### Purpose

记录输入文件和工具输出文件的稳定归属关系，明确文件来自哪个 run、哪个 tool invocation。

### Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `id` | `BIGINT` | yes | 主键 |
| `runId` | `BIGINT` | yes | FK -> `DialogueRun.id` |
| `toolInvocationId` | `BIGINT` | no | 输入文件为空；输出文件指向 `ToolInvocation.id` |
| `toolCallId` | `VARCHAR(128)` | no | 输入文件为空；输出文件保留来源 toolCallId |
| `artifactRole` | `VARCHAR(16)` | yes | `input` 或 `output` |
| `visibility` | `VARCHAR(16)` | yes | `visible` 或 `internal` |
| `sourceType` | `VARCHAR(32)` | yes | `user_upload` 或 `tool_output` |
| `sourceName` | `VARCHAR(128)` | no | `user_upload` 或工具名 |
| `fileName` | `VARCHAR(256)` | yes | 文件名 |
| `storageKey` | `VARCHAR(512)` | no | 稳定资源 key |
| `downloadUrl` | `VARCHAR(1024)` | no | 下载地址 |
| `previewUrl` | `VARCHAR(1024)` | no | 预览地址 |
| `mimeType` | `VARCHAR(128)` | no | MIME |
| `fileSize` | `BIGINT` | no | 文件大小 |
| `fileHash` | `VARCHAR(128)` | no | 哈希，可选 |
| `metadataJson` | `JSON` | no | 扩展元数据 |
| `createTime` / `updateTime` / `deleted` | meta | yes | 标准审计字段 |

### Validation & Uniqueness

- 输入文件必须有 `runId` 且 `toolInvocationId` 为空
- 输出文件必须同时带 `runId`、`toolInvocationId` 和 `toolCallId`
- `(run_id, tool_call_id, storage_key)` 唯一，用于避免重复登记稳定文件

## 5. AgentRunState

### Purpose

运行态上下文对象，不直接落库，用于把 run、LLM、tool 的账本身份在执行链路中持续传递。

### Fields

| Field | Type | Purpose |
|--------|------|---------|
| `runId` | `Long` | 当前 run 主键 |
| `runUid` | `String` | 当前 run 外部身份 |
| `currentAgentName` | `String` | 当前执行 agent 名称 |
| `currentStepNo` | `Integer` | 当前规划/执行步号 |
| `nextLlmInvocationSeq` | `Integer` | 下一个 LLM 顺序号 |
| `currentLlmInvocationId` | `Long` | 当前 LLM invocation 主键 |
| `toolInvocationIdByToolCallId` | `ConcurrentMap<String, Long>` | 工具预登记后的映射 |

## 6. Relationships

```text
DialogueRun 1 --- N LlmInvocation
DialogueRun 1 --- N ToolInvocation
DialogueRun 1 --- N ArtifactRecord
LlmInvocation 1 --- N ToolInvocation
ToolInvocation 1 --- N ArtifactRecord (output only)
```

## 7. Query Shapes

### Run Detail

- 输入：`requestId`
- 输出：1 条 `DialogueRun` + 其下全部 `LlmInvocation`、`ToolInvocation`、`ArtifactRecord`

### Recent Tool Calls

- 输入：`toolName`, `limit`
- 输出：最近 N 条 `ToolInvocation`，按 `create_time DESC`

### Session Runs

- 输入：`sessionId`
- 输出：按时间倒序的 `DialogueRun` 列表，可选择性附带产物摘要
