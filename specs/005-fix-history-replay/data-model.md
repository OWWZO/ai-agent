# Data Model: 对话历史最终态重构与一致性修复

## 1. ConversationSummary (`ai_agent_conversation`)

- **Purpose**: 历史列表和归属管理的轻量摘要，不承担详情快照职责。
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
- **Attribute Cleanup**:
  - 保持 `session_id` 唯一，不新增任何详情型 JSON 字段。
  - `message_count`、`pinned`、`deleted` 继续保持非负布尔/计数语义。
  - 列表查询继续依赖 `idx_device_id`、`idx_user_id`。
- **Validation**:
  - `session_id` 全局唯一。
  - `device_id` / `user_id` 至少一个可用于归属校验。
  - 列表读取只允许返回 scope 匹配的数据。

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
- **Attribute Cleanup**:
  - `response` 只表示单轮最终答案/上下文文本，不代表最终细节集合。
  - 不新增也不恢复任何 `thought/plan/tasks/render snapshot` 风格字段。
  - `uk_conversation_sort` 已能支撑 `(conversation_id, sort_order)` 查询；`idx_conversation_sort` 可作为冗余索引删除。
- **Validation**:
  - `request_id` 全局唯一。
  - 同一 `conversation_id` 下 `sort_order` 单调递增。
  - `status` 仍沿用现有 `0/1/2/3` 语义，避免额外扩散。

## 3. FinalDetailEvent (`ai_agent_message_event`)

- **Purpose**: 历史详情真正需要读取的最终态细节表，一条记录只表达一个最终可见细节项。
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
- **Removed Fields**:
  - `message_id_ext`
  - `is_final`
  - `started_at`
  - `ended_at`
- **Attribute Cleanup**:
  - `payload_json` 只保留最终态细节所需的结构化数据，不再保存过程回放辅助字段。
  - `status` 收敛为最终态可读语义，重点覆盖 `completed` / `error`；资源缺失优先通过 `artifactRefs[].missing` 表达。
  - `uk_message_seq(message_id, seq_no)` 保留；`idx_message_id` 与该唯一索引重复，删除。
  - 当前历史读取链路只按 `message_id` 顺序查询，`idx_task_id` 删除。
- **Validation**:
  - `(message_id, seq_no)` 唯一。
  - 一条记录只表达一个最终可见细节项，不能混装多个同类最终细节。
  - 历史详情默认只按 `message_id + seq_no` 顺序读取。

## 4. ArtifactReference (embedded in `FinalDetailEvent.payload_json`)

- **Purpose**: 统一表达工作区文件、报告、导出产物等可长期访问的稳定引用。
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
  - 不允许把工作区临时路径作为唯一定位信息。
  - `missing=true` 时必须带明确原因，供前端直接展示。
  - `artifactRefs[]` 是 payload 的 canonical 表达；旧 `fileInfo/fileList` 只允许在兼容层派生。

## 5. ConversationDetailView (API projection)

- **Purpose**: 面向 trigger/ui 的历史详情投影视图。
- **Fields**:
  - `conversation`: `ConversationSummary`
  - `turns[]`: `ConversationTurnView`

### 5.1 ConversationTurnView

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
- `events[]`: `FinalDetailEventView`

### 5.2 FinalDetailEventView

- `seqNo`
- `eventType`
- `eventSubType`
- `displayArea`
- `taskId`
- `taskOrder`
- `title`
- `contentText`
- `status`
- `payload`
- `isFinal`

> `isFinal` 在 API 层可以继续输出 `1` 作为兼容位，但不再依赖数据库字段。

## 6. Frontend History State

### 6.1 Summary List Cache

- 来源于列表接口。
- 只保存会话摘要，不带详情内容。

### 6.2 Detail Cache

- 只在选中某个历史会话后缓存 `turns[] + events[]`。
- 输入数据已是最终细节事件，不需要再从过程事件推导最终态。

### 6.3 Draft / Streaming State

- 仅存在于当前会话运行时。
- 不要求长期持久化，也不与历史详情做双向同步。

## Relationships

- `ConversationSummary` 1:N `ConversationTurn`
- `ConversationTurn` 1:N `FinalDetailEvent`
- `FinalDetailEvent` 0:N `ArtifactReference`
- `ConversationDetailView` 按 `session_id` 聚合 `ConversationSummary + ConversationTurn + FinalDetailEvent`

## State Transitions

### ConversationTurn Status

- `STREAMING` → `COMPLETED`
- `STREAMING` → `ERROR`
- `STREAMING` → `FORCE_STOPPED`

### FinalDetailEvent Lifecycle

- 运行时流式过程在内存中累积
- 消息结束时投影为最终可见细节项
- 最终细节项一次性写入 `ai_agent_message_event`
- 历史读取只消费最终细节项，不再回放过程事件

## Old Data Strategy

- 旧历史数据与新模型不兼容时，允许直接删除。
- 不设计双路径读取、历史迁移脚本或回填逻辑。
