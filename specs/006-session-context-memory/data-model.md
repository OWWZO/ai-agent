# Data Model: ReAct / PlanSolve 完整链路会话上下文复原

## 1. AgentConversation（既有）

**Purpose**: 会话主档，提供 `sessionId`、模式归属与历史重开入口。

| Field | Type | Notes |
|------|------|-------|
| `id` | `BIGINT` | 主键 |
| `session_id` | `VARCHAR(64)` | 单会话唯一标识，working memory rebuild 的根键 |
| `device_id` | `VARCHAR(128)` | 会话归属校验 |
| `agent_type` | `TINYINT` | `0=CHAT,1=PLAN_SOLVE,2=REACT` |
| `message_count` | `INT` | 轮次统计 |
| `last_message_preview` | `VARCHAR(200)` | 列表展示摘要 |

**Invariants**

- 同一 `session_id` 一旦绑定 `REACT` 或 `PLAN_SOLVE`，后续续聊必须保持一致
- 本期 transcript 恢复仅在 `agent_type != CHAT` 时启用

## 2. AgentMessage（既有）

**Purpose**: 单轮账本，是 turn 级恢复、模式守卫和并发守卫的基础来源。

| Field | Type | Notes |
|------|------|-------|
| `id` | `BIGINT` | 主键 |
| `conversation_id` | `BIGINT` | 关联 `AgentConversation` |
| `request_id` | `VARCHAR(64)` | 单轮唯一请求 ID |
| `sort_order` | `INT` | 会话内轮次顺序 |
| `query` | `TEXT` | 用户问题 |
| `files_json` | `JSON` | 本轮上传文件 |
| `agent_type` | `TINYINT` | 本轮模式，需与会话模式一致 |
| `response` | `MEDIUMTEXT` | 最终回答 |
| `metrics_json` | `JSON` | 执行指标 |
| `status` | `TINYINT` | `0=STREAMING,1=COMPLETED,2=ERROR,3=FORCE_STOPPED` |
| `force_stop` | `TINYINT(1)` | 是否强制停止 |
| `started_at` / `finished_at` | `DATETIME` | 生命周期 |

**Eligibility Rules**

- `status = COMPLETED`：允许进入直接回放窗口
- `status = STREAMING`：作为 `session_busy` 守卫依据
- `status = ERROR / FORCE_STOPPED`：只保留历史展示，不进入新的 working memory

## 3. AgentMessageEvent（既有）

**Purpose**: 最终可见事件账本。本期从“展示附属数据”升级为“运行时 transcript 恢复源”。

| Field | Type | Notes |
|------|------|-------|
| `id` | `BIGINT` | 主键 |
| `message_id` | `BIGINT` | 所属 `AgentMessage` |
| `seq_no` | `INT` | 单轮最终事件顺序 |
| `event_type` | `VARCHAR(32)` | 事件主类型 |
| `event_sub_type` | `VARCHAR(32)` | 子类型 |
| `display_area` | `VARCHAR(32)` | 展示区域 |
| `task_id` / `task_order` | `VARCHAR(64)` / `INT` | 任务链定位 |
| `title` | `VARCHAR(256)` | 展示标题 |
| `content_text` | `MEDIUMTEXT` | 展示/预览文本 |
| `payload_json` | `JSON` | 规范化后的 canonical payload |
| `status` | `VARCHAR(16)` | `completed/partial/error` |

**Runtime Reuse Rules**

- working memory rebuild 需要读取所选 turn 的完整 final events，而不是只读取带 `artifactRefs` 的事件
- `payload_json` 继续统一走 `ConversationEventPayloadNormalizer` 规范化
- `content_text` 作为 payload 缺失时的回退文本来源
- `seq_no` 是 turn 内恢复顺序的唯一依据

## 4. AgentSessionMemorySnapshot（既有，读取语义不变）

**Purpose**: 已压缩历史的边界和摘要来源，本期不改其 schema、写入策略和 compaction 规则。

| Field | Type | Notes |
|------|------|-------|
| `session_id` | `VARCHAR(64)` | 单会话唯一快照键 |
| `summary_text` | `MEDIUMTEXT` | 已压缩历史摘要 |
| `facts_json` | `JSON` | 结构化事实 |
| `artifact_refs_json` | `JSON` | 已归档稳定文件/产物引用 |
| `boundary_message_id` | `BIGINT` | 已压缩的最后一条消息 |
| `boundary_sort_order` | `INT` | 已压缩的最后一轮顺序 |
| `source_turn_count` | `INT` | 摘要覆盖轮次数 |

**Phase Scope Rules**

