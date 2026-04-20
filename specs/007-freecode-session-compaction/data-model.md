# Data Model: 对齐 free-code 的请求前会话压缩

## 1. AgentConversation（既有）

**Purpose**: 会话主档，提供 `sessionId`、模式归属和重开入口。

| Field | Type | Notes |
|------|------|-------|
| `id` | `BIGINT` | 主键 |
| `session_id` | `VARCHAR(64)` | 会话唯一标识 |
| `agent_type` | `TINYINT` | `0=CHAT,1=PLAN_SOLVE,2=REACT` |
| `message_count` | `INT` | 历史轮次数 |
| `last_message_preview` | `VARCHAR(200)` | 列表预览 |

**Invariants**

- 同一 `session_id` 的 `REACT / PLAN_SOLVE` 模式归属必须稳定
- 本期请求前 compaction 只对 `agent_type != CHAT` 启用

## 2. AgentMessage（既有）

**Purpose**: 单轮账本，是 compaction 输入源、并发守卫和最近窗口恢复的基础来源。

| Field | Type | Notes |
|------|------|-------|
| `id` | `BIGINT` | 主键 |
| `conversation_id` | `BIGINT` | 所属会话 |
| `request_id` | `VARCHAR(64)` | 单轮请求标识 |
| `sort_order` | `INT` | 会话内顺序 |
| `query` | `TEXT` | 用户输入 |
| `files_json` | `JSON` | 本轮文件 |
| `response` | `MEDIUMTEXT` | 最终回答 |
| `status` | `TINYINT` | `STREAMING / COMPLETED / ERROR / FORCE_STOPPED` |
| `started_at / finished_at` | `DATETIME` | 生命周期 |

**Eligibility Rules**

- 只有 `COMPLETED` 轮次允许进入 compaction 输入与最近窗口
- `STREAMING` 轮次只参与 `session_busy` 守卫
- `ERROR / FORCE_STOPPED` 轮次保留历史展示，但不进入新的结构化 memory

## 3. AgentMessageEvent（既有）

**Purpose**: 最终事件账本，为 rich transcript、工具链完整性和最近窗口裁剪提供细粒度语义。

| Field | Type | Notes |
|------|------|-------|
| `id` | `BIGINT` | 主键 |
| `message_id` | `BIGINT` | 所属 `AgentMessage` |
| `seq_no` | `INT` | 单轮事件顺序 |
| `event_type` | `VARCHAR(32)` | 主类型 |
| `event_sub_type` | `VARCHAR(32)` | 子类型 |
| `content_text` | `MEDIUMTEXT` | 可见文本 |
| `payload_json` | `JSON` | 规范化结构化载荷 |
| `status` | `VARCHAR(16)` | `completed / partial / error` |

**Runtime Reuse Rules**

- 请求前 compaction 和 rebuild 读取完整 final events，而不是仅 artifact 事件
- `payload_json` 继续通过 `ConversationEventPayloadNormalizer` 统一 legacy 兼容
- 最近窗口裁剪必须尊重 `tool_use / tool_result` 和同一逻辑响应片段的关联关系

## 4. AgentSessionMemorySnapshotVersion（演进后的持久化模型）

**Purpose**: 追加式版本快照，保存某次请求前 compaction 成功后生成的完整结构化会话记忆。

| Field | Type | Notes |
|------|------|-------|
| `id` | `BIGINT` | 自增主键，同时作为版本顺序主依据 |
| `conversation_id` | `BIGINT` | 所属会话 |
| `session_id` | `VARCHAR(64)` | 会话标识；不再唯一 |
| `agent_type` | `TINYINT` | 会话模式 |
| `summary_text` | `MEDIUMTEXT` | 结构化 markdown session memory |
| `facts_json` | `JSON` | 兼容性 facts 投影/分析索引 |
| `artifact_refs_json` | `JSON` | 稳定文件/产物引用 |
| `boundary_message_id` | `BIGINT` | 本版快照覆盖到的最后消息 |
| `boundary_sort_order` | `INT` | 本版快照覆盖到的最后轮次 |
| `source_turn_count` | `INT` | 本版快照覆盖的 turn 数 |
| `last_compacted_at` | `DATETIME` | compaction 发生时间 |
| `create_time` | `DATETIME` | 插入时间 |
| `deleted` | `TINYINT(1)` | 软删除标记，运行时默认 `0` |

**Storage Rules**

- 每次 compaction 成功都插入一条新记录，不覆盖旧版本
- 运行时按 `session_id + deleted=0 + id desc limit 1` 读取最新有效版本
- 历史分析可按 `session_id` 或 `conversation_id` 查看所有版本
- `summary_text` 是主记忆载体，`facts_json` 不再是主摘要来源

## 5. StructuredSessionMemoryDocument（持久化在 `summary_text` 中）

**Purpose**: free-code 风格的结构化会话记忆文档，是 append-only snapshot 的主内容。

| Section | Purpose |
|---------|---------|
| `Session Title` | 当前会话的高密度标题 |
| `Current State` | 当前正在做什么、下一步是什么 |
| `Task specification` | 用户任务说明和关键边界 |
| `Files and Functions` | 关键文件、产物和相关函数/模块 |
| `Workflow` | 重要执行流程、常用命令和顺序 |
| `Errors & Corrections` | 错误、失败尝试与修正 |
| `Codebase and System Documentation` | 关键系统知识和架构上下文 |
| `Learnings` | 有效做法、应避免做法 |
| `Key results` | 交付结果和关键结论 |
| `Worklog` | 最近关键操作轨迹 |

