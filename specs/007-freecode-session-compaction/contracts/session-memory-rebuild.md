# Contract: Session Memory Preflight & Rebuild

## Scope

本契约定义请求前 session memory preflight 的输入、输出和内部行为。它连接 send-stream 入口、snapshot versioning 和最终 working memory 装配。

## Primary Service Contract

建议把现有 `IAgentSessionMemoryService` 演进为：

```text
prepareForRequest(conversation) -> SessionMemoryPreparationResult
rebuildWorkingMemory(conversation) -> SessionWorkingMemory
```

其中 `prepareForRequest(...)` 负责请求前 compaction decision，`rebuildWorkingMemory(...)` 负责输出最终工作记忆。

## Input

| Input | Source |
|-------|--------|
| `conversation` | `AgentConversation` |
| latest snapshot | `ai_agent_session_memory` 最新 version |
| completed turns | `ai_agent_message` |
| final events | `ai_agent_message_event` |
| config | `ReactorConfig.session-memory.*` |
| guardrail state | session-level in-memory failure state |

## Output: `SessionMemoryPreparationResult`

| Field | Description |
|------|-------------|
| `decision` | `BYPASS / COMPACTED / DEGRADED_CONTINUE / REJECTED / SKIPPED_CIRCUIT_OPEN` |
| `workingMemory` | 最终可注入主请求的 working memory |
| `snapshotVersionId` | 若本次 compaction 成功，返回新版本 ID |
| `estimatedTokens` | 请求前 working memory 体量 |
| `postCompactionTokens` | 若发生 compaction，返回压缩后估算 |
| `failureCount` | 当前 session 连续失败次数 |
| `rejectReason` | 若拒绝，请给出原因 |

## Rebuild Rules

1. 先读取最新 snapshot version
2. 再读取边界之后的 completed turns 与 full final events
3. 构建候选 working memory 并估算 token
4. 若低于主动阈值：
   - 直接返回 `BYPASS`
5. 若高于主动阈值：
   - 调用 structured memory generator 生成新的 session memory document
   - 追加写入新的 snapshot version
   - 基于新 snapshot + preserved recent window 重建 working memory
   - 返回 `COMPACTED`
6. 若 compaction 失败：
   - 若低于硬上限，返回 `DEGRADED_CONTINUE`
   - 若高于硬上限或上下文无效，返回 `REJECTED`

## Structured Memory Generator Contract

### Input Material

- 上一版 `summary_text`
- 新增 completed turns 的 rich transcript
- 稳定 `artifact_refs`
- 关键边界信息（如最新 boundary）

### Output Material

- free-code 风格 markdown memory
- 可选的 `facts_json` compatibility projection
- 不得伪造 `artifact_refs_json`

## Preserved Recent Window Contract

### Required Guarantees

- 以 token 预算为主裁剪
- 至少保留最小真实消息窗口
- `tool_use / tool_result` 不拆断
- 同一逻辑响应的关键片段不拆断
- 超长正文只保留关键结果与稳定引用

### Source Priority

1. 最新 snapshot 边界之后的 completed turns
2. 对应 full final events
3. 已归档稳定引用

## Failure Circuit Contract

- 每个 `sessionId` 独立维护连续失败计数
- compaction 成功后清零
- 达到上限后短期内跳过主动 compaction
- circuit open 只影响主动 compaction，不影响模式守卫和会话账本读取

## Backward Compatibility

- 老 snapshot 文本仍可直接注入为 `summary_text`
- 无 events 的旧 turn 退化为 `query + response`
- `facts_json` 缺失不阻塞 rebuild
