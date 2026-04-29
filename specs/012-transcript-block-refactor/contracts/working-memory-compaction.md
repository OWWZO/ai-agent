# Contract: Working Memory & Compaction

## Input Sources

### Latest Snapshot

- `ai_agent_session_memory` 中同一 `session_id` 的最新有效版本

### Recent Turns

- `ai_agent_turn` 中 `status=COMPLETED` 且 `sort_order > boundary_sort_order` 的 turns

### Transcript Facts

- 上述 turns 对应的 `ai_agent_transcript_block`

## Output Model

`WorkingContextWindow`

### Required Outputs

| Output | Meaning |
|--------|---------|
| `summaryText` | 最新 snapshot 摘要 |
| `recentTurns` | 边界之后仍直接参与上下文的 turns |
| `formattedHistoryDialogue` | 注入 `{{history_dialogue}}` 的格式化文本 |
| `messages` | 结构化上下文消息 |
| `sessionFiles` | 当前可复用产物引用 |
| `estimatedTokens` | 当前上下文估算量 |

## Request-Preflight Compaction Rules

1. 每次新请求开始前都执行压缩判定。
2. 当 `estimatedTokens > compaction-threshold-tokens` 时，先尝试压缩，再执行当前请求。
3. 压缩只处理最近窗口之前的已完成 turns。
4. 最近窗口至少保留 `recent-window-min-messages`，同时受 `recent-window-turns` 与 `recent-window-max-tokens` 约束。

## Snapshot Rules

1. 压缩成功时新增一条新的 snapshot 版本。
2. 运行时只读取最新有效版本。
3. snapshot 通过 `boundary_sort_order` 指明已覆盖的最后一个 turn。
4. snapshot 保存压缩摘要和归档后的 `artifact_refs`。

## Failure Rules

1. 压缩失败时，直接跳过压缩并继续当前请求。
2. 压缩失败不得写入半成品 snapshot。
3. 压缩失败不得推进 `boundary_sort_order`。
4. 不再使用“连续失败后拒绝请求”或“熔断短路拒绝”的主链路策略。

## Block-to-Context Rules

| Block Type | Context Role |
|------------|--------------|
| `USER_INPUT` | `user` |
| `ASSISTANT_THOUGHT` | `assistant_thought` |
| `TOOL_USE` | `assistant/tool_use` |
| `TOOL_RESULT` | `tool` |
| `ARTIFACT_REFERENCE` | `assistant/artifact_reference` |
| `ASSISTANT_ANSWER` | `assistant` |

## Final Implementation Notes

1. `formattedHistoryDialogue` 与 `messages` 必须来自同一份 turn/block 数据，不允许再走两套不同恢复链。
2. 旧的 `SessionWorkingMemoryAssembler`、`SessionMemorySummaryBuilder`、`SessionMemoryPromptFormatter` 等兼容链应被新的 builder/formatter 替换。
