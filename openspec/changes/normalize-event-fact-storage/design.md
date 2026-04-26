## Context

当前对话历史链路已经完成了“后端事实优先”的第一轮重构，但 `ai_agent_message_event.payload_json` 仍然承载了过多主语义字段，包括 `thoughtType`、`snapshotType`、`sourceType/sourceSubType`、`toolUseId`、`toolName`、`toolArguments`、`summary`、`artifactRefs`、`messageId` 等。结果是：

- 事件主语义仍然隐藏在 JSON 内，数据库列没有真正成为事实模型。
- 历史回显和会话记忆恢复仍需大量解析 payload，写入端与读取端耦合过深。
- `generated_files_json` 仍通过扫描 event payload 反提 `artifactRefs` 构建，turn 级文件摘要依赖 event 内部结构。
- `event_sub_type` 还没有承载稳定的细分语义，导致 subtype 事实在列和 payload 间重复。

这次变更的约束已经明确：

- MySQL 只保存后端真实产出的消息、事件、文件引用和必要渲染元数据。
- 前端实时展示效果不变；历史回显复用“后端发给前端”的同一渲染契约。
- 不兼容旧历史数据，允许发布前清空旧数据，不做回填迁移。
- 不新增独立文件表，继续保留 `ai_agent_message.generated_files_json` 作为 turn 级生成文件索引。

受影响的核心链路包括：

- 写入端：`AgentStreamPersistServiceImpl`
- 事件持久化：`AgentMessageEvent` / DAO / Mapper XML
- 历史回放：`ConversationReplayAssembler`
- 会话记忆恢复：`SessionTranscriptBlockAssembler`

## Goals / Non-Goals

**Goals:**

- 让 `ai_agent_message.response` 成为 turn 最终回答的唯一真相源。
- 让 `ai_agent_message_event` 变成“列优先”的事件事实账本，主语义不再依赖 payload。
- 让 `event_sub_type` 承担真实细分语义，消除 `thoughtType`、`snapshotType` 等重复字段。
- 让 `payload_json` 只作为扩展字段容器，默认允许为 `NULL`。
- 让历史回显和会话记忆恢复从同一套后端事实模型重建，保持与实时渲染链路一致。
- 让 `generated_files_json` 从标准化事件列派生，而不是从 payload 内部结构反推。

**Non-Goals:**

- 不改变前端的最终展示效果与组件树契约。
- 不新增独立文件表，也不把文件正文写回数据库。
- 不兼容旧事件 payload 结构，不做在线 backfill。
- 不在本次引入新的外部文件存储方案或新的文件上传协议。

## Decisions

### 1. 采用列优先的事件事实模型，`payload_json` 退化为扩展槽位

`ai_agent_message_event` 保留现有通用列，并新增以下标准化列：

| 列名 | 作用 |
|------|------|
| `tool_use_id` | 工具调用实例标识，用于把 `tool_use` 和后续 `tool_result` 关联起来 |
| `tool_name` | 工具名称，避免历史读取再从 payload 猜测 |
| `tool_arguments_json` | 工具参数快照，供历史回显和 transcript 恢复 |
| `reference_only` | 标记结果是否仅为引用/文件产物，不需要内联正文 |
| `artifact_refs_json` | 标准化产物引用数组，存稳定资源标识与预览/下载元数据 |
| `structured_data_json` | 无法拆成单列、但属于后端事实本身的结构化数据，如计划快照、标准结果对象 |
| `payload_json` | 仅存扩展字段；默认 `NULL`，禁止继续承载主语义 |

保留的已有列职责：

- `event_type`：事实主类型
- `event_sub_type`：事实细分语义
- `display_area`：最小展示区域提示
- `task_id / task_order`：任务链归属
- `title / content_text`：人类可读标题与摘要
- `status`：该事件落库时对应的 turn 终态

选择该方案而不是“继续把主信息塞进 payload”的原因：

- 用户目标是后端事实模型干净，而不是仅做 payload 瘦身。
- 如果 `toolUseId`、`artifactRefs`、`toolArguments`、`resultData` 仍留在 payload，读写端依旧会把 JSON 当主模型，设计目标无法达成。

备选方案：

