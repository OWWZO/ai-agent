# Contract: Structured Tool Output Query And Replay

## Purpose

定义执行详情查询、history replay 和 direct tool call 检索如何消费新输出表。该契约只允许读取 8 张 `ai_agent_tool_output_*` 表，不允许回退到主账本 `output_json`。

## Reader Interface

```java
public interface ToolOutputReader {

    Optional<ToolStructuredOutput> readByInvocationId(String toolName, Long toolInvocationId);

    Optional<ToolOutputView> readDirect(String requestId, String toolCallId);
}
```

## Reader Output

```java
@Data
@Builder
public class ToolOutputView {

    private String toolName;
    private String requestId;
    private String sessionId;
    private String toolCallId;
    private Integer status;
    private String errorMsg;
    private ToolStructuredOutput structuredOutput;
}
```

## Read Pattern 1: Replay / Execution Detail

### Input

| Field | Required | Notes |
|--------|----------|-------|
| `toolName` | yes | 已知 rich tool 名称 |
| `toolInvocationId` | yes | 主账本工具调用主键 |

### Output

- 对应 rich tool 的 typed output 子类型

### Semantics

1. rich projector 只允许通过 `readByInvocationId` 取数据
2. `ToolInvocationView` 删除 `outputJson`，改为承载 `ToolStructuredOutput structuredOutput`
3. `ExecutionLedgerQueryServiceImpl` 在组装详情时：
   - 对 rich tool 调用 `readByInvocationId`
   - 对普通工具保持 `structuredOutput = null`

## Read Pattern 2: Direct Tool Call Lookup

### Input

| Field | Required | Notes |
|--------|----------|-------|
| `requestId` | yes | 请求唯一标识 |
| `toolCallId` | yes | 工具调用唯一标识 |

### Output

`ToolOutputView`

### Semantics

1. reader 在 8 张新输出表内做固定扇出查询
2. 期望最多命中 1 张表：
   - 0 命中：返回 empty
   - 1 命中：返回该工具的 `ToolOutputView`
   - 多命中：视为数据冲突，记录冲突日志并返回 empty，避免把歧义结果继续暴露给上层
3. direct lookup 不允许回查主账本，也不要求 `toolInvocationId` 存在

## Projector Rules

### Default Projector

- 不读取 `ToolOutputReader`
- 只按以下顺序构造 `tool_result`：
  1. `llmObservation`
  2. `errorMsg`
  3. `inputJson` 继续用于 `toolParam`

### Rich Projectors

- 不直接访问 `ToolOutputReader`
- 只消费 `ToolInvocationView.structuredOutput`、`llmObservation/errorMsg` 与 `ArtifactView`
- 通过 typed output 子类型组装事件，不再解析 JSON 字符串
- `DeepSearchToolInvocationProjector` 从 `DeepSearchToolOutput.stages` 恢复 `extend / search / report`

## File Merge Rules

1. reader 从 `file_refs_json` 反序列化出 `List<ToolFileRef>`
2. projector/detail 展示层可将其与 `ArtifactView` 的稳定链接合并
3. 若 artifact 为空，仍应保留输出表中的 `fileRefs`

## Failure Rules

1. 当 `structuredOutput` 业务字段最小化时，projector/detail 仍需结合 `status + errorMsg + llmObservation` 解释失败
2. 失败 rich tool 若 reader 返回 empty，视为数据缺失，应在测试中视为失败，不允许回退旧 JSON

## Query Service Expectations

### `ExecutionLedgerQueryService`

- `queryRunDetail(requestId)` 返回的每条 `ToolInvocationView` 对 rich tool 都可附带 `structuredOutput`
- `queryRecentToolInvocations(toolName, limit)` 不再暴露 `outputJson`
- replay/detail 所需的 `structuredOutput` 由 `ExecutionLedgerQueryServiceImpl` 在进入 projector 前完成 enrich
- 历史消费方从此只面向 `structuredOutput` 或 fallback 文本

## Non-Goals

- 不新增对外 HTTP 查询 API
- 不兼容旧 `output_json` 历史数据
- 不做跨表聚合报表或复杂搜索 DSL
