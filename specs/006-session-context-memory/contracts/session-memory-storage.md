# Contract: Session Memory Storage

## Scope

本契约描述 working memory rebuild 所依赖的存储来源、读取顺序和兼容要求。重点是“怎么读现有账本”，不是“怎么改表”。

## Storage Sources

| Table | Role In This Phase |
|------|---------------------|
| `ai_agent_conversation` | 会话主档、模式归属、`sessionId` 入口 |
| `ai_agent_message` | turn 级账本、`COMPLETED` 窗口来源、`STREAMING` 并发守卫来源 |
| `ai_agent_message_event` | final event ledger，提供思考、工具链、工具结果、artifact 引用 |
| `ai_agent_session_memory` | 已压缩历史的摘要、facts、artifact refs 和 replay boundary |

## Schema Policy

- 本期 **不新增** 表、列、索引
- 本期 **不修改** `ai_agent_session_memory` 的写入语义
- 本期 **不改变** `ai_agent_message_event` 的持久化模型，仍保持“一条最终可见块一条记录”

## Read Contract

### 1. Snapshot Read

按 `session_id` 查询 `ai_agent_session_memory`：

```text
summary_text
facts_json
artifact_refs_json
boundary_message_id
boundary_sort_order
```

**Usage**

- `summary_text / facts_json` 进入 `historyDialogue`
- `artifact_refs_json` 参与 `restoredFiles`
- `boundary_sort_order` 约束直接回放窗口

### 2. Turn Ledger Read

按 `conversation_id + boundary_sort_order` 查询最近 N 轮 `status = COMPLETED` 的 `ai_agent_message`：

```text
id
request_id
sort_order
query
files_json
response
status
```

**Rules**

- 仅 `status = COMPLETED` 可进入当前直接回放窗口
- `ERROR / FORCE_STOPPED` 不参与 rebuild
- `STREAMING` 只用于 `session_busy` 守卫

### 3. Event Ledger Read

按 selected `message_id` 批量查询完整 final events：

```text
message_id
seq_no
event_type
event_sub_type
content_text
payload_json
status
```

**Required Query Semantics**

- 必须返回所选 turn 的完整 final events，而不是仅返回带 `artifactRefs` 的事件
- 返回顺序必须为 `message_id ASC, seq_no ASC`
- `deleted = 0` 是基础过滤条件
- rebuild 路径优先消费 `payload_json`，`content_text` 作为 fallback

## Write Contract

本期不改写持久化链路，但要求继续满足以下语义：

- `AgentMessageEventServiceImpl.persistEvents(...)` 仍以 final visible blocks 覆盖写入 `ai_agent_message_event`
- `ConversationEventPayloadNormalizer` 仍负责把 legacy `fileInfo/fileList` 收敛到 canonical `artifactRefs[]`
- turn 结束时：
  - `COMPLETED`：照旧落 turn + events，并按现有逻辑决定是否刷新 snapshot
  - `ERROR / FORCE_STOPPED`：照旧落 turn + events，但不刷新 snapshot

## Field-Level Source Mapping

| Runtime Need | Source Field |
|--------------|--------------|
| 会话模式守卫 | `ai_agent_conversation.agent_type` |
| 并发守卫 | `ai_agent_message.status` |
| 用户输入 | `ai_agent_message.query` |
| 当前轮上传文件 | `ai_agent_message.files_json` |
| 最终回答 | `ai_agent_message.response` |
| 思考文本 / 工具结果预览 | `ai_agent_message_event.content_text` |
| 工具参数 / 结果 payload / message identity | `ai_agent_message_event.payload_json` |
| 稳定产物引用 | `ai_agent_message_event.payload_json.artifactRefs[]` + `ai_agent_session_memory.artifact_refs_json` |
| 已压缩历史摘要 | `ai_agent_session_memory.summary_text` |
| 已压缩历史事实 | `ai_agent_session_memory.facts_json` |
| 直接回放边界 | `ai_agent_session_memory.boundary_sort_order` |

## Compatibility Rules

- 旧 turn 无 events：允许退化为 `query + response`
- 旧 event 只有 legacy 文件字段：允许通过 normalizer 转为 `artifactRefs[]`
- 已被 snapshot 覆盖的旧历史：继续通过 `summary_text / facts_json` 表达
- 历史详情接口继续读相同的 `ai_agent_message_event`，不新增 replay 专用存储

## Mapper / DAO Implications

- `IAgentMessageDao` 现有 completed / streaming 查询能力继续复用
- `IAgentMessageEventDao` 需要补齐“按 messageIds 读取完整 final events”的查询能力
- `ai_agent_message_event_mapper.xml` 需要把 rebuild 查询从 artifact-only 语义调整为 transcript rebuild 语义