- 方案 A：只删除少量重复字段，保留 payload 作为主承载。拒绝原因：只是“更少脏”，不是真正标准化。
- 方案 B：只新增 `tool_use_id / tool_name / artifact_refs_json / reference_only`。拒绝原因：`toolArguments` 和 `resultData` 仍会长期滞留 payload。

### 2. 把 `event_sub_type` 语义做实，移除 payload 内嵌 subtype

采用统一的事件语义字典：

| `event_type` | `event_sub_type` 语义 |
|--------------|-----------------------|
| `assistant_thought` | `plan` / `tool` |
| `plan_snapshot` | `plan` / `task` |
| `tool_use` | 工具域，如 `deep_search` / `browser` / `knowledge` / `data_analysis` / `file_generation` |
| `tool_result` | 结果域，如 `deep_search.search` / `deep_search.report` / `browser.result` / `knowledge.answer` / `markdown.report` / `html.page` / `ppt.deck` / `code.bundle` / `file.output` |
| `artifact_reference` | `generated_file` / `uploaded_file` / `task_artifact` |

结果：

- `thoughtType` 不再写入 payload，改由 `event_type=assistant_thought + event_sub_type=plan|tool` 表达。
- `snapshotType` 不再写入 payload，改由 `event_type=plan_snapshot + event_sub_type=plan|task` 表达。
- `sourceType/sourceSubType` 不再写入 payload，改由 `event_type=tool_result + event_sub_type=<canonical subtype>` 表达。

这样做的好处是：

- SQL 查询可以直接按事件子语义过滤，不需要 JSON 解析。
- 历史回放和 transcript 恢复不再依赖 payload 中的 subtype 回填逻辑。
- 新事件类型扩展时，只需维护一份 canonical subtype 映射表。

### 3. 为每类事件定义固定列归属，禁止把已标准化字段重新塞回 payload

目标合同如下：

| 事件类型 | 标准列 | `structured_data_json` | `payload_json` |
|----------|--------|------------------------|----------------|
| `assistant_thought.plan` | `content_text` | `NULL` | `NULL` |
| `assistant_thought.tool` | `content_text`, `tool_use_id`, `tool_name`, `tool_arguments_json` | `NULL` | `NULL` |
| `plan_snapshot.plan` | `title`, `content_text` | 计划快照对象 | `NULL` |
| `plan_snapshot.task` | `task_id`, `task_order`, `title`, `content_text` | 任务快照对象 | `NULL` |
| `tool_use.*` | `task_id`, `task_order`, `tool_use_id`, `tool_name`, `tool_arguments_json`, `title`, `content_text` | `NULL` | `NULL` |
| `tool_result.*` | `task_id`, `task_order`, `tool_use_id`, `tool_name`, `reference_only`, `artifact_refs_json`, `title`, `content_text` | 标准结果对象 | 仅当存在未标准化扩展字段时写入 |
| `artifact_reference.*` | `task_id`, `task_order`, `reference_only`, `artifact_refs_json`, `title`, `content_text` | `NULL` | 仅当存在未标准化扩展字段时写入 |

明确禁止继续进入 `payload_json` 的字段：

- `blockType`
- `thoughtType`
- `snapshotType`
- `sourceType`
- `sourceSubType`
- `summary`
- `messageId`
- `taskId`
- `taskOrder`
- `toolUseId`
- `toolName`
- `toolArguments`
- `artifactRefs`

其中 `messageId` 属于前端渲染相关标识，不是后端事实。历史读取时如果前端仍需要该字段，由读取层按规则临时合成，例如 `history:{message_id}:{seq_no}`，而不是落库。

### 4. `generated_files_json` 继续保留，但改为从标准化事件列派生

`ai_agent_message.generated_files_json` 保留为 turn 级直接查询字段，因为它能高效回答“这一轮生成了哪些文件”。

但它的来源改为：

- 扫描本轮 `ai_agent_message_event.artifact_refs_json`
- 只汇总 `tool_result` 与 `artifact_reference` 中的标准化产物引用
- 通过统一去重键聚合：`resourceKey -> downloadUrl -> previewUrl -> displayName`
- 再投影为 `generated_files_json` 需要的轻量文件摘要

