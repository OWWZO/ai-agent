# Data Model: 对话历史最终态重构与一致性修复

## 1. ConversationSummary (`ai_agent_conversation`)

- **Purpose**: 面向历史列表的轻量会话摘要，不承担详情快照职责。
- **Retained Fields**:
  - `id`
  - `session_id`
  - `device_id`
  - `user_id`
  - `title`
  - `agent_type`
  - `product_type`
  - `ai_agent_id`
  - `ai_agent_name_snapshot`
  - `message_count`
  - `pinned`
  - `last_message_preview`
  - `create_time`
  - `update_time`
  - `deleted`
- **Validation**:
  - `session_id` 全局唯一。
  - 列表查询只能返回当前 scope 可访问的会话。
  - 不新增详情型 JSON 字段。

## 2. ConversationTurn (`ai_agent_message`)

- **Purpose**: 表示一次用户请求及其最终完成结果，是历史详情的父级账本。
- **Retained Fields**:
  - `id`
  - `conversation_id`
  - `request_id`
  - `sort_order`
  - `query`
  - `files_json`
  - `agent_type`
  - `response`
  - `metrics_json`
  - `status`
  - `force_stop`
  - `started_at`
  - `finished_at`
  - `create_time`
  - `update_time`
  - `deleted`
- **Role Rules**:
  - `response` 仍表示单轮最终答案/上下文文本。
  - 最终界面细节块全部由 `ai_agent_message_event` 承担。
  - `PLAN_SOLVE` 与 `REACT` 读取结构化细节块；`CHAT` 允许只依赖 `response + files + metrics` 等轻量历史。
- **Validation**:
  - `request_id` 全局唯一。
  - 同一 `conversation_id` 下 `sort_order` 单调递增。

## 3. FinalVisibleDetailEvent (`ai_agent_message_event`)

- **Purpose**: 历史详情真正需要读取的最终界面细节表；一条记录只表达一个结束时仍可见的细节块。
- **Retained Fields**:
  - `id`
  - `message_id`
  - `seq_no`
  - `event_type`
  - `event_sub_type`
  - `display_area`
  - `task_id`
  - `task_order`
  - `title`
  - `content_text`
  - `payload_json`
  - `status`
  - `create_time`
  - `deleted`
- **Removed / Deprecated Fields**:
  - 仅服务于全过程回放的独立顶层字段继续清理，例如 `message_id_ext`、`is_final`、`started_at`、`ended_at`
  - 如仍需兼容历史前端匹配，`messageId` / `messageIdExt` 从 `payload_json` 派生，不作为新的真相字段恢复
- **Validation**:
  - `(message_id, seq_no)` 唯一。
  - 一条记录只表达一个最终界面细节块，不能把多个块压缩成一条摘要。
  - `display_area` 表示主展示区域。
  - 若同一细节同时关联工作区和对话区，只保留一条 canonical 记录，通过 payload 的展示关系恢复跨区域展示。

## 4. FinalVisibleDetailEvent Types

以下类型只要在对话结束时仍可见，就可以成为最终细节块：

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

### 4.1 Type Rules

- `event_type`
  - 直接对应最终界面细节块类型，优先复用当前前端已识别的消息类型。
- `event_sub_type`
  - 用于细分最终状态，例如 `final_state`、`search`、`report`。
- `display_area`
  - `timeline`：主显示于对话/时间线区域
  - `workspace`：主显示于右侧工作区/预览区域

## 5. Embedded Payload Model (`payload_json`)

### 5.1 CommonPayload

- `messageType`
- `messageId`
- `resultMap`
- `artifactRefs[]`
- `presentation`

### 5.2 PresentationLink

- **Purpose**: 表达同一 canonical 细节块如何在不同界面区域被恢复。
- **Fields**:
  - `primaryArea`
  - `relatedAreas[]`
  - `linkedArtifactKeys[]`
- **Validation**:
  - `primaryArea` 必须与顶层 `display_area` 一致。
  - `relatedAreas[]` 只表达附加展示关系，不产生第二条真相记录。

### 5.3 ArtifactReference

- **Purpose**: 统一表达工作区文件、HTML、Markdown、报告等最终产物引用。
- **Fields**:
  - `artifactType`
  - `displayName`
  - `resourceKey`
  - `downloadUrl`
  - `previewUrl`
  - `fileSize`
  - `mimeType`
  - `missing`
  - `missingReason`
- **Validation**:
  - 不允许只依赖本地临时路径。
  - `missing=true` 时必须返回明确原因。
  - `artifactRefs[]` 是 canonical 表达；`fileInfo/fileList` 只允许在兼容层派生。

## 6. History Detail Projection

### 6.1 ConversationDetailView

- `conversation`: `ConversationSummary`
- `turns[]`: `ConversationTurnView`

### 6.2 ConversationTurnView

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
- `events[]`: `FinalVisibleDetailEventView`

### 6.3 FinalVisibleDetailEventView

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
- `payload`
- `isFinal`

> `messageIdExt` 作为兼容字段继续输出，但从 payload `messageId` 派生。`isFinal` 在 API 层恒定输出 `1`。

## 7. Frontend History State

### 7.1 Summary List Cache

- 只保存会话摘要，不保存细节内容。

### 7.2 Structured Detail Cache

- 仅用于 `PLAN_SOLVE` 与 `REACT`。
- 保存 `turns[] + events[]`，输入即最终界面细节块，不再从摘要事件推理思考或工具调用。

### 7.3 Lightweight Chat Detail

- 适用于普通 `CHAT`。
- 继续以 `response/files/metrics` 为主，不强制接入复杂细节块模型。

## 8. State Transitions

### 8.1 Turn Lifecycle

- `STREAMING` → `COMPLETED`
- `STREAMING` → `ERROR`
- `STREAMING` → `FORCE_STOPPED`

### 8.2 Final Visible Detail Lifecycle

- 流式执行时在内存中积累原始增量
- 流结束时识别哪些内容最终仍留在界面上
- 将这些内容投影成最终界面细节块
- 每个最终细节块写入一条 `ai_agent_message_event`
- 历史读取只消费这些最终细节块，不再回放所有增量

### 8.3 Replacement Rule

- 若某个增量内容在结束前已从界面消失或被后续块替换，则不进入历史最终细节。
- 若某个思考/工具块结束时仍可见，则以最终文本或最终状态写入历史。

## 9. Old Data Strategy

- 旧历史数据与新模型不兼容时，允许直接删除。
- 不设计双路径读取、历史迁移脚本或回填逻辑。
