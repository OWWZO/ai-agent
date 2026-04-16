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
      "createTime": "2026-04-16T11:20:00",
      "updateTime": "2026-04-16T11:25:00"
    }
  ]
}
```

### Rules

- 列表接口只返回摘要，不返回 turn/event 明细。
- 排序规则保持 `pinned DESC, updateTime DESC`。
- 不返回最终细节 payload、artifactRefs 或历史过程型字段。

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
    "createTime": "2026-04-16T11:20:00",
    "updateTime": "2026-04-16T11:25:00"
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
        "detailCount": 5
      },
      "startedAt": "2026-04-16T11:20:01",
      "finishedAt": "2026-04-16T11:20:30",
      "events": [
        {
          "seqNo": 1,
          "eventType": "plan",
          "eventSubType": "final_state",
          "displayArea": "timeline",
          "title": "执行计划",
          "contentText": "全部计划已完成",
          "taskId": null,
          "taskOrder": null,
          "status": "completed",
          "isFinal": 1,
          "payload": {
            "messageType": "plan",
            "resultMap": {
              "title": "执行计划",
              "steps": [
                "确认分析范围",
                "检索核心原因",
                "整理总结"
              ],
              "stepStatus": [
                "completed",
                "completed",
                "completed"
              ]
            }
          }
        },
        {
          "seqNo": 2,
          "eventType": "deep_search",
          "eventSubType": "search",
          "displayArea": "timeline",
          "title": "检索：近三个月销量波动原因",
          "contentText": "已整理 3 条关键发现",
          "taskId": "task-2",
          "taskOrder": 1,
          "status": "completed",
          "isFinal": 1,
          "payload": {
            "messageType": "task",
            "resultMap": {
              "messageType": "deep_search",
              "resultMap": {
                "messageType": "search",
                "query": [
                  "近三个月销量波动原因"
                ],
                "answer": "已整理 3 条关键发现"
              }
            }
          }
        },
        {
          "seqNo": 5,
          "eventType": "html",
          "eventSubType": null,
          "displayArea": "workspace",
          "title": "销量分析报告.html",
          "contentText": "最终报告",
          "taskId": null,
          "taskOrder": null,
          "status": "completed",
          "isFinal": 1,
          "payload": {
            "messageType": "html",
            "artifactRefs": [
              {
                "artifactType": "html",
                "displayName": "销量分析报告.html",
                "resourceKey": "file-123",
                "downloadUrl": "https://file.example.com/download/123",
                "previewUrl": "https://file.example.com/preview/123",
                "fileSize": 40960,
                "mimeType": "text/html",
                "missing": false,
                "missingReason": null
              }
            ]
          }
        }
      ]
    }
  ]
}
```

### Rules

- 详情接口继续输出 `turns[].events[]`，但这些 `events` 只表示最终细节事件。
- `response` 是单轮最终答案/上下文文本；最终工具细节、plan 完成态和 workspace 产物全部来自 `events[]`。
- `events[]` 只允许包含对话结束时仍需向用户展示的最终细节，不允许返回过程回放型事件。
- `isFinal` 可以作为兼容字段恒定返回 `1`，但不要求数据库保留同名列。
- `payload.artifactRefs[]` 是 canonical 文件引用表达；如旧组件仍消费 `fileInfo`，只允许在响应层或前端兼容层派生。

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
      "displayName": "summary-report.html",
      "resourceKey": "file-123",
      "downloadUrl": null,
      "previewUrl": null,
      "missing": true,
      "missingReason": "引用资源不存在或已失效"
    }
  ]
}
```
