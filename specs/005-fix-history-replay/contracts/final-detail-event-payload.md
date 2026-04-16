# Contract: Final Detail Event Payload

## 1. Contract Goal

历史详情事件包含两层信息：

- 外层 `ConversationEventRespVO`
  - 负责时间线顺序、标题、区域、终态和兼容字段
- 内层 `payload`
  - 必须直接兼容进行中链路已消费的 `MESSAGE.EventData` 语义

这意味着历史重开时，前端应当可以把 `payload` 直接喂给 `combineData` / `handleTaskData`，而不是先走历史专用修复分支。

## 2. Canonical Event Envelope

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
    "taskId": "task-1",
    "taskOrder": 1,
    "resultMap": {
      "messageType": "tool_thought",
      "toolThought": "先看区域分布，再对比促销与库存因素",
      "isFinal": true
    },
    "artifactRefs": [],
    "presentation": {
      "primaryArea": "timeline",
      "relatedAreas": [],
      "linkedArtifactKeys": []
    }
  }
}
```

## 3. Outer Envelope Semantics

| Field | Meaning |
|-------|---------|
| `seqNo` | 最终展示顺序 |
| `eventType` | 可见块类型，供 `Dialogue` 分组使用 |
| `eventSubType` | 类型细分，如 `final_state`、`search`、`report` |
| `displayArea` | 主展示区域，`timeline` 或 `workspace` |
| `title` | UI 直出标题 |
| `contentText` | UI 直出摘要 |
| `taskId` / `taskOrder` | 任务分组与排序信息 |
| `messageIdExt` | 兼容字段，值来自 `payload.messageId` |
| `status` | 终态，支持 `completed`、`error`、`force_stop` |
| `isFinal` | 恒定为 `1` |

## 4. Canonical Payload Shape

### 4.1 Shared Keys

| Field | Meaning |
|-------|---------|
| `messageType` | 与 live `MESSAGE.EventData.messageType` 一致 |
| `messageId` | 稳定 block identity |
| `taskId` | 任务归属 |
| `taskOrder` | 任务顺序 |
| `resultMap` | 与 live 处理链一致的结构化内容 |
| `artifactRefs[]` | canonical 产物引用 |
| `presentation` | 多区域展示关系 |

### 4.2 Message Type Rules

- `plan_thought`
  - `payload.messageType = "plan_thought"`
  - `payload.resultMap.planThought` 保存最终思考文本
- `plan`
  - `payload.messageType = "plan"`
  - `payload.resultMap` 直接是计划对象
- 任务内块
  - `tool_thought`
  - `tool_result`
  - `deep_search`
  - `task_summary`
  - `html/markdown/file/code/browser/...`
  - 对这些块统一使用 `payload.messageType = "task"`
  - 实际细分写在 `payload.resultMap.messageType`

## 5. Supported Payload Patterns

### 5.1 Final Plan Thought

```json
{
  "eventType": "plan_thought",
  "payload": {
    "messageType": "plan_thought",
    "messageId": "msg-thought-1",
    "taskId": null,
    "taskOrder": null,
    "resultMap": {
      "planThought": "先确认范围，再逐步检索证据并汇总",
      "isFinal": true
    }
  }
}
```

### 5.2 Final Plan State

```json
{
  "eventType": "plan",
  "payload": {
    "messageType": "plan",
    "messageId": "msg-plan-1",
    "taskId": null,
    "taskOrder": null,
    "resultMap": {
      "title": "执行计划",
      "steps": ["确认分析范围", "检索核心原因", "整理总结"],
      "stepStatus": ["completed", "completed", "completed"]
    }
  }
}
```

### 5.3 Final Deep Search Result

一条搜索 query 对应一条最终块：

```json
{
  "eventType": "deep_search",
  "eventSubType": "search",
  "payload": {
    "messageType": "task",
    "messageId": "msg-search-1",
    "taskId": "task-1",
    "taskOrder": 1,
    "resultMap": {
      "messageType": "deep_search",
      "isFinal": true,
      "searchFinish": true,
      "searchResult": {
        "query": ["近三个月销量波动原因"],
        "docs": [[{"title": "示例文档"}]]
      },
      "answer": "已整理 3 条关键发现"
    }
  }
}
```

### 5.4 Final Workspace Artifact

```json
{
  "eventType": "html",
  "displayArea": "workspace",
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
        "displayName": "research-report.html",
        "resourceKey": "file-123",
        "downloadUrl": "https://file.example.com/download/123",
        "previewUrl": "https://file.example.com/preview/123",
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
```

### 5.5 Error Or Force-Stop Snapshot

```json
{
  "eventType": "deep_search",
  "eventSubType": "search",
  "status": "force_stop",
  "payload": {
    "messageType": "task",
    "messageId": "msg-search-partial-1",
    "taskId": "task-1",
    "taskOrder": 1,
    "resultMap": {
      "messageType": "deep_search",
      "isFinal": true,
      "searchFinish": false,
      "searchResult": {
        "query": ["华东销量波动原因"],
        "docs": [[{"title": "已抓取的中间结果"}]]
      }
    }
  }
}
```

规则：

- 即使未成功完成，只要该块在终止瞬间仍可见，就必须作为最终快照保留。
- `status` 反映终态，`resultMap.isFinal` 仍为 `true`，表示它是“最终可回看版本”，而不是流式中间片段。

## 6. Presentation Object

```json
{
  "primaryArea": "workspace",
  "relatedAreas": ["timeline"],
  "linkedArtifactKeys": ["file-123"]
}
```

Rules:

- `primaryArea` 必须与顶层 `displayArea` 一致。
- `relatedAreas[]` 只描述同一块还会在哪里展示，不生成第二条 event。
- `linkedArtifactKeys[]` 用于把时间线点击与工作区预览绑定到同一份产物引用。

## 7. Disallowed Patterns

以下内容不应再作为历史最终细节事件持久化：

- 对话结束前已经从界面消失的瞬时流式增量
- 只服务打字机效果的 token 片段
- 把多个 `deep_search/search` 合并成同一条 event
- 对同一块内容分别写一条 timeline 记录和一条 workspace 记录
- 只有“搜索完成”“工具完成”这类通用提示，却没有对应最终思考/工具/搜索内容的替代摘要

## 8. Compatibility Rules

- `PLAN_SOLVE` 与 `REACT` 都必须返回结构化最终细节事件。
- 普通 `CHAT` 不强制返回同等复杂的结构化事件。
- 历史读取顺序只依赖 `seqNo`。
- `artifactRefs[]` 是唯一 canonical 文件引用表达；如旧组件仍消费 `fileInfo`，只能由兼容层派生，不能重新作为数据库真相字段。
- 若 `artifactRefs[]` 中的引用失效，前端必须继续展示该事件，并给出明确缺失原因。