- 本期只把它当作 `summary + facts + restored artifacts + replay boundary`
- 对 `boundary_sort_order` 之前的历史，不要求回溯恢复完整 event chain

## 5. TranscriptContextBlock（新增运行时模型，非持久化）

**Purpose**: 从 turn ledger + event ledger 恢复出的最小上下文单元。

| Field | Type | Notes |
|------|------|-------|
| `blockType` | `ENUM` | `USER_INPUT / ASSISTANT_THOUGHT / ASSISTANT_ANSWER / TOOL_USE / TOOL_RESULT / ARTIFACT_REFERENCE` |
| `sourceMessageId` | `Long` | 来源 turn |
| `sourceSeqNo` | `Integer` | 来源 event 顺序；用户输入/最终回答可为空 |
| `role` | `String` | `user / assistant / tool` |
| `text` | `String` | 文本内容或结果预览 |
| `toolUseId` | `String` | tool call 唯一标识；`TOOL_USE / TOOL_RESULT` 可用 |
| `toolName` | `String` | 工具名 |
| `toolArgumentsJson` | `String` | 关键入参 |
| `resultPayloadJson` | `String` | 结构化结果摘要 |
| `artifactRefs` | `List<JSONObject>` | 稳定文件/产物引用 |
| `referenceOnly` | `Boolean` | 是否仅保留引用，不回灌正文 |

**Normalization Rules**

- `USER_INPUT` 由 `AgentMessage.query` 和 `files_json` 派生
- `ASSISTANT_ANSWER` 默认来自 `AgentMessage.response`
- `ASSISTANT_THOUGHT` / `TOOL_USE` / `TOOL_RESULT` / `ARTIFACT_REFERENCE` 优先来自 `AgentMessageEvent`
- 对长报告正文、超长 `stdout/stderr`、大 `diff` 等，`referenceOnly = true`

## 5A. Payload Mapping & Tool Correlation Contract

**Purpose**: 在“不改表结构”的前提下，明确 `payload_json` 如何稳定恢复 block 语义和 `tool_use -> tool_result` 关联。

| Runtime Field | Source Priority | Notes |
|------|-----------------|-------|
| `toolUseId` | `payload_json.toolUseId` → `payload_json.toolCall.id` → `payload_json.tool.id` → `messageId:seqNo` | 缺失时使用确定性 fallback，保证同轮内可引用 |
| `toolName` | `payload_json.toolName` → `payload_json.toolCall.function.name` → `event_sub_type` → `event_type` | 统一为运行时展示/注入使用的工具名 |
| `toolArgumentsJson` | `payload_json.toolArguments` → `payload_json.toolCall.function.arguments` → `payload_json.arguments` | 保留关键入参，不要求回灌所有冗余字段 |
| `resultPayloadJson` | `payload_json.result` → `payload_json.summary` → `payload_json` | 长输出仅保留结构化摘要或结果骨架 |
| `artifactRefs` | `payload_json.artifactRefs[]` | 必须先经过 `ConversationEventPayloadNormalizer` 规范化 |
| `referenceOnly` | `payload_json.referenceOnly` → 长输出策略判定 | `deepsearch report`、超长命令输出、大 `diff` 默认置为 `true` |

**Correlation Rules**

- `TOOL_RESULT` 优先按同一 `toolUseId` 与 `TOOL_USE` 配对
- 若结果事件缺少 `toolUseId`，则在同一 `messageId` 内按 `task_id/task_order/toolName` 匹配最近一个未闭合的 `TOOL_USE`
- 若仍无法匹配，则保留顺序语义并回退到该结果事件自己的 fallback `toolUseId`
- 同一工具多次调用时，必须依赖 `toolUseId + seqNo` 保留逐次调用关系，不能按 `toolName` 合并

## 6. SessionTurnMemory（增强后的运行时 turn 视图，非持久化）

**Purpose**: 表示当前直接回放窗口中的一轮上下文，不再退化成 `userMessage + assistantMessage`。

| Field | Type | Notes |
|------|------|-------|
| `messageId` | `Long` | 对应 `AgentMessage.id` |
| `requestId` | `String` | 对应 `AgentMessage.request_id` |
| `sortOrder` | `Integer` | 轮次顺序 |
| `blocks` | `List<TranscriptContextBlock>` | 当前轮的有序上下文块 |
| `artifactRefs` | `List<JSONObject>` | 从 turn/files/event 聚合出的稳定引用 |
| `finalAnswer` | `String` | 兼容性字段，可由 `ASSISTANT_ANSWER` 反推出 |

**Build Rules**

