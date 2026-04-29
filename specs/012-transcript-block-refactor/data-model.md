# Data Model: TranscriptBlock 会话记忆重写

## 1. Conversation Summary

对应表：`ai_agent_conversation`

本期不重写会话摘要表，只继续承担会话归属、标题、模式、列表排序和轮次数统计。

| 字段 | 含义 | 说明 |
|------|------|------|
| `session_id` | 会话唯一标识 | 新旧链路切换后仍是唯一会话范围键 |
| `agent_type` | 会话模式 | `CHAT / PLAN_SOLVE / REACT` |
| `message_count` | 轮次数 | 列表摘要字段 |
| `last_message_preview` | 最新摘要 | 列表展示使用 |
| `pinned` | 是否置顶 | 列表排序使用 |

## 2. Turn Ledger

对应表：`ai_agent_turn`

### 2.1 职责

- 表达一轮完整请求的元数据
- 维护会话内顺序和终态
- 作为 transcript blocks、display events 和 snapshot boundary 的父节点

### 2.2 关键字段

| 字段 | 含义 | 说明 |
|------|------|------|
| `id` | turn 主键 | 内部关联键 |
| `conversation_id` | 所属会话 | FK -> `ai_agent_conversation.id` |
| `request_id` | 单轮请求标识 | 会话外也唯一 |
| `sort_order` | 会话内顺序 | `conversation_id + sort_order` 唯一 |
| `query` | 用户输入 | 当前轮问题文本 |
| `status` | 终态 | `0=STREAMING,1=COMPLETED,2=ERROR,3=STOPPED` |
| `started_at` | 开始时间 | 流式开始 |
| `finished_at` | 结束时间 | 流式结束 |
| `create_time` | 创建时间 | 审计用途 |
| `deleted` | 软删除 | 逻辑删除标记 |

### 2.3 状态流转

`STREAMING -> COMPLETED | ERROR | STOPPED`

规则：

- 只有 `COMPLETED` 的 turn 可以进入 snapshot 和续聊上下文
- `ERROR / STOPPED` turn 只保留历史展示价值，不参与后续记忆重建

## 3. Transcript Block Ledger

对应表：`ai_agent_transcript_block`

### 3.1 职责

- 保存单轮内部有序发生的语义事实块
- 作为续聊上下文和压缩输入的唯一细粒度事实来源
- 不再承载前端展示协议

### 3.2 关键字段

| 字段 | 含义 | 说明 |
|------|------|------|
| `id` | block 主键 | 内部关联键 |
| `turn_id` | 所属 turn | FK -> `ai_agent_turn.id` |
| `seq_no` | 轮内顺序 | `turn_id + seq_no` 唯一 |
| `block_type` | 固定块类型 | 仅允许 6 种标准类型 |
| `role` | 语义角色 | 如 `user` / `assistant` / `tool` |
| `text` | 摘要文本 | 用户输入、思考、结果、最终回答等可读文本 |
| `tool_use_id` | 工具调用标识 | 用于关联 `TOOL_USE` 与 `TOOL_RESULT` |
| `tool_name` | 工具名称 | 工具调用语义 |
| `tool_arguments` | 工具参数 | 结构化 JSON |
| `result_payload` | 结果载荷 | 结构化 JSON |
| `artifact_refs` | 稳定产物引用 | 轻量文件/资源元数据 |
| `create_time` | 创建时间 | 审计用途 |
| `deleted` | 软删除 | 逻辑删除标记 |

### 3.3 block_type 枚举

| 值 | 语义 |
|----|------|
| `USER_INPUT` | 用户输入块 |
| `ASSISTANT_THOUGHT` | 助手思考块 |
| `TOOL_USE` | 工具调用块 |
| `TOOL_RESULT` | 工具结果块 |
| `ARTIFACT_REFERENCE` | 产物引用块 |
| `ASSISTANT_ANSWER` | 最终回答块 |

### 3.4 约束规则

- 不允许动态扩散新的 block 类型
- `TOOL_RESULT` 必须通过 `tool_use_id` 关联到对应工具调用
- 大体量正文不直接进入 `text` 或 `result_payload`，只保留摘要和稳定引用
- 上传文件和生成产物都通过 `artifact_refs` 进入语义块，不再单独依赖旧消息表字段

## 4. Display Event Read Model

对应表：`ai_agent_display_event`

### 4.1 职责

- 保存面向前端历史详情的直接可读模型
- 与 transcript facts 同事务同步双写
- 不反向参与续聊上下文构建

### 4.2 关键字段

