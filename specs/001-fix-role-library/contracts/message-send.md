# Contract: Message Send

## POST `/api/agent/message/send-stream`

SSE 发送接口，扩展 chat 角色参数。

### Request

```json
{
  "sessionId": "7aaf5fc0-bb81-4cd8-a7f7-9d2d8a7b9e11",
  "requestId": "req-001",
  "query": "请用这个角色和我打个招呼",
  "deepThink": 0,
  "outputStyle": "chat",
  "filesJson": null,
  "aiAgentId": "85374287"
}
```

### Request Rules

- `outputStyle = "chat"` 时：
  - 会话已存在：`aiAgentId` 可为空；若传入则必须与会话已绑定角色一致
  - 会话不存在：`aiAgentId` 可为空；为空时由后端回退默认角色并创建会话
- 非 chat 模式忽略 `aiAgentId`

## Success Stream

现有成功事件格式保持不变，仍然通过 `GptProcessResult` 逐步返回。

## Error Stream

当发生以下业务错误时，返回兼容 `GptProcessResult` 的终态错误包，而不是让下游 Fix 执行继续：

- `noAvailableChatRole`
- `roleUnavailable`
- `roleSwitchRejected`

### Example

```json
{
  "status": "roleUnavailable",
  "finished": true,
  "packageType": "result",
  "reqId": "req-001",
  "traceId": "req-001",
  "response": "",
  "responseAll": "",
  "errorMsg": "当前角色已不可继续使用，请新建对话后重新选择角色"
}
```

### Semantics

- `roleUnavailable`: 会话绑定角色已失效，历史可读但不允许继续发送
- `roleSwitchRejected`: 用户试图在已有会话中使用不同角色发送消息
- `noAvailableChatRole`: 当前系统中没有任何可用于 chat 的 Fix 角色

### Persistence Expectation

- 若错误发生在真正调用 Fix 执行前，消息应被标记为错误态，避免前端出现“发送了但没有记录”的错觉。
- 不允许因为角色错误去执行默认角色兜底覆盖已有会话绑定。
