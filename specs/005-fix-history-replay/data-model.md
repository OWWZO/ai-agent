# Data Model: 对话细节统一 UI 与最终态历史重构

## 1. Conversation Summary

### 1.1 `ai_agent_conversation`

- **Purpose**: 历史列表和访问控制用的轻量会话摘要。
- **Canonical Responsibilities**:
  - 会话归属：`session_id`、`device_id`、`user_id`
  - 列表展示：`title`、`agent_type`、`product_type`、`ai_agent_name_snapshot`
  - 排序与摘要：`message_count`、`pinned`、`last_message_preview`
  - 生命周期：`create_time`、`update_time`、`deleted`
- **Validation**:
  - `session_id` 全局唯一。
  - 不新增任何详情级 JSON 或最终态快照字段。
  - `message_count` 只反映轮次数，不反映细节块数量。

## 2. Conversation Turn Ledger

### 2.1 `ai_agent_message`

- **Purpose**: 单轮请求及其终态账本，是最终细节块的父级记录。
- **Canonical Responsibilities**:
  - 父级关系：`conversation_id`
  - 轮次标识：`request_id`、`sort_order`
  - 输入输出：`query`、`files_json`、`response`
  - 终态与指标：`status`、`force_stop`、`metrics_json`
  - 时间：`started_at`、`finished_at`
- **Validation**:
  - `request_id` 全局唯一。
  - `(conversation_id, sort_order)` 唯一，保持单调递增。
  - `response` 继续保存单轮最终回复正文，不承担计划/工具/搜索等结构化细节。
  - `status` 与 `force_stop` 必须能区分 `completed`、`error`、`force_stop` 三种结束态。

### 2.2 Turn Status Mapping

| Turn Field | Meaning |
|-----------|---------|
| `status = 1, force_stop = 0` | `completed` |
| `status = 2, force_stop = 0` | `error` |
| `status = 3, force_stop = 1` | `force_stop` |

## 3. Final Detail Block Snapshot

### 3.1 `ai_agent_message_event`

- **Purpose**: 历史详情的唯一细节真相源，一条记录对应一个最终仍可见的界面块。
- **Canonical Responsibilities**:
  - 父级关系：`message_id`
  - 最终顺序：`seq_no`
  - 细节类型：`event_type`、`event_sub_type`
  - 主展示区域：`display_area`
  - 分组关系：`task_id`、`task_order`
  - UI 直出文本：`title`、`content_text`
  - 最终快照：`payload_json`
  - 终态：`status`
  - 生命周期：`create_time`、`deleted`
- **Validation**:
  - `(message_id, seq_no)` 唯一。
  - 一条记录只能表达一个最终可见细节块，禁止把多个搜索或多个工具块压缩进同一行。
  - `seq_no` 表示“对话结束时最终展示顺序”，不是过程回放序号。
  - `status` 使用终态语义，推荐值为 `completed`、`error`、`force_stop`。

### 3.2 Removed Replay-Only Fields

以下字段不再作为数据库真相字段保留：

- `message_id_ext`
- `is_final`
- `started_at`
- `ended_at`

说明：

- `message_id_ext` 由 `payload_json.messageId` 派生后在 API 层兼容输出。
- `is_final` 在历史详情 API 中恒定输出 `1`，不需要单独落库。

## 4. Canonical Block Identity And Order

### 4.1 Stable Block Identity

- **Canonical Field**: `payload_json.messageId`
- **Why**: 当前前端 `combineData`、`handleTaskData`、`Dialogue`、`ActionView` 都已经把 `messageId` 视为任务卡片与工作区联动的稳定标识。
- **Rule**:
  - 同一最终细节块在整个 turn 内必须拥有稳定不变的 `messageId`。
  - 多条同类型块必须使用不同 `messageId`，例如拆分后的多条 `deep_search/search`。

### 4.2 Display Order

- **Canonical Field**: `seq_no`
- **Rule**:
  - 历史详情按 `seq_no ASC` 恢复。
  - `seq_no` 与 `payload_json.messageOrder` 可保持一致，但数据库真相源是 `seq_no`。

## 5. Canonical Detail Payload

### 5.1 `payload_json` Shape

`payload_json` 必须直接兼容前端进行中链路消费的 `MESSAGE.EventData` 语义：

