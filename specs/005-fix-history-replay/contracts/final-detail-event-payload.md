# Contract: Final Visible Detail Event Payload

## 1. Canonical Event Shape

```json
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
    "presentation": {
      "primaryArea": "timeline",
      "relatedAreas": [],
      "linkedArtifactKeys": []
    },
    "resultMap": {
      "messageType": "tool_thought",
      "toolThought": "先看区域分布，再对比促销与库存因素",
      "isFinal": true
    },
    "artifactRefs": []
  }
}
```

## 2. Field Semantics

- `eventType`
  - 直接表示最终界面细节块类型，优先复用当前前端已识别的消息类型。
  - 允许出现 `plan_thought`、`tool_thought`、`task_summary`、`result` 等类型，只要这些块在对话结束时仍可见。
- `eventSubType`
  - 用于区分同一大类下的最终状态，例如 `final_state`、`search`、`report`。
- `displayArea`
  - 表示 canonical 细节块的主展示区域。
  - `timeline` 与 `workspace` 的跨区域关系通过 `payload.presentation` 表达，不产生第二条独立真相记录。
- `title`
  - 直接用于历史详情展示标题。
  - 必须能在不解析深层 payload 的情况下表达当前细节块是什么。
- `contentText`
  - 用于当前细节块的用户可读摘要。
  - 不要求完整正文都塞在此字段里；完整文本放入合适的 `payload.resultMap` 中。
- `messageIdExt`
  - 作为兼容字段返回，值来自 `payload.messageId`。
  - 不再要求数据库保留同名列。
- `isFinal`
  - 仅作为兼容字段返回，值恒定为 `1`。

## 3. Payload Shape

### 3.1 Common Keys

- `messageType`
  - 保持与现有前端恢复链路兼容，常见为 `plan_thought`、`plan`、`task`、`html`、`file` 等。
- `messageId`
  - 当前细节块的稳定标识，用于前端在历史时间线中匹配对应卡片。
- `resultMap`
  - 保留该细节块的结构化内容。
- `artifactRefs[]`
  - 产物引用的 canonical 表达。
- `presentation`
  - 描述主展示区域、附属区域和关联产物关系。

### 3.2 Presentation Object

```json
{
  "primaryArea": "workspace",
  "relatedAreas": [
    "timeline"
  ],
  "linkedArtifactKeys": [
    "file-123"
  ]
}
```

Rules:

- `primaryArea` 必须与顶层 `displayArea` 一致。
- `relatedAreas[]` 用于表达同一 canonical 细节块还会在哪些区域被恢复展示。
- `linkedArtifactKeys[]` 用于把时间线细节块与工作区产物引用关联起来。

## 4. Supported Final Detail Patterns

### 4.1 最终思考过程

```json
{
  "eventType": "plan_thought",
  "eventSubType": "final_state",
  "payload": {
    "messageType": "plan_thought",
    "messageId": "msg-thought-1",
    "resultMap": {
      "planThought": "先确认范围，再逐步检索证据并汇总",
      "isFinal": true
    }
  }
}
```

### 4.2 最终计划状态

```json
{
  "eventType": "plan",
  "eventSubType": "final_state",
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
}
```

### 4.3 最终任务头 / 工具思考 / 工具结果

```json
{
  "eventType": "task",
  "payload": {
    "messageType": "task",
    "messageId": "msg-task-1",
    "taskId": "task-1",
    "resultMap": {
      "messageType": "task",
      "task": "检索销量波动原因"
    }
  }
}
```

```json
{
  "eventType": "tool_result",
  "payload": {
    "messageType": "task",
    "messageId": "msg-tool-result-1",
    "taskId": "task-1",
    "resultMap": {
      "messageType": "tool_result",
      "toolResult": {
        "toolName": "browser"
      }
    }
  }
}
```

### 4.4 最终搜索 / 总结细节

```json
{
  "eventType": "deep_search",
  "eventSubType": "search",
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
}
```

```json
{
  "eventType": "deep_search",
  "eventSubType": "report",
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
}
```

### 4.5 工作区文件 / 报告产物

```json
{
  "eventType": "html",
  "displayArea": "workspace",
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

## 5. Disallowed Payload Patterns

以下内容不应再作为历史最终细节事件持久化：

- 对话结束前已经从界面消失的瞬时流式增量
- 仅用于在线打字机效果的部分 token 片段
- 同一细节块分别为时间线和工作区各写一份互相独立的镜像记录
- 只有“搜索完成”“工具完成”这类通用提示、却丢失原始最终可见思考/工具内容的替代摘要

## 6. Compatibility Rules

- `PLAN_SOLVE` 与 `REACT` 都必须返回结构化最终细节事件。
- 普通 `CHAT` 不强制返回同等复杂的结构化事件。
- 历史详情读取顺序只依赖 `seqNo`。
- 若前端旧面板仍消费 `resultMap.fileInfo`，可由 `artifactRefs[]` 派生，不允许把 `fileInfo` 重新定义为数据库 canonical 字段。
- 若 `artifactRefs[]` 中所有引用都失效，前端必须展示明确缺失状态，不得静默吞掉该细节项。