| 字段 | 含义 | 说明 |
|------|------|------|
| `id` | display event 主键 | 内部关联键 |
| `turn_id` | 所属 turn | FK -> `ai_agent_turn.id` |
| `seq_no` | 轮内展示顺序 | `turn_id + seq_no` 唯一 |
| `display_type` | 展示类型 | UI 直接消费 |
| `task_id` | 任务标识 | 可选 |
| `task_name` | 任务名称 | 可选 |
| `task_order` | 任务顺序 | 可选 |
| `title` | 显示标题 | 直接展示 |
| `content_text` | 展示文本 | 直接展示 |
| `content_json` | 结构化内容 | 表格、图表、复杂结果 |
| `tool_use_id` | 工具调用标识 | 与工具事件对齐 |
| `tool_name` | 工具名称 | 工具事件对齐 |
| `tool_arguments_json` | 工具参数 | UI 可直接查看 |
| `artifact_refs_json` | 稳定文件引用 | UI 文件预览与下载 |
| `result_payload_json` | 结果载荷 | UI 结构化结果 |
| `status` | 展示状态 | `pending/running/completed/error` |
| `display_area` | 展示区域 | `timeline/sidebar` |
| `display_props_json` | 组件附加属性 | 最小 UI 元数据 |
| `create_time` | 创建时间 | 审计用途 |
| `deleted` | 软删除 | 逻辑删除标记 |

### 4.3 display_type 枚举

| 值 | 语义 |
|----|------|
| `user_message` | 用户输入 |
| `thought` | 思考过程 |
| `tool_call` | 工具调用 |
| `tool_result` | 工具结果 |
| `artifact` | 产物引用 |
| `final_answer` | 最终回答 |

### 4.4 映射规则

- `DisplayEvent` 来自 `TranscriptBlock` 的同步投影
- UI 历史详情直接消费 `display_type + content_* + artifact_refs_json + result_payload_json`
- 不再要求 UI 通过旧 payload 组合算法恢复显示状态

## 5. Session Memory Snapshot

对应表：`ai_agent_session_memory`

### 5.1 职责

- 保存某个会话在某次压缩后的摘要快照
- 通过 boundary 把“已压缩历史”和“仍需直接读取的 turns”分开
- 支持同一会话的多版本快照回溯

### 5.2 关键字段

| 字段 | 含义 | 说明 |
|------|------|------|
| `id` | snapshot 版本主键 | 版本顺序依据 |
| `conversation_id` | 所属会话 | FK -> `ai_agent_conversation.id` |
| `session_id` | 会话 ID | 同一会话允许多版本 |
| `boundary_sort_order` | 已覆盖的最后 turn 顺序 | snapshot 边界 |
| `summary_text` | 压缩摘要 | 工作记忆摘要文本 |
| `artifact_refs` | 快照级稳定引用 | 被压缩历史保留下来的产物引用 |
| `source_turn_count` | 已覆盖 turn 数 | 统计和校验使用 |
| `last_compacted_at` | 压缩时间 | 审计用途 |
| `create_time` | 创建时间 | 版本时间戳 |
| `deleted` | 软删除 | 逻辑删除标记 |

### 5.3 版本规则

- 同一 `session_id` 可以存在多条快照
- 运行时只读取最新有效版本
- 压缩失败不得生成新的 snapshot 版本

## 6. Working Context Window

不是数据库表，而是请求前动态构建的运行时模型。

### 6.1 输入

- 最新有效 `Session Memory Snapshot`
- `boundary_sort_order` 之后的 `Turn Ledger`
- 对应 turns 的 `Transcript Block Ledger`

### 6.2 输出

| 字段 | 含义 |
|------|------|
| `summaryText` | 快照摘要 |
| `recentTurns` | 最近未压缩 turn 列表 |
| `formattedHistoryDialogue` | 直接注入 prompt 的格式化文本 |
| `messages` | 结构化上下文消息 |
| `sessionFiles` | 当前可复用文件/产物引用 |
| `estimatedTokens` | 当前工作记忆估算量 |

### 6.3 关键规则

- `formattedHistoryDialogue` 与 `messages` 来自同一份 turn/block 事实
- 最近窗口至少保留配置指定的最小消息量，且保持工具调用因果顺序
- 压缩只覆盖最近窗口之前的已完成 turns

## 7. Relationships

```text
Conversation (1)
  └─ Turn Ledger (N)
       ├─ Transcript Block Ledger (N)
       └─ Display Event Read Model (N)

Conversation (1)
  └─ Session Memory Snapshot (N versions)
```

说明：

- `Turn Ledger` 是 transcript facts 和 display events 的共同父节点
- `Session Memory Snapshot` 不复制 display events，只服务续聊和压缩
- 历史详情查询只读 `Conversation + Turn Ledger + Display Event Read Model`
- 续聊和压缩只读 `Conversation + Session Memory Snapshot + Turn Ledger + Transcript Block Ledger`
