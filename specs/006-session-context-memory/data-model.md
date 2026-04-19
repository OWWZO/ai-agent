# Data Model: ReAct / PlanSolve 会话上下文记忆

## 1. AgentConversation（既有）

**Purpose**: 会话主档，提供 `sessionId`、模式归属、设备作用域与列表展示信息。

| Field | Type | Notes |
|------|------|-------|
| `id` | `BIGINT` | 主键 |
| `session_id` | `VARCHAR(64)` | 会话唯一标识，单会话记忆归属键 |
| `device_id` | `VARCHAR(128)` | 会话作用域校验 |
| `agent_type` | `TINYINT` | `0=CHAT,1=PLAN_SOLVE,2=REACT`，本次用于模式锁定 |
| `product_type` | `VARCHAR(32)` | 产品形态 |
| `message_count` | `INT` | 轮次冗余统计 |
| `last_message_preview` | `VARCHAR(200)` | 列表展示摘要 |

**New Invariants**

- 同一 `session_id` 的 `agent_type` 一旦是 `REACT` 或 `PLAN_SOLVE`，后续续聊必须保持一致。
- 会话记忆快照与消息账本都必须以 `session_id` 和 `conversation_id` 对齐到同一行会话主档。

## 2. AgentMessage（既有）

**Purpose**: 每轮消息账本，是最近详细窗口、并发守卫、摘要候选输入的主要来源。

| Field | Type | Notes |
|------|------|-------|
| `id` | `BIGINT` | 主键 |
| `conversation_id` | `BIGINT` | 关联 `AgentConversation` |
| `request_id` | `VARCHAR(64)` | 单轮请求唯一 ID |
| `sort_order` | `INT` | 会话内轮次顺序 |
| `query` | `TEXT` | 用户问题 |
| `files_json` | `JSON` | 当前轮上传文件 |
| `agent_type` | `TINYINT` | 本轮模式，需与会话模式一致 |
| `response` | `MEDIUMTEXT` | 单轮最终回答 |
| `metrics_json` | `JSON` | 指标信息 |
| `status` | `TINYINT` | `0=STREAMING,1=COMPLETED,2=ERROR,3=FORCE_STOPPED` |
| `force_stop` | `TINYINT(1)` | 是否强制停止 |
| `started_at` / `finished_at` | `DATETIME` | 轮次生命周期 |

**Memory Eligibility Rules**

- 仅 `status=COMPLETED` 的消息允许进入：
  - 最近详细窗口
  - 摘要压缩输入
  - 结构化事实提炼
- `ERROR / FORCE_STOPPED` 只保留历史展示价值，不进入会话记忆。
- 同一会话若存在 `status=STREAMING` 的消息，新的 `/send-stream` 续聊请求必须被拒绝。

**State Transitions**

```text
STREAMING -> COMPLETED      => 可进入会话记忆
STREAMING -> ERROR          => 不进入会话记忆
STREAMING -> FORCE_STOPPED  => 不进入会话记忆
```

## 3. AgentMessageEvent（既有）

**Purpose**: 最终可见细节块快照表，提供稳定 `artifactRefs` 与历史细节内容，是文件/产物恢复的来源。

| Field | Type | Notes |
|------|------|-------|
| `id` | `BIGINT` | 主键 |
| `message_id` | `BIGINT` | 所属消息轮次 |
| `seq_no` | `INT` | 最终展示顺序 |
| `event_type` / `event_sub_type` | `VARCHAR` | 事件类型 |
| `display_area` | `VARCHAR(32)` | 展示区域 |
| `title` | `VARCHAR(256)` | 展示标题 |
| `content_text` | `MEDIUMTEXT` | 展示摘要文本 |
| `payload_json` | `JSON` | 规范化后的最终可见 payload，包含 `artifactRefs[]` |
| `status` | `VARCHAR(16)` | `completed/partial/error` |

**Memory Reuse Rules**

- 只从规范化后的 `payload_json.artifactRefs[]` 恢复稳定文件/产物引用。
- 不把事件块整体作为工作记忆消息回灌，只提取：
  - 稳定文件引用
  - 必要的结构化事实
  - 摘要生成素材

## 4. AgentSessionMemorySnapshot（新增）

**Purpose**: 每个 `sessionId` 当前唯一生效的摘要快照，等价于 free-code 中“compact summary + boundary”的数据库版。

**Suggested Table Name**: `ai_agent_session_memory`

