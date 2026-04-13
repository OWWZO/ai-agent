# Contract: Replay Event Payload

## 1. Replay Event Shape

```json
{
  "seqNo": 6,
  "eventType": "deep_search",
  "eventSubType": "report",
  "displayArea": "workspace",
  "title": "总结完成",
  "contentText": null,
  "taskId": "task-2",
  "taskOrder": 3,
  "status": "completed",
  "isFinal": true,
  "payload": {
    "messageType": "task",
    "messageOrder": 1,
    "messageId": "msg-6",
    "resultMap": {
      "messageType": "deep_search",
      "isFinal": true,
      "resultMap": {
        "messageType": "report",
        "query": "近三个月销量波动原因",
        "answer": "报告已生成"
      }
    },
    "artifactRefs": [
      {
        "artifactType": "html",
        "displayName": "research-report.html",
        "resourceKey": "oss://report/2026/04/research-report.html",
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
```

## 2. Field Semantics

- `contentText`
  - 只承载短文本或直接可读的事件片段
  - 不用于保存大体量工作区总结正文
- `payload`
  - 统一承载结构化扩展数据
  - 详情回放沿用实时 SSE 的 `eventData` 形状，保证前端还原逻辑只维护一套
  - 新增显示类型优先通过 `payload` 扩展，而不是新增表字段
- `artifactRefs`
  - 只引用稳定持久化资源
  - 允许多个引用
  - 缺失时必须返回 `missing=true`

## 3. Supported Payload Patterns

### 3.1 文本思考事件

```json
{
  "messageType": "plan_thought"
}
```

### 3.2 工具结果事件

```json
{
  "messageType": "task",
  "resultMap": {
    "messageType": "tool_result",
    "toolResult": {
      "toolName": "browser"
    }
  }
}
```

### 3.3 搜索/总结事件

```json
{
  "messageType": "task",
  "resultMap": {
    "messageType": "deep_search",
    "resultMap": {
      "messageType": "report",
      "answer": "已完成资料归纳"
    }
  },
  "artifactRefs": [
    {
      "artifactType": "markdown",
      "displayName": "summary.md",
      "resourceKey": "file-001",
      "downloadUrl": "https://file.example.com/download/001",
      "previewUrl": "https://file.example.com/preview/001",
      "missing": false
    }
  ]
}
```

## 4. Compatibility Rules

- 前端必须以 `eventType + payload.messageType + artifactRefs` 组合判断渲染方式。
- 当 `payload.messageType = task` 时，具体渲染类型继续读取 `payload.resultMap.messageType` 或更深层的 `payload.resultMap.resultMap.messageType`。
- 即使 `payload` 字段未来增加新键，旧前端也应忽略未知字段而不报错。
- 若 `artifactRefs` 全部缺失，前端应展示“内容不可读取”的明确状态，不得静默吞掉该事件。
