## ADDED Requirements

### Requirement: Session transcript memory SHALL use canonical block-only turns
系统必须把 `SessionTurnMemory.blocks` 作为单轮 transcript 的唯一事实表示。续聊 working memory 组装、会话记忆压缩、历史 transcript 恢复都必须以 `blocks` 和 `artifactRefs` 为输入，不得再依赖 `userMessage`、`assistantMessage`、`finalAnswer` 等旧版扁平字段。

#### Scenario: Working memory is rebuilt from canonical blocks
- **WHEN** 系统从近期消息恢复 session working memory
- **THEN** 系统必须只消费 turn 的 `blocks`、`artifactRefs` 以及定位字段
- **THEN** 系统不得再尝试从旧版扁平 transcript 字段拼接用户输入、助手思考或最终回答

#### Scenario: Compaction output preserves only canonical turn fields
- **WHEN** 系统执行会话记忆压缩并生成新的 turn memory 摘要
- **THEN** 压缩结果中的 turn 数据必须只包含 `messageId`、`requestId`、`sortOrder`、`blocks`、`artifactRefs`
- **THEN** 系统不得再在压缩结果中生成或保留旧版扁平 transcript 字段

### Requirement: Session memory snapshots SHALL persist simplified boundary facts
系统必须将 `ai_agent_session_memory` 收敛为当前真实使用的会话记忆快照模型。快照边界必须以 `boundary_sort_order` 作为定位依据，不得继续依赖 `boundary_message_id`；旧兼容 `facts_json` 也不得再参与存储、读取或恢复逻辑。

#### Scenario: Snapshot persistence writes only active boundary fields
- **WHEN** 系统保存或更新一条会话记忆压缩快照
- **THEN** 快照必须使用 `boundary_sort_order` 标识压缩边界
- **THEN** 系统不得再写入 `facts_json` 或 `boundary_message_id`

#### Scenario: Snapshot restore no longer reads removed compatibility fields
- **WHEN** 系统读取会话记忆快照以恢复续聊上下文
- **THEN** 系统必须仅基于当前快照字段恢复记忆摘要和压缩边界
- **THEN** 系统不得因为缺失 `facts_json` 或 `boundary_message_id` 而执行 fallback 解析

### Requirement: Stream persistence SHALL separate execution, projection, and commit stages
系统必须把流式持久化链路拆分为独立的执行、投影和提交阶段。HTTP 请求构建与 SSE 逐行读取必须由专职执行组件处理，`AgentResponse` 到 `OrderedEvent` 的转换必须由专职投影组件处理，最终消息与事件落库必须由专职持久化协调组件处理；总协调服务不得再次承担这些细分职责的完整实现。

#### Scenario: Stream execution is handled by a dedicated executor
- **WHEN** 系统发起一次新的 Agent 流式请求
- **THEN** HTTP 请求构建、异步调用、SSE 逐行消费和回调分发必须由独立的流执行组件负责
- **THEN** 总协调服务必须只负责编排执行组件并处理会话级上下文

#### Scenario: Event projection and persistence are independently testable
- **WHEN** 系统接收到标准 `AgentResponse` 并准备写入 turn 事件
- **THEN** 事件投影必须能够在不依赖真实 HTTP/SSE 连接的前提下独立转换为有序事件列表
- **THEN** 持久化协调必须能够在不依赖事件投影细节的前提下独立提交消息、事件和会话状态

### Requirement: Event normalization and artifact restore SHALL consume only standardized structures
系统在标准化事件 payload、恢复 transcript blocks 或恢复产物引用时，必须只接受标准单层结构和标准字段名，不得继续兼容 legacy 多层 `resultMap`、`fileInfo` / `fileList` 结构，或 `fileName|name`、`ossUrl|downloadUrl|url` 等字段别名。

#### Scenario: Normalizer handles only standard event payload shapes
- **WHEN** 系统标准化一条来自内部协议的事件 payload
- **THEN** 系统必须直接按标准字段路径读取数据
- **THEN** 系统不得再遍历多层 `resultMap` 或尝试解析 legacy 文件列表结构

#### Scenario: Artifact restore uses canonical field names only
- **WHEN** 系统从事件事实恢复产物引用或 transcript 中的文件块
- **THEN** 系统必须只读取标准字段名对应的文件标识、展示名、预览地址和下载地址
- **THEN** 系统不得再为旧字段别名提供恢复兼容逻辑

### Requirement: Generated file cache SHALL remain derived and non-authoritative
系统可以继续维护 `ai_agent_message.generated_files_json` 作为 turn 级只读缓存，但该缓存必须由本轮事件聚合后单向派生，且不得作为 transcript 恢复、历史事实恢复或产物引用恢复的兜底事实源。

#### Scenario: Generated files cache is derived from finalized event facts
- **WHEN** 一轮流式对话结束并进入统一持久化阶段
- **THEN** 系统必须基于本轮已确定的事件产物引用聚合 `generated_files_json`
- **THEN** 系统不得独立于事件账本生成另一套文件事实

#### Scenario: History and memory restore do not fall back to generated file cache
- **WHEN** 系统重建历史详情或 session transcript blocks
- **THEN** 系统必须优先使用事件标准字段和产物引用恢复结果
- **THEN** 即使 `generated_files_json` 存在，系统也不得把它作为缺失事件事实时的兜底输入
