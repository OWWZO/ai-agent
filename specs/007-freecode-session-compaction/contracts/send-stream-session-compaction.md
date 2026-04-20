# Contract: Send-Stream Request-Entry Compaction

## Scope

本契约描述 `/api/agent/message/send-stream` 在 `REACT / PLAN_SOLVE` 会话中的请求前压缩行为。外部 HTTP 请求结构不变，但内部处理时序和拒绝分支会演进。

## Endpoint

```text
POST /api/agent/message/send-stream
```

## Unchanged Request Fields

| Field | Notes |
|------|-------|
| `sessionId` | 会话标识 |
| `requestId` | 请求标识 |
| `query` | 用户问题 |
| `deepThink` | 模式相关参数 |
| `outputStyle` | 输出样式 |

## Request-Entry Sequence

### `CHAT` Mode

- 保持现有滑动窗口策略
- 不触发 session memory compaction preflight

### `REACT / PLAN_SOLVE` Mode

1. 查询会话并执行现有 `mode_conflict / session_busy` 守卫
2. 调用 session memory preflight：
   - 加载最新 snapshot version
   - 重建候选 working memory
   - 判断是否需要 compaction
   - 必要时生成新的 structured session memory 并插入新 snapshot
3. **只有 preflight 成功或允许降级继续时，才允许插入占位消息**
4. 基于最终 working memory 构建 `AgentRequest`
5. 发起原有 SSE 主执行链

## Decision Outcomes

| Decision | Behavior |
|----------|----------|
| `BYPASS` | 不需要 compaction，直接继续 |
| `COMPACTED` | 已生成新 snapshot，基于新 snapshot + 最近窗口继续 |
| `DEGRADED_CONTINUE` | compaction 失败，但原始上下文仍在硬上限内，允许继续 |
| `REJECTED` | compaction 失败且上下文仍超限或已损坏，请求被拒绝 |
| `SKIPPED_CIRCUIT_OPEN` | 因连续失败已暂停主动 compaction；若原始上下文仍可继续则降级，否则拒绝 |

## Rejection Contract

### Guard Phase

- 拒绝必须发生在占位消息插入之前
- 拒绝时不得新增 `ai_agent_message` 占位记录
- 拒绝时不得新增新的 `ai_agent_session_memory` 版本

### Error Shape

建议沿用现有 guard result 风格，新增一个上下文超限类错误码，例如：

```json
{
  "code": "context_limit_exceeded",
  "message": "当前会话上下文过长且压缩失败，请稍后重试或新建会话"
}
```

> 实现阶段可根据现有错误封装做等价映射，但必须保留“无占位消息写入”的语义。

## Observability

请求前压缩至少记录：

- `sessionId`
- `requestId`
- compaction decision type
- 原始 working memory token 估算
- compaction 后 token 估算
- snapshot version id（若生成）
- failure count / circuit 状态（若失败）

## Compatibility

- 对外接口参数和 SSE 成功流格式保持不变
- 仅新增请求前拒绝分支
- `CHAT` 模式完全不受影响
