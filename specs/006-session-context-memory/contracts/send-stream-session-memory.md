# Contract: `/api/agent/message/send-stream` 会话记忆与守卫语义

## Scope

本契约定义 `POST /api/agent/message/send-stream` 在引入会话记忆后，对同会话续聊、模式冲突、并发冲突和记忆更新的行为约束。

## Request

沿用现有 `MessageSendReqVO`：

```json
{
  "sessionId": "sess-001",
  "requestId": "req-001",
  "query": "继续补充上一个结论",
  "deepThink": 0,
  "outputStyle": "html",
  "filesJson": "[]",
  "aiAgentId": null
}
```

## Guard Order

服务端在真正创建占位消息和发起下游 Agent 请求前，必须按顺序执行以下守卫：

| Step | Rule | If Failed |
|------|------|-----------|
| 1 | 按 `sessionId` 查询会话；不存在则创建新会话 | 继续 |
| 2 | 校验请求模式与 `ai_agent_conversation.agent_type` 一致 | 返回模式冲突终态，要求新建会话 |
| 3 | 校验当前会话不存在 `STREAMING` 消息 | 返回会话忙终态，要求等待或先停止 |
| 4 | 通过守卫后才允许插入占位消息并重建工作记忆 | 进入正常执行 |

## Transport Compatibility

为保持前端 SSE 调用方式不变，本次规划约定冲突场景继续复用现有 SSE 结果结构返回“立即结束的错误结果”，而不是新增第二套 JSON 接口。

**Guard Failure Requirements**

- 不创建新的 `AgentMessage` 占位行
- 不更新 `ai_agent_session_memory`
- 立即输出终态错误结果并关闭 SSE

## Error Result Shape

建议继续复用现有 `GptProcessResult` 终态结构，差异仅体现在 `status / errorMsg`：

```json
{
  "finished": true,
  "status": "session_busy",
  "errorMsg": "当前会话仍在执行中，请等待完成或先停止当前轮次"
}
```

或：

```json
{
  "finished": true,
  "status": "mode_conflict",
  "errorMsg": "当前会话已绑定 REACT/PLAN_SOLVE，请新建会话后再切换模式"
}
```

## Success Path Memory Semantics

当请求通过守卫并正常结束时：

| Turn Status | 是否写入历史账本 | 是否进入工作记忆 | 是否更新摘要快照 |
|------------|------------------|------------------|------------------|
| `COMPLETED` | 是 | 是 | 视是否超过阈值决定是否更新 |
| `ERROR` | 是 | 否 | 否 |
| `FORCE_STOPPED` | 是 | 否 | 否 |

## Implemented Flow

当前实现采用以下顺序：

1. `AgentStreamPersistServiceImpl` 先做模式守卫与并发守卫
2. 守卫通过后插入占位消息
3. `AgentSessionMemoryServiceImpl.rebuildWorkingMemory` 从 MySQL 重建工作记忆
4. `historyDialogue`、`messages`、`sessionFiles` 注入 `AgentRequest`
5. 流结束后仅 `completed` 状态调用 `refreshSessionMemory`
6. `partial/error` 仅保留历史账本，不刷新摘要快照

## Notes

- `sessionId` 必须是会话级 ID，不能退回使用 `requestId`
- 同一会话模式锁定只对 `REACT / PLAN_SOLVE` 生效，`CHAT` 不在本次范围内