**Generation Rules**

- 每次 compaction 时由模型基于上一版 document + 新增 completed transcript 直接重写或更新
- 超长原始输出不直接进入正文，应转为关键结果和稳定引用
- `Current State` 与 `Key results` 必须优先保持最新

## 6. SessionMemoryCompactionDecision（新增运行时模型，非持久化）

**Purpose**: 表达请求前 preflight 的判定结果。

| Field | Type | Notes |
|------|------|-------|
| `decisionType` | `ENUM` | `BYPASS / COMPACTED / DEGRADED_CONTINUE / REJECTED / SKIPPED_CIRCUIT_OPEN` |
| `estimatedTokens` | `Integer` | 原始 working memory 估算体量 |
| `thresholdTokens` | `Integer` | 主动压缩阈值 |
| `hardLimitTokens` | `Integer` | 拒绝上限 |
| `failureCount` | `Integer` | 当前 session 连续失败次数 |
| `reason` | `String` | 判定原因 |
| `snapshotVersionId` | `Long` | 若 compaction 成功，指向新版本快照 |

**State Transitions**

```text
BYPASS -> COMPACTED -> BYPASS
BYPASS -> DEGRADED_CONTINUE
BYPASS -> REJECTED
REJECTED/DEGRADED_CONTINUE -> SKIPPED_CIRCUIT_OPEN (after repeated failures)
COMPACTED -> failureCount reset to 0
```

## 7. PreservedRecentWindow（新增运行时视图，非持久化）

**Purpose**: 在最新 snapshot 之外仍保留的原始上下文窗口。

| Field | Type | Notes |
|------|------|-------|
| `messages` | `List<AgentMessage>` | 保留的最近 completed turns |
| `events` | `Map<Long, List<AgentMessageEvent>>` | 对应完整 final events |
| `tokenBudget` | `Integer` | 主预算 |
| `minMessageCount` | `Integer` | 最小真实消息窗口 |
| `estimatedTokens` | `Integer` | 当前窗口估算体量 |
| `integrityPreserved` | `Boolean` | 是否满足工具链/片段不拆断约束 |

**Selection Rules**

- 以 token 预算为主裁剪
- 至少保留最小真实消息窗口
- 不拆断 `tool_use / tool_result`
- 不拆断同一逻辑响应的关键片段

## 8. CompactionGuardrailState（新增运行时状态，非持久化）

**Purpose**: 记录某个 `sessionId` 的 compaction 失败节流信息。

| Field | Type | Notes |
|------|------|-------|
| `sessionId` | `String` | 会话标识 |
| `consecutiveFailures` | `Integer` | 连续失败次数 |
| `lastFailureAt` | `LocalDateTime` | 最近失败时间 |
| `circuitOpen` | `Boolean` | 是否暂停主动压缩尝试 |

**Rules**

- 成功 compaction 后重置失败计数
- 达到上限后在一定窗口内停止重复尝试
- 该状态属于运行态，不作为持久事实存储

## 9. SessionWorkingMemory（增强版运行时聚合）

**Purpose**: 请求前 preflight 完成后返回给主执行链的最终工作记忆。

| Field | Type | Notes |
|------|------|-------|
| `conversationId` | `Long` | 当前会话 |
| `sessionId` | `String` | 当前 session |
| `agentType` | `Integer` | 当前模式 |
| `summaryText` | `String` | 最新结构化 session memory |
| `facts` | `List<SessionMemoryFact>` | 兼容性 facts |
| `recentTurns` | `List<SessionTurnMemory>` | 保留的最近原始窗口 |
| `restoredFiles` | `List<FileInformation>` | 稳定文件/产物 |
| `historyDialogue` | `String` | 注入 prompt 的文本 |
| `boundarySortOrder` | `Integer` | 最新 snapshot 边界 |
| `estimatedTokens` | `Integer` | working memory 总体量 |
| `needsCompaction` | `Boolean` | 是否超过主动阈值 |

**Assembly Order**

1. 读取最新 snapshot version
2. 读取边界之后的 completed turns 和 full final events
3. 估算真实 working memory 体量
4. 必要时生成新 structured memory 并插入新 snapshot
5. 依据 token 预算选择最近窗口
6. 生成最终 `historyDialogue`、preloaded messages 和 `sessionFiles`

## Relationships

```text
AgentConversation
  └── AgentMessage (completed turns)
        └── AgentMessageEvent (full final events)
               └── PreservedRecentWindow

AgentSessionMemorySnapshotVersion
  └── StructuredSessionMemoryDocument(summary_text)
  └── facts_json / artifact_refs_json

SessionMemoryCompactionDecision
  ├── may create new AgentSessionMemorySnapshotVersion
  └── returns SessionWorkingMemory
```

## Backward Compatibility Rules

- 老数据只有单条 snapshot：最新版本查询仍可正常返回
- 老 snapshot 仍是旧摘要文本：运行时允许直接注入，不强制立刻重写
- 无 `AgentMessageEvent` 的旧 turn：最近窗口退化为 `query + response`
- `facts_json` 即使为空或旧格式，也不应阻塞最新 snapshot 使用
