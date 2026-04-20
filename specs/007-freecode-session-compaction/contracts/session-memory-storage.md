# Contract: Session Memory Storage Versioning

## Scope

本契约描述 `ai_agent_session_memory` 从单行 upsert 演进为 append-only 版本快照后的存储规则、查询规则和兼容要求。

## Storage Sources

| Table | Role |
|------|------|
| `ai_agent_conversation` | 会话主档 |
| `ai_agent_message` | completed turn 输入源 |
| `ai_agent_message_event` | rich transcript 输入源 |
| `ai_agent_session_memory` | 结构化 session memory version store |

## Schema Evolution

### Required DDL Changes

- 移除 `uk_session_id(session_id)` 唯一键
- 新增最新版本查询索引，例如：
  - `idx_session_latest(session_id, deleted, id)`
  - `idx_conversation_history(conversation_id, deleted, id)`

> 若实现阶段为了查询计划稳定需要把 `boundary_sort_order` 纳入索引，可作为等价优化，但核心语义不变。

## Persistent Semantics

### Write

- 每次 compaction 成功：
  - `INSERT` 一条新的 snapshot version
- 不再使用 `ON DUPLICATE KEY UPDATE`
- 旧 snapshot 默认保持 `deleted = 0`，供分析查询使用

### Read

#### Latest Snapshot

```sql
SELECT *
FROM ai_agent_session_memory
WHERE session_id = #{sessionId}
  AND deleted = 0
ORDER BY id DESC
LIMIT 1;
```

#### Snapshot History

```sql
SELECT *
FROM ai_agent_session_memory
WHERE session_id = #{sessionId}
ORDER BY id DESC;
```

## Field Semantics

| Field | Meaning After This Feature |
|------|-----------------------------|
| `summary_text` | 结构化 markdown session memory 主体 |
| `facts_json` | 兼容性 facts 投影/分析索引 |
| `artifact_refs_json` | 结构化 memory 覆盖区间内的稳定引用 |
| `boundary_message_id` | 本版 snapshot 覆盖到的最后消息 |
| `boundary_sort_order` | 本版 snapshot 覆盖到的最后轮次 |
| `source_turn_count` | 本版 snapshot 覆盖的 turn 数 |
| `last_compacted_at` | 本版 compaction 时间 |

## Runtime Rules

- working memory rebuild 永远只读取最新有效 version
- 历史分析与排障允许读取全量 versions
- 旧 versions 不参与默认请求装配

## Compatibility Rules

- 老数据只有一行 snapshot 时，`latest` 查询结果与旧语义一致
- 老数据中 `summary_text` 仍为旧版流水账文本时，运行时应继续兼容，不要求一次性迁移
- 软删除能力保留，但不用于正常“让旧版本失效”；默认通过“读最新”隔离旧版本

## DAO / Mapper Implications

- `IAgentSessionMemoryDao` 需要从：
  - `queryBySessionId`
  - `upsert`

  演进为：
  - `queryLatestBySessionId`
  - `queryHistoryBySessionId`
  - `insert`
- 旧 `upsert` 语义应从运行时主路径移除

## Analysis Use Cases

历史 snapshot versions 可用于：

- 分析不同 compaction 轮次的 memory 演进
- 对比某次请求前后的结构化记忆差异
- 排查模型生成 memory 的退化或遗漏
