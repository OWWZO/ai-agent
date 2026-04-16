# Contract: Final Detail Event Payload

## 1. Canonical Event Shape

```json
{
  "seqNo": 3,
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
    },
    "artifactRefs": []
  }
}
```

## 2. Field Semantics

- `eventType`
  - 表示最终细节的大类，例如 `plan`、`task`、`tool_result`、`deep_search`、`task_summary`、`html`、`markdown`、`file`
  - 不再表示原始流式阶段本身
- `title`
  - 直接用于历史详情展示标题
  - 必须能在不解析深层 payload 的情况下表达当前细节是什么
- `contentText`
  - 只承载该最终细节的短文本摘要
  - 不用于保存大体量正文或整段报告全文
- `payload`
  - 承载该细节的结构化扩展数据
  - `artifactRefs[]` 是文件/报告/工作区结果的 canonical 表达
- `isFinal`
  - 仅作为兼容字段返回，值恒定为 `1`
  - 不再要求数据库保留同名列

## 3. Supported Final Detail Patterns

### 3.1 最终计划状态

```json
{
  "eventType": "plan",
  "eventSubType": "final_state",
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
}
```

### 3.2 最终工具细节

```json
{
  "eventType": "tool_result",
  "payload": {
    "messageType": "task",
    "resultMap": {
      "messageType": "tool_result",
      "toolResult": {
        "toolName": "browser"
      }
    }
  }
}
```

### 3.3 最终搜索/总结细节

```json
{
  "eventType": "deep_search",
  "eventSubType": "report",
  "payload": {
    "messageType": "task",
    "resultMap": {
      "messageType": "deep_search",
      "resultMap": {
        "messageType": "report",
        "query": "近三个月销量波动原因",
        "answer": "总结已完成"
      }
    }
  }
}
```

### 3.4 工作区文件 / 报告产物

```json
{
  "eventType": "html",
  "displayArea": "workspace",
  "payload": {
    "messageType": "html",
    "artifactRefs": [
      {
        "artifactType": "html",
        "displayName": "research-report.html",
        "resourceKey": "file-123",
        "downloadUrl": "https://file.example.com/download/123",
        "previewUrl": "https://file.example.com/preview/123",
        "missing": false,
        "missingReason": null
      }
    ]
  }
}
```

## 4. Disallowed Payload Patterns

以下内容不应再作为历史最终细节事件持久化：

- `plan_thought`
- `tool_thought`
- 非最终态 `deep_search` 片段
- 仅用于流式拼接的 `agent_stream` 增量文本
- 只对实时播放有价值、但对几天后回看无价值的中间过程片段

## 5. Compatibility Rules

- 历史详情读取顺序只依赖 `seqNo`，不再依赖原始流式 `messageIdExt`。
- 若前端旧面板仍消费 `resultMap.fileInfo`，可由 `artifactRefs[]` 派生，不允许把 `fileInfo` 作为数据库 canonical 字段继续保存。
- 未识别的 `payload` 新键必须被旧前端忽略，而不是导致历史详情报错。
- 若 `artifactRefs[]` 中所有引用都失效，前端必须展示明确缺失状态，不得静默吞掉该细节项。
