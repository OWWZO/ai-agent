# Data Model: 后端事实对话账本重构

## 1. Conversation Summary

对应表：`ai_agent_conversation`

| 字段 | 含义 | 说明 |
|------|------|------|
| `session_id` | 会话唯一标识 | 前端/后端共享的会话范围主键 |
| `title` | 会话标题 | 历史列表展示 |
| `agent_type` | 会话模式 | `CHAT / PLAN_SOLVE / REACT` |
| `message_count` | 轮次数 | 列表摘要字段 |
| `last_message_preview` | 最新摘要 | 列表展示使用 |
| `pinned` | 是否置顶 | 列表排序使用 |
| `update_time` | 最近更新时间 | 列表排序使用 |

本次不改变其职责：它始终只承载列表和归属信息。

## 2. Conversation Turn Ledger

对应表：`ai_agent_message`

### 2.1 职责

- 保存一轮用户请求的主账本
- 保存上传文件摘要和本轮生成文件摘要
- 保存最终回答、状态、指标和时间信息
- 作为历史详情与会话记忆恢复的 turn 级父节点

### 2.2 关键字段

| 字段 | 含义 | 说明 |
|------|------|------|
| `request_id` | 单轮请求标识 | 一轮唯一 |
| `sort_order` | 轮次顺序 | 会话内有序 |
| `query` | 用户输入 | 用户事实 |
| `files_json` | 上传文件摘要 | 仅表示用户上传文件 |
| `generated_files_json` | 本轮生成文件摘要 | 仅表示本轮执行产物，便于直接查询 |
| `response` | 最终回答 | 最终助手回答真相源 |
| `status` | 执行终态 | `STREAMING / COMPLETED / ERROR / FORCE_STOPPED` |
| `metrics_json` | 执行指标 | 非核心事实，但保留必要派生信息 |
| `started_at / finished_at` | 时间信息 | 历史展示和排查使用 |

### 2.3 Generated File Summary 结构

`generated_files_json` 结构复用轻量文件摘要，建议字段如下：

| 字段 | 含义 |
|------|------|
| `fileName` | 展示名称 |
| `fileType` | 类型，如 `html` / `markdown` / `ppt` |
| `resourceKey` | 稳定资源标识 |
| `domainUrl` / `previewUrl` | 预览入口 |
| `ossUrl` / `downloadUrl` | 下载入口 |
| `fileSize` | 文件大小 |
| `mimeType` | MIME 类型 |
| `missing` | 是否不可访问 |
| `missingReason` | 缺失原因 |

## 3. Turn Fact Block Ledger

对应表：`ai_agent_message_event`

### 3.1 职责

- 保存单轮内部有序发生的后端事实块
- 既服务于历史详情投影，也服务于会话记忆恢复
- 不再作为前端最终态快照直接存储

### 3.2 建议语义

| 列 | 新语义 |
|----|--------|
| `message_id` | 所属轮次 |
| `seq_no` | 事实块顺序 |
| `event_type` | 事实块主类型 |
| `event_sub_type` | 事实块细分来源 |
| `display_area` | 最小展示区域提示 |
| `task_id / task_order` | 原始任务链关联 |
| `title / content_text` | 可读摘要和回退文案 |
| `payload_json` | 事实数据 + 资源引用 + 必要渲染元数据 |
| `status` | 此块所属轮次的终态快照 |

### 3.3 事实块主类型

| `event_type` | 语义 |
|--------------|------|
| `assistant_thought` | 助手思考/推理文本 |
| `plan_snapshot` | 计划或任务快照 |
| `tool_use` | 工具调用事实 |
| `tool_result` | 工具结果事实 |
| `artifact_reference` | 产物引用事实 |

### 3.4 细分来源示例

| `event_sub_type` | 语义 |
|------------------|------|
| `plan` | 顶层计划 |
| `task` | 任务执行块 |
| `deep_search.search` | 深度搜索中的检索事实 |
| `deep_search.report` | 深度搜索中的总结/报告事实 |
| `html` / `markdown` / `ppt` / `code` | 文件型结果来源 |
| `browser` | 浏览行为结果 |
| `knowledge` | 知识库结果 |
| `data_analysis` | 数据分析结果 |

### 3.5 payload_json 原则

- 保存事实本身，而不是前端事件快照
- 可包含：
  - `blockId`
  - `toolUseId`
  - `toolName`
  - `toolArguments`
  - `summary`
  - `referenceOnly`
  - `artifactRefs`
  - 与计划、任务、搜索结果相关的最小结构化字段
- 不长期保存：
  - HTML/Markdown/PPT 正文全文
  - 只用于前端本地状态合并的字段
  - 可以稳定引用回取的大体量文件内容

## 4. Artifact Reference

Artifact Reference 既可能出现在 `generated_files_json` 中，也可能出现在事件块 `payload_json.artifactRefs` 中。

### 4.1 必要字段

| 字段 | 含义 |
|------|------|
| `artifactType` | 产物类型 |
| `displayName` | 展示名称 |
| `resourceKey` | 稳定资源标识 |
| `previewUrl` | 预览入口 |
| `downloadUrl` | 下载入口 |
| `fileSize` | 大小 |
| `mimeType` | MIME 类型 |
| `missing` | 是否丢失 |
| `missingReason` | 丢失原因 |

### 4.2 归属规则

- 轮次级常用摘要进入 `generated_files_json`
- 与具体事实块强关联的引用继续保留在对应 `payload_json.artifactRefs`
- 两者都只保存轻量元数据，不保存正文

## 5. History Projection

History Projection 不是数据库实体，而是后端读取账本后的派生模型。

### 5.1 输入

- `ai_agent_message`
- `ai_agent_message_event`

### 5.2 输出

- `ConversationTurnDetail`
- `ConversationEventDetail.payload`

### 5.3 规则

- turn 级字段直接来自 `ai_agent_message`
- event 级字段来自事实块账本
- `payload` 由后端把事实块投影成实时路径可消费的 canonical payload
- `generatedFiles` 直接来自 `generated_files_json`，不再从 event payload 二次提取

## 6. Session Memory Materialization

### 6.1 输入

- `query`
- `response`
- `files_json`
- `generated_files_json`
- 事实块账本

### 6.2 输出

- `TranscriptContextBlock` 列表
- `historyDialogue`
- `sessionFiles`

### 6.3 关键规则

- `query` 生成 `USER_INPUT`
- `response` 生成 `ASSISTANT_ANSWER`
- `files_json` / `generated_files_json` 转为稳定文件引用
- `ai_agent_message_event` 直接生成 `ASSISTANT_THOUGHT / TOOL_USE / TOOL_RESULT / ARTIFACT_REFERENCE` 等 block
- 不再依赖从 UI 快照 payload 中猜测 transcript 语义