不再允许：

- 从 `payload_json.artifactRefs` 反提
- 从前端回放结构猜测生成文件

这样可以保留 turn 级查询便利性，同时去掉 event payload 与 message 文件摘要之间的隐式耦合。

### 5. 历史回显采用“读取事实 -> 投影实时契约”的统一路径

新的读取原则不是“把数据库里的 payload 原样吐给前端”，而是：

1. 读取 `ai_agent_message` 和 `ai_agent_message_event`
2. 基于标准列、`structured_data_json`、`artifact_refs_json` 还原事件事实
3. 由后端投影出与实时 SSE 相同的前端渲染 payload
4. 前端继续按当前实时链路渲染，无需感知数据库内部结构

落地要求：

- `ConversationReplayAssembler` 负责历史详情投影，但不再把 payload 当主输入。
- `SessionTranscriptBlockAssembler` 负责会话记忆恢复，但主数据来自标准列与结构化列。
- 两个装配器共享同一套事件事实读取辅助能力，避免一份逻辑依赖 `structured_data_json`，另一份逻辑又回退解析 `payload_json`。

这样做优于“直接持久化前端 payload”的原因：

- 实时输出契约可以演进，但数据库仍保持后端事实模型稳定。
- 历史与实时只共享投影层，不共享存储结构，职责更清晰。

### 6. `payload_json` 实施白名单治理

`payload_json` 只允许承载两类信息：

- 当前没有稳定列归属、但历史回显必须恢复的扩展字段
- 短期兼容某些复杂结构所需的过渡字段

约束规则：

- 默认写 `NULL`
- 只有通过单一的 `buildExtensionPayload(...)` 入口才能写入
- 新增字段必须先判断是否可以进入标准列或 `structured_data_json`
- 单元测试应覆盖“绝大部分事件 payload 为空”的目标

这项治理的目的不是“以后完全不用 payload”，而是避免它再次退化成匿名垃圾桶。

## Risks / Trade-offs

- [列数增加，表结构更显式] → 代价是 schema 更宽；缓解方式是把 JSON 承载的主语义全部收回到命名列，换取长期可维护性。
- [事件 subtype 字典需要持续维护] → 代价是新增事件时必须补 canonical 映射；缓解方式是在写入服务集中维护映射函数与测试样例。
- [历史回显与实时输出存在投影偏差风险] → 缓解方式是让历史回放只复用一套 canonical projector，并用回归测试对比实时与历史的关键展示字段。
- [`generated_files_json` 与 event 产物引用存在信息重复] → 这是有意保留的读优化；缓解方式是只允许由事件列单向派生，不允许两边独立写入。
- [回滚到旧代码会无法正确读取新结构] → 因为本次明确不兼容旧数据；缓解方式是发布窗口内清空历史，出现回滚时同步清理新写入会话数据。

## Migration Plan

1. 为 `ai_agent_message_event` 增加 `tool_use_id`、`tool_name`、`tool_arguments_json`、`reference_only`、`artifact_refs_json`、`structured_data_json` 列，并保留 `payload_json`。
2. 更新 `AgentMessageEvent` 实体、DAO、Mapper XML 与建表脚本，完成新列读写映射。
3. 重构 `AgentStreamPersistServiceImpl` 的事件构造逻辑：优先填充标准列，再写 `structured_data_json`，最后按白名单决定 `payload_json` 是否为空。
4. 重构 `buildGeneratedFilesJson(...)`，改为只消费标准化 `artifact_refs_json`。
5. 重构 `ConversationReplayAssembler` 与 `SessionTranscriptBlockAssembler`，改为从标准列和结构化列投影前端 payload / transcript blocks。
6. 删除与旧前端回放快照相关的冗余 fallback 逻辑和无用字段处理。
7. 发布前清空旧历史数据；发布后仅允许新事实模型写入。

回滚策略：

- 应用回滚不是目标路径；若必须回滚，需同时清理新结构写入的历史数据，再恢复旧版本应用。
- 因为本次不做旧新双写，也不做 backfill，整体策略应按一次性切换处理。

## Open Questions

- 当前无阻塞性开放问题。本次设计默认采用一次性切换，不保留旧历史兼容层。
