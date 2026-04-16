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
            "taskId": null,
            "taskOrder": null,
            "resultMap": {
              "planThought": "先确认分析范围，再逐步检索证据并汇总结论",
              "isFinal": true
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
            "taskId": null,
            "taskOrder": null,
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
            "taskOrder": 1,
            "resultMap": {
              "messageType": "deep_search",
              "searchFinish": true,
              "isFinal": true,
              "searchResult": {
                "query": ["近三个月销量波动原因"],
                "docs": [[{"title": "示例文档"}]]
              },
              "answer": "已整理 3 条关键发现"
            },
            "presentation": {
              "primaryArea": "timeline",
              "relatedAreas": [],
              "linkedArtifactKeys": []
            }
          }
        },
        {
          "seqNo": 4,
          "eventType": "html",
          "eventSubType": "final_state",
          "displayArea": "workspace",
          "title": "销量分析报告.html",
          "contentText": "最终报告",
          "taskId": "task-1",
          "taskOrder": 1,
          "messageIdExt": "msg-html-1",
          "status": "completed",
          "isFinal": 1,
          "payload": {
            "messageType": "task",
            "messageId": "msg-html-1",
            "taskId": "task-1",
            "taskOrder": 1,
            "resultMap": {
              "messageType": "html",
              "isFinal": true
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
            ],
            "presentation": {
              "primaryArea": "workspace",
              "relatedAreas": ["timeline"],
              "linkedArtifactKeys": ["file-123"]
            }
          }
        }
      ]
    }
  ]
}
```

### Rules

- 详情接口继续输出 `turns[].events[]`，但这些 `events` 表示最终界面细节块，不再表示全过程回放增量。
- `events[].payload` 必须直接兼容前端进行中链路消费的 `MESSAGE.EventData` 语义，历史不得再依赖独立 normalization 分支才能进入统一渲染路径。
- `PLAN_SOLVE` 与 `REACT` 必须返回结构化最终细节块；普通 `CHAT` 允许返回空 `events[]` 或轻量历史。
- `response` 继续表示单轮最终答案；如界面结束时仍可见 `task_summary` / `result` 块，则这些块也必须出现在 `events[]` 中。
- `messageIdExt` 继续作为兼容字段返回，但由 `payload.messageId` 派生。
- `isFinal` 在历史详情中恒定为 `1`。
- 若同一细节块同时服务于时间线与工作区，只允许返回一条 canonical 事件记录，通过 `displayArea + payload.presentation` 恢复跨区域关系。

## 3. 终态语义

### 3.1 成功完成

- `turn.status = 1`
- `turn.forceStop = 0`
- `event.status = "completed"`

### 3.2 异常结束

- `turn.status = 2`
- `turn.forceStop = 0`
- `event.status = "error"`

### 3.3 手动停止

- `turn.status = 3`
- `turn.forceStop = 1`
- `event.status = "force_stop"`

规则：

- 三类终态都必须返回“最后仍可见的细节块”。
- 历史 UI 不能因为 `error` 或 `force_stop` 而退化成摘要视图。

## 4. Artifact 缺失语义

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

规则：

- 缺失态仍保留该事件，不得静默吞掉。
- 前端点击后必须展示明确不可用原因，而不是通用 `Failed to fetch`。