```json
{
  "messageType": "task",
  "messageId": "task-search-1",
  "taskId": "task-1",
  "taskOrder": 1,
  "resultMap": {
    "messageType": "deep_search",
    "searchFinish": true,
    "isFinal": true,
    "searchResult": {
      "query": ["华东销量波动原因"],
      "docs": [[{"title": "示例文档"}]]
    }
  },
  "artifactRefs": [],
  "presentation": {
    "primaryArea": "timeline",
    "relatedAreas": [],
    "linkedArtifactKeys": []
  }
}
```

### 5.2 Common Payload Fields

| Field | Meaning |
|-------|---------|
| `messageType` | 与进行中 `MESSAGE.EventData.messageType` 对齐，通常为 `plan`、`plan_thought`、`task` |
| `messageId` | 稳定 block identity |
| `taskId` | 任务分组键 |
| `taskOrder` | 任务顺序 |
| `resultMap` | 与进行中一致的结构化内容 |
| `artifactRefs[]` | 产物引用 canonical 表达 |
| `presentation` | 时间线与工作区的跨区域关系 |

### 5.3 Presentation Link

| Field | Meaning |
|-------|---------|
| `primaryArea` | 与顶层 `display_area` 一致 |
| `relatedAreas[]` | 同一块还会在哪些区域恢复展示 |
| `linkedArtifactKeys[]` | 与工作区预览关联的稳定产物键 |

## 6. Supported Final Block Types

以下类型只要在轮次结束时仍可见，就能成为最终细节块：

- `plan_thought`
- `plan`
- `task`
- `tool_thought`
- `tool_result`
- `deep_search`
- `task_summary`
- `result`
- `browser`
- `code`
- `html`
- `markdown`
- `file`
- `knowledge`
- `data_analysis`
- `ppt`

### 6.1 Type Rules

- `event_type`
  - 表示时间线/工作区看到的块类型，用于 `Dialogue` 分组和标题/状态渲染。
- `payload_json.messageType`
  - 表示喂给进行中处理链路的核心类型。
  - 对 `tool_thought`、`tool_result`、`deep_search`、`task_summary` 这类任务内块，通常固定为 `task`，真实子类型放到 `resultMap.messageType`。

## 7. API Projection Model

### 7.1 `ConversationDetailRespVO`

- `conversation`: 会话摘要
- `turns[]`: 单轮明细

### 7.2 `ConversationTurnRespVO`

- `requestId`
- `sortOrder`
- `query`
- `files`
- `agentType`
- `response`
- `status`
- `forceStop`
- `metrics`
- `startedAt`
- `finishedAt`
- `events[]`

### 7.3 `ConversationEventRespVO`

- `seqNo`
- `eventType`
- `eventSubType`
- `displayArea`
- `taskId`
- `taskOrder`
- `messageIdExt`
- `title`
- `contentText`
- `status`
- `isFinal`
- `payload`

Compatibility 规则：

- `messageIdExt = payload.messageId`
- `isFinal = 1`

## 8. Frontend Runtime Model

### 8.1 Shared Render Entry

- 历史详情进入前端后，应直接恢复为与进行中一致的 `CHAT.ChatItem`。
- `restoreTurn` 只负责把 `turn + events` 转为 `CHAT.ChatItem`，不再承担“补 plan 结构”“拆多条搜索”“派生 artifact 兼容视图”这类历史专用语义修复。

### 8.2 Timeline And Workspace Link

- 左侧时间线使用 `eventType`、`title`、`contentText`、`payload` 进行分组和点击联动。
- 右侧工作区通过同一个 `payload.messageId` / `artifactRefs[]` 找到对应任务或文件预览。
- 同一块既出现在时间线又关联工作区时，只保留一条 canonical event。

## 9. State Transitions

### 9.1 Turn Lifecycle

- `STREAMING -> COMPLETED`
- `STREAMING -> ERROR`
- `STREAMING -> FORCE_STOP`

### 9.2 Final Block Lifecycle

1. 流式阶段在内存里积累原始增量。
2. 终态到达时判断哪些块在界面上仍可见。
3. 将这些块投影为最终快照。
4. 逐块写入 `ai_agent_message_event`。
5. 历史详情只读取这些最终快照，不再回放过程增量。

## 10. Old Data Strategy

- 旧错误历史数据允许直接删除。
- 不设计迁移脚本。
- 不设计双读双写兼容层。