| Field | Type | Notes |
|------|------|-------|
| `id` | `BIGINT` | 主键 |
| `conversation_id` | `BIGINT` | 关联会话主档 |
| `session_id` | `VARCHAR(64)` | 唯一键，单会话单快照 |
| `agent_type` | `TINYINT` | 会话模式快照，需与 conversation 一致 |
| `summary_text` | `MEDIUMTEXT` | 当前生效的压缩摘要文本 |
| `facts_json` | `JSON` | 结构化事实，如目标、约束、结论、待续状态 |
| `artifact_refs_json` | `JSON` | 已归档阶段仍需恢复的稳定文件/产物引用 |
| `boundary_message_id` | `BIGINT` | 已被摘要吸收的最后一条消息 ID |
| `boundary_sort_order` | `INT` | 已被摘要吸收的最后一轮顺序 |
| `source_turn_count` | `INT` | 本快照覆盖了多少已完成轮次 |
| `last_compacted_at` | `DATETIME` | 最近一次压缩时间 |
| `create_time` / `update_time` | `DATETIME` | 审计字段 |
| `deleted` | `TINYINT(1)` | 软删除 |

**Invariants**

- `session_id` 全局唯一，每个会话只有一条生效快照。
- `boundary_sort_order` 只能单调前进，不能回退。
- `agent_type` 必须与 `ai_agent_conversation.agent_type` 一致。
- `summary_text` 不为空时，`source_turn_count` 必须大于 0。

**State Transitions**

```text
ABSENT -> ACTIVE        第一次压缩后创建
ACTIVE -> REFRESHED     后续压缩原地更新摘要与边界
ACTIVE -> SOFT_DELETED  会话删除时跟随软删除
```

## 5. SessionWorkingMemory（请求级视图，非持久化）

**Purpose**: 每次新请求开始前从数据库重建的工作上下文，等价于本项目里的“请求级 mutableMessages 视图”。

| Field | Type | Notes |
|------|------|-------|
| `sessionId` | `String` | 当前会话 |
| `agentType` | `Integer` | `REACT / PLAN_SOLVE` |
| `summaryText` | `String` | 已压缩历史摘要 |
| `facts` | `Map<String, Object>` | 结构化事实 |
| `recentTurns` | `List<SessionTurnMemory>` | 最近详细窗口 |
| `restoredFiles` | `List<File>` | 恢复后的会话级文件上下文，注入 `AgentContext.productFiles` |
| `historyDialogue` | `String` | 注入 `{{history_dialogue}}` 的提示词文本 |
| `boundarySortOrder` | `Integer` | 快照覆盖边界 |
| `estimatedTokens` | `Integer` | 估算后的记忆载荷大小 |
| `needsCompaction` | `Boolean` | 当前请求结束后是否需要压缩 |

**Build Rules**

- 先读 `AgentSessionMemorySnapshot`
- 再读边界之后最近若干轮 `COMPLETED` 消息
- 最后批量读这些轮次的 `AgentMessageEvent`，恢复 `artifactRefs`
- 如果无快照，则退化为“最近窗口 + 文件恢复”

## 6. SessionTurnMemory（请求级视图，非持久化）

**Purpose**: 最近详细窗口中的一轮高价值消息。

| Field | Type | Notes |
|------|------|-------|
| `requestId` | `String` | 原消息轮次 |
| `sortOrder` | `Integer` | 顺序 |
| `userMessage` | `String` | `query` |
| `assistantMessage` | `String` | `response` |
| `artifactRefs` | `List<Map<String, Object>>` | 本轮稳定产物引用 |

**Normalization Rules**

- 只保留 user/assistant 级别的完整语义，不回灌细粒度 tool message。
- `assistantMessage` 为空时，该轮不进入详细窗口。

## 7. RestoredSessionFile（恢复视图，非持久化）

**Purpose**: 将 `artifactRefs[]` 映射回 Agent 运行时可消费的 `File` 对象。

| Field | Type | Notes |
|------|------|-------|
| `fileName` | `String` | 展示名 |
| `ossUrl` / `domainUrl` | `String` | 可下载 / 可预览地址 |
| `fileSize` | `Long` | 文件大小 |
| `description` | `String` | 说明 |
| `isInternalFile` | `Boolean` | 会话恢复文件默认作为可见历史产物，不标记内部文件 |

**Rules**

- 仅恢复具备稳定 URL 或资源键的文件。
- 缺失态文件不注入 `productFiles`，但仍可在摘要或历史详情中保留说明。