- block 顺序遵循：`USER_INPUT -> event seq order -> ASSISTANT_ANSWER`
- 若该轮无 event，则退化为 `USER_INPUT + ASSISTANT_ANSWER`
- 若 `response` 为空但 event 足够表达完成语义，可仅保留 event chain

## 7. PreloadedContextMessage / AgentRequest.Message（内部传输视图）

**Purpose**: 从 `SessionTurnMemory.blocks` 派生出来，进入 `AgentRequest.Message` 和 Agent `Memory.messages` 的中间表示。本期采用“结构化扩展 `AgentRequest.Message`”方案，而不是把 transcript 整体压成 markdown 文本。

| Field | Type | Notes |
|------|------|-------|
| `role` | `String` | `user / assistant / tool` |
| `content` | `String` | 文本内容 |
| `messageType` | `String` | `user_input / assistant_thought / assistant_answer / tool_use / tool_result / artifact_reference` |
| `toolCalls` | `List<ToolCall>` | 对应 `TOOL_USE` |
| `toolCallId` | `String` | 对应 `TOOL_RESULT` |
| `files` | `List<FileInformation>` | 需要以内联文件形式提示时使用 |
| `artifactRefs` | `List<JSONObject>` | 供运行时复用稳定引用 |
| `referenceOnly` | `Boolean` | 是否只保留摘要/引用 |

**Mapping Rules**

| Transcript Block | Preloaded Message |
|------------------|-------------------|
| `USER_INPUT` | `role=user, messageType=user_input, content=query/约束文本` |
| `ASSISTANT_THOUGHT` | `role=assistant, messageType=assistant_thought, content=thought text` |
| `TOOL_USE` | `role=assistant, messageType=tool_use, content=thought or call preview, toolCalls=[...]` |
| `TOOL_RESULT` | `role=tool, messageType=tool_result, toolCallId=... , content=result preview or reference text, referenceOnly=...` |
| `ASSISTANT_ANSWER` | `role=assistant, messageType=assistant_answer, content=final answer` |
| `ARTIFACT_REFERENCE` | `messageType=artifact_reference`，视情况转为 `files` 或 assistant/tool 的引用文本，同时仍注入 `sessionFiles` |

## 8. SessionWorkingMemory（现有聚合的增强版，非持久化）

**Purpose**: 每次新请求前装配出的工作上下文，统一向 `history_dialogue`、preloaded messages、stable files 三条注入链供给数据。

| Field | Type | Notes |
|------|------|-------|
| `conversationId` | `Long` | 当前会话 |
| `sessionId` | `String` | 当前 session |
| `agentType` | `Integer` | `REACT / PLAN_SOLVE` |
| `summaryText` | `String` | 已压缩历史摘要 |
| `facts` | `List<SessionMemoryFact>` | 已压缩历史事实 |
| `recentTurns` | `List<SessionTurnMemory>` | 当前直接回放窗口 |
| `restoredFiles` | `List<FileInformation>` | 稳定文件/产物 |
| `historyDialogue` | `String` | prompt 注入文本 |
| `boundarySortOrder` | `Integer` | 已压缩边界 |
| `estimatedTokens` | `Integer` | 当前 working memory 估算体量 |
| `needsCompaction` | `Boolean` | 继续沿用现有 compaction 判定，不在本期改变含义 |

**Assembly Order**

1. 读取 `AgentSessionMemorySnapshot`
2. 查询边界之后的最近 `COMPLETED` 消息
3. 按 messageId 批量查询完整 final events
4. 恢复 `SessionTurnMemory.blocks`
5. 聚合 `restoredFiles`
6. 生成 `historyDialogue`
7. 派生 `PreloadedContextMessage` 列表供 `AgentStreamPersistServiceImpl` 注入

## Relationships

```text
AgentConversation
  └── AgentMessage (COMPLETED turns after boundary)
        └── AgentMessageEvent (ordered final events)
               └── TranscriptContextBlock
                      └── SessionTurnMemory
                             └── SessionWorkingMemory
                                    ├── historyDialogue
                                    ├── preloaded messages
                                    └── restoredFiles

AgentSessionMemorySnapshot
  └── provides summary/facts/boundary for SessionWorkingMemory
```

## Backward Compatibility Rules

- 无 `AgentMessageEvent` 的旧 turn：退化为 `query + response`
- 只有旧 `fileInfo/fileList` 的 payload：经 `ConversationEventPayloadNormalizer` 转为 `artifactRefs[]`
- 已被 `ai_agent_session_memory` 覆盖的 turn：继续通过 `summaryText/facts` 表达，不回溯展开
