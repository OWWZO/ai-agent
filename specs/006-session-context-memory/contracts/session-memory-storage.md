# Contract: Session Memory Storage

## Scope

本契约描述会话记忆快照的数据库存储约束，以及它与既有三张会话表的关系。

## New Table

**Table**: `ai_agent_session_memory`

## Suggested Columns

| Column | Type | Constraint | Meaning |
|--------|------|------------|---------|
| `id` | `BIGINT` | PK | 主键 |
| `conversation_id` | `BIGINT` | NOT NULL | 关联会话 |
| `session_id` | `VARCHAR(64)` | UNIQUE NOT NULL | 单会话唯一快照 |
| `agent_type` | `TINYINT` | NOT NULL | 模式快照 |
| `summary_text` | `MEDIUMTEXT` | NOT NULL | 当前生效摘要 |
| `facts_json` | `JSON` | NULL | 结构化事实 |
| `artifact_refs_json` | `JSON` | NULL | 已归档阶段的稳定产物引用 |
| `boundary_message_id` | `BIGINT` | NULL | 已摘要覆盖的最后一条消息 |
| `boundary_sort_order` | `INT` | NULL | 已摘要覆盖的最后一轮顺序 |
| `source_turn_count` | `INT` | NOT NULL DEFAULT 0 | 摘要覆盖轮次数 |
| `last_compacted_at` | `DATETIME` | NOT NULL | 最近压缩时间 |
| `create_time` | `DATETIME` | NOT NULL | 创建时间 |
| `update_time` | `DATETIME` | NOT NULL | 更新时间 |
| `deleted` | `TINYINT(1)` | NOT NULL DEFAULT 0 | 软删除 |

## Storage Rules

### Snapshot Update

- 每个 `sessionId` 最多存在一条 `deleted=0` 的快照
- 每次压缩都更新同一行，而不是新插入版本历史
- `boundary_sort_order` 只能前进，不能后退

### Eligibility

- 仅 `COMPLETED` 轮次可参与：
  - 摘要重算
  - `facts_json` 提炼
  - `artifact_refs_json` 聚合
- `ERROR / FORCE_STOPPED` 不得修改快照

### Artifact Source

- `artifact_refs_json` 的原始来源是 `ai_agent_message_event.payload_json.artifactRefs[]`
- 任何写入快照的 artifact 引用都必须是已规范化后的结构，避免后续再做兼容转换

## Implemented Persistence Strategy

- transcript 真相源仍为：`ai_agent_conversation / ai_agent_message / ai_agent_message_event`
- 当前生效快照为：`ai_agent_session_memory`
- 快照写入方式为 `session_id` 维度 upsert
- `boundary_sort_order` 只根据本次新增归档轮次前进，不从 `ERROR / FORCE_STOPPED` 轮次推进

## Existing Table Cooperation

| Table | Role In Memory Pipeline |
|------|--------------------------|
| `ai_agent_conversation` | 会话主档与模式归属源 |
| `ai_agent_message` | 轮次账本、最近窗口来源、并发守卫来源 |
| `ai_agent_message_event` | 稳定文件/产物引用与最终细节来源 |
| `ai_agent_session_memory` | 当前生效摘要快照 |

## Backward Compatibility

- 老会话在快照缺失时仍可通过最近完成轮退化续聊
- 快照首次生成前，不要求补齐历史回填任务
- 会话列表、会话详情的已有查询契约保持不变
