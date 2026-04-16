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
        "detailCount": 8
      },
      "startedAt": "2026-04-16T11:20:01",
      "finishedAt": "2026-04-16T11:20:30",
      "events": [
        {
          "seqNo": 1,
          "eventType": "plan_thought",
          "eventSubType": "final_state",
          "displayArea": "timeline",
          "title": "思考过程",
          "contentText": "先确认分析范围，再逐步检索证据并汇总结论",
          "taskId": null,
          "taskOrder": null,
          "messageIdExt": "msg-thought-1",
          "status": "completed",
          "isFinal": 1,
          "payload": {
            "messageType": "plan_thought",
            "messageId": "msg-thought-1",
            "resultMap": {
              "planThought": "先确认分析范围，再逐步检索证据并汇总结论",
              "isFinal": true
            },
            "presentation": {
              "primaryArea": "timeline",
              "relatedAreas": [],
              "linkedArtifactKeys": []
            }
          }
        },
        {
          "seqNo": 2,
          "eventType": "plan",
          "eventSubType": "final_state",
          "displayArea": "timeline",
          "title": "执行计划",
          "contentText": "全部计划已完成",
          "taskId": null,
          "taskOrder": null,
          "messageIdExt": "msg-plan-1",
          "status": "completed",
          "isFinal": 1,
          "payload": {
            "messageType": "plan",
            "messageId": "msg-plan-1",
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
          "seqNo": 3,
          "eventType": "task",
          "eventSubType": "final_state",
          "displayArea": "timeline",
          "title": "检索销量波动原因",
          "contentText": "任务 1",
          "taskId": "task-1",
          "taskOrder": 1,
          "messageIdExt": "msg-task-1",
          "status": "completed",
          "isFinal": 1,
          "payload": {
            "messageType": "task",
            "messageId": "msg-task-1",
            "taskId": "task-1",
            "resultMap": {
              "messageType": "task",
              "task": "检索销量波动原因"
            }
          }
        },
        {
          "seqNo": 4,
          "eventType": "tool_thought",
          "eventSubType": "final_state",
          "displayArea": "timeline",
          "title": "检索策略思考",
          "contentText": "先看区域分布，再对比促销与库存因素",
          "taskId": "task-1",
          "taskOrder": 1,
          "messageIdExt": "msg-tool-thought-1",
          "status": "completed",
          "isFinal": 1,
          "payload": {
            "messageType": "task",
            "messageId": "msg-tool-thought-1",
            "taskId": "task-1",
            "resultMap": {
              "messageType": "tool_thought",
              "toolThought": "先看区域分布，再对比促销与库存因素",
              "isFinal": true
            }
          }
        },
        {
          "seqNo": 5,
          "eventType": "deep_search",
          "eventSubType": "search",
          "displayArea": "timeline",
          "title": "检索：近三个月销量波动原因",
          "contentText": "已整理 3 条关键发现",
          "taskId": "task-1",
          "taskOrder": 1,
          "messageIdExt": "msg-search-1",
          "status": "completed",
          "isFinal": 1,
          "payload": {
            "messageType": "task",
            "messageId": "msg-search-1",
            "taskId": "task-1",
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
          "seqNo": 6,
          "eventType": "deep_search",
          "eventSubType": "report",
          "displayArea": "timeline",
          "title": "总结完成",
          "contentText": "华东区域波动与促销衰减、缺货共同相关",
          "taskId": "task-1",
          "taskOrder": 1,
          "messageIdExt": "msg-report-1",
          "status": "completed",
          "isFinal": 1,
          "payload": {
            "messageType": "task",
            "messageId": "msg-report-1",
            "taskId": "task-1",
            "resultMap": {
              "messageType": "deep_search",
              "resultMap": {
                "messageType": "report",
                "query": "近三个月销量波动原因",
                "answer": "华东区域波动与促销衰减、缺货共同相关"
              }
            }
          }
        },
        {
          "seqNo": 7,
          "eventType": "html",
          "eventSubType": null,
          "displayArea": "workspace",
          "title": "销量分析报告.html",
          "contentText": "最终报告",
          "taskId": "task-1",
          "taskOrder": 1,
          "messageIdExt": "msg-html-1",
          "status": "completed",
          "isFinal": 1,
          "payload": {
            "messageType": "html",
            "messageId": "msg-html-1",
            "presentation": {
              "primaryArea": "workspace",
              "relatedAreas": [
                "timeline"
              ],
              "linkedArtifactKeys": [
                "file-123"
              ]
            },
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

- 详情接口继续输出 `turns[].events[]`，但这些 `events` 表示最终界面细节块，不再表示全过程回放增量，也不再退化为最小摘要。
- `PLAN_SOLVE` 与 `REACT` 必须返回结构化最终细节块；普通 `CHAT` 允许返回空 `events[]` 或轻量历史。
- `response` 仍表示单轮最终答案/上下文文本；若界面还展示 `task_summary` / `result` 类块，则这些块也必须出现在 `events[]` 中。
- `events[]` 允许包含 `plan_thought`、`plan`、`task`、`tool_thought`、`tool_result`、`deep_search`、`task_summary`、`result`、`html/markdown/file/...` 等结束时仍可见的块类型。
- `messageIdExt` 继续作为兼容字段返回，但由 payload `messageId` 派生，不要求数据库保留独立列。
- `payload.artifactRefs[]` 是 canonical 文件引用表达；如旧组件仍消费 `fileInfo`，只允许在响应层或前端兼容层派生。
- 若同一细节块同时服务于时间线与工作区，只允许返回一条 canonical 事件记录，通过 `displayArea + payload.presentation` 恢复跨区域关系。

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
