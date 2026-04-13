# Contract: Conversation History API

## 1. 会话列表

- **Method**: `GET`
- **Path**: `/api/agent/conversation/list`
- **Scope**: 必须按 `X-Device-Id` / 当前用户归属过滤

### Request

```http
GET /api/agent/conversation/list?pageNo=1&pageSize=50
X-Device-Id: device-xxx
```

### Response

```json
{
  "total": 2,
  "list": [
    {
      "sessionId": "session-001",
      "title": "分析最近三个月销量波动",
      "agentType": 1,
      "productType": "html",
      "messageCount": 3,
      "pinned": 0,
      "lastMessagePreview": "已完成销量波动原因分析",
      "role": null,
      "createTime": "2026-04-12T11:20:00",
      "updateTime": "2026-04-12T11:25:00"
    }
  ]
}
```

### Rules

- 列表接口只返回摘要，不返回 turn/event 明细。
- 列表结果必须按 `pinned DESC, updateTime DESC` 排序。
- 不返回 `tasksJson`、`renderSnapshotJson`、`events` 等 rich payload。

## 2. 会话详情

- **Method**: `GET`
- **Path**: `/api/agent/conversation/detail`
- **Scope**: 必须按 `sessionId + device/user scope` 校验

### Request

```http
GET /api/agent/conversation/detail?sessionId=session-001
X-Device-Id: device-xxx
```

### Response

```json
{
  "conversation": {
    "sessionId": "session-001",
    "title": "分析最近三个月销量波动",
    "agentType": 1,
    "productType": "html",
    "messageCount": 3,
    "pinned": 0,
    "lastMessagePreview": "已完成销量波动原因分析",
    "role": null,
    "createTime": "2026-04-12T11:20:00",
    "updateTime": "2026-04-12T11:25:00"
  },
  "turns": [
    {
      "requestId": "req-001",
      "sortOrder": 0,
      "query": "分析最近三个月销量波动",
      "files": [],
      "agentType": 1,
      "response": "销量下降主要集中在华东区域……",
      "status": 1,
      "forceStop": 0,
      "metrics": {
        "eventCount": 8
      },
      "startedAt": "2026-04-12T11:20:01",
      "finishedAt": "2026-04-12T11:20:30",
      "events": [
        {
          "seqNo": 1,
          "eventType": "plan_thought",
          "eventSubType": null,
          "displayArea": "timeline",
          "title": "思考中",
          "contentText": "先确认数据范围，再拆解波动因素",
          "taskId": null,
          "taskOrder": null,
          "status": "completed",
          "isFinal": false,
          "payload": {
            "messageType": "plan_thought",
            "resultMap": {
              "planThought": "先确认数据范围，再拆解波动因素",
              "isFinal": false
            }
          }
        }
      ]
    }
  ]
}
```

### Rules

- 详情接口以 `turns[]` 替代旧 `messages[]` rich 字段结构。
- 每个 turn 仅包含 request 级信息与 `events[]`。
- `response` 是单轮最终回答/上下文文本，不是 rich replay 的权威来源。
- `events[]` 是前端历史回放的唯一权威来源。
- `payload` 兼容实时 SSE 的 `eventData` 结构；若事件属于任务流，`payload.messageType` 可能为 `task`，再由 `payload.resultMap.messageType` 区分具体节点类型。

## 3. 错误语义

### 会话不存在或越权

```json
{
  "code": "ILLEGAL_PARAMETER",
  "info": "会话不存在"
}
```

### Artifact 引用失效

详情接口仍返回 turn/event，但对应事件 payload 中标记缺失状态：

```json
{
  "artifactRefs": [
    {
      "artifactType": "html",
      "displayName": "总结报告.html",
      "resourceKey": "file-123",
      "downloadUrl": null,
      "previewUrl": null,
      "missing": true,
      "missingReason": "resource not found"
    }
  ]
}
```
