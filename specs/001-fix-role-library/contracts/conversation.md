# Contract: Conversation

## POST `/api/agent/conversation/create`

### Request

```json
{
  "sessionId": "7aaf5fc0-bb81-4cd8-a7f7-9d2d8a7b9e11",
  "title": "新对话",
  "agentType": 0,
  "productType": "chat",
  "aiAgentId": "85374287"
}
```

### Rules

- `productType = "chat"` 时：
  - `aiAgentId` 可省略；省略后由后端绑定默认角色
  - 若传入的角色不可用，则创建失败
- 非 chat 模式忽略 `aiAgentId`

## GET `/api/agent/conversation/list`

### Response Item

```json
{
  "id": 12,
  "sessionId": "7aaf5fc0-bb81-4cd8-a7f7-9d2d8a7b9e11",
  "title": "新对话",
  "agentType": 0,
  "productType": "chat",
  "messageCount": 3,
  "pinned": 0,
  "lastMessagePreview": "帮我介绍一下这个角色的能力边界",
  "createTime": "2026-04-11 10:30:00",
  "updateTime": "2026-04-11 10:31:18",
  "role": {
    "agentId": "85374287",
    "agentName": "测试Agent",
    "available": true,
    "defaultRole": true
  }
}
```

## GET `/api/agent/conversation/detail`

### Response

```json
{
  "conversation": {
    "id": 12,
    "sessionId": "7aaf5fc0-bb81-4cd8-a7f7-9d2d8a7b9e11",
    "title": "新对话",
    "agentType": 0,
    "productType": "chat",
    "messageCount": 3,
    "pinned": 0,
    "lastMessagePreview": "帮我介绍一下这个角色的能力边界",
    "createTime": "2026-04-11 10:30:00",
    "updateTime": "2026-04-11 10:31:18",
    "role": {
      "agentId": "85374287",
      "agentName": "测试Agent",
      "available": true,
      "defaultRole": true
    }
  },
  "messages": []
}
```

### Rules

- chat 会话必须返回 `role`。
- 非 chat 会话返回 `role = null`。
- 当角色已失效但历史会话仍存在时：
  - `role.agentName` 优先返回快照名
  - `role.available = false`
  - 前端可以据此禁用继续发送或展示提示
