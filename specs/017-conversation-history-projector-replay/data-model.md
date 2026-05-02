# Data Model: Conversation History Projector Replay

## 1. Dialogue Session

对应表：`ai_agent_dialogue_session`（新增）

### 1.1 职责

- 表达一个稳定的会话容器
- 为系统范围近期会话列表提供轻量摘要
- 为会话详情提供稳定的会话级统计、状态和排序锚点

### 1.2 关键字段

| 字段 | 含义 | 说明 |
|------|------|------|
| `id` | 主键 | 内部关联键 |
| `session_id` | 会话唯一标识 | 与前端、执行链统一复用 |
| `title` | 会话标题 | 用于列表与详情标题 |
| `status` | 会话当前状态 | 由最近一轮或会话统计归一得出 |
| `latest_request_id` | 最近一次请求 ID | 详情恢复与调试锚点 |
| `latest_query_text` | 最近一次提问预览 | 列表展示字段 |
| `latest_summary_text` | 最近一轮总结 | 详情兜底与后续扩展使用，不在列表预览中暴露 |
| `run_count` | 会话请求总数 | 列表与详情共用 |
| `finished_run_count` | 成功轮次数 | 列表与详情共用 |
| `failed_run_count` | 失败轮次数 | 包含失败与必要时的超时/终止统计 |
| `started_at` | 首轮开始时间 | 会话生命周期锚点 |
| `last_active_at` | 最近活动时间 | 默认排序字段 |
| `create_time` | 创建时间 | 审计用途 |
| `update_time` | 更新时间 | 审计用途 |
| `deleted` | 软删除标记 | 与既有账本保持一致 |

### 1.3 约束规则

- `session_id + deleted` 唯一
- 默认列表查询按 `last_active_at DESC, id DESC`
- 列表仅展示 `title + latest_query_text + latest status/活动时间/轮次统计`，不展示 `latest_summary_text`

## 2. Dialogue Run

对应表：`ai_agent_dialogue_run`（既有）

### 2.1 职责

- 表达会话中的一次独立请求
- 作为历史恢复的最小业务轮次单元
- 负责携带 run 级状态、提问、总结和时间锚点

### 2.2 与会话的关系

- 一个 `Dialogue Session` 对应多条 `Dialogue Run`
- `Dialogue Session` 维护摘要与排序；`Dialogue Run` 维护细节与事实恢复入口

### 2.3 关键恢复字段

| 字段 | 含义 | 说明 |
|------|------|------|
| `request_id` | 单轮请求 ID | 详情与查询入口 |
| `session_id` | 所属会话 ID | 会话聚合键 |
| `status` | 轮次终态 | `0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT,4=STOPPED` |
| `query_text` | 用户问题 | 详情与回放上下文 |
| `final_summary_text` | 最终总结 | 当无显式最终回答时的结果兜底 |
| `started_at` / `finished_at` | 开始/结束时间 | 顺序与耗时展示 |
| `llm_call_count` / `tool_call_count` / `artifact_count` | 聚合统计 | 详情摘要辅助信息 |

### 2.4 顺序规则

- 会话详情中 runs 必须按 `create_time ASC, id ASC` 返回
- 列表中的“最近活动”只看会话主表，不从 run 临时推导

## 3. Execution Run Detail

对应运行时聚合模型：`ExecutionRunDetail`

### 3.1 职责

- 组装单个 run 的完整账本事实
- 作为 `ReplayFactBundle` 的输入来源

### 3.2 包含内容

| 字段 | 含义 |
|------|------|
| `run` | `DialogueRunView` |
| `llmInvocations` | 当前 run 的 LLM 调用序列 |
| `toolInvocations` | 当前 run 的工具调用序列，已回填 `structuredOutput` |
| `artifacts` | 当前 run 的输入/输出产物引用 |

## 4. Replay Fact Bundle

对应运行时模型：`ReplayFactBundle`（扩展）

### 4.1 职责

- 承接单个 run 的全部历史回放事实
- 为 `ReplayProjector` 提供统一输入

### 4.2 字段

| 字段 | 含义 | 说明 |
|------|------|------|
| `run` | `DialogueRunView` | 用于终态、fallback 与元数据补充 |
| `llmInvocations` | `List<LlmInvocationView>` | 参与 `agent_name` 语义映射 |
| `toolInvocations` | `List<ToolInvocationView>` | rich tool structured output 回放入口 |
| `artifacts` | `List<ArtifactView>` | 文件/报告等稳定引用 |

### 4.3 关键规则

- `llmInvocations` 必须按 `invocationSeq` 升序
- `toolInvocations` 必须按 `dispatchIndex` 和真实账本顺序稳定回放
- 当 `ReplayProjector` 未产出显式最终 `result` 事件时，允许根据 `run.finalSummaryText` 合成兜底事件

## 5. Session Summary

对应列表契约：`ConversationSessionRespVO` / `ConversationSessionItem`

### 5.1 职责

- 为首页或调试入口提供近期会话摘要列表
- 与会话详情共享同一统计和状态来源

### 5.2 字段

| 字段 | 含义 |
|------|------|
| `sessionId` |
| `title` |
| `latestQueryText` |
| `status` |
| `runCount` |
| `finishedRunCount` |
| `failedRunCount` |
| `lastActiveAt` |
| `startedAt` |

### 5.3 展示规则

- 默认返回最近 20 条
- 默认按 `lastActiveAt` 倒序
- 未打开详情前不返回总结正文

## 6. Session Detail

对应详情契约：`ConversationHistoryDetailRespVO` / `ConversationHistoryDetail`

### 6.1 职责

- 表达一个会话的完整多轮详情
- 供前端恢复到现有 `ConversationHistory` 结构

### 6.2 字段

| 字段 | 含义 |
|------|------|
| `sessionId` |
| `title` |
| `status` |
| `productType` / `outputStyle` |
| `deepThink` |
| `role` / `roleAgentId` |
| `runCount` |
| `finishedRunCount` |
| `failedRunCount` |
| `startedAt` |
| `lastActiveAt` |
| `runs` |

### 6.3 `runs[*]` 字段

| 字段 | 含义 |
|------|------|
| `requestId` |
| `status` |
| `queryText` |
| `finalSummaryText` |
| `startedAt` / `finishedAt` |
| `replayFrames` |

## 7. Replay Frame

对应详情子模型：`ProjectedReplayEvent` / `GptProcessResult` 风格包装

### 7.1 职责

- 表达某个 run 中最终仍需展示的历史事件
- 保持与现有 SSE `eventData` 足够同构，便于前端复用现有恢复链

### 7.2 关键字段

| 字段 | 含义 |
|------|------|
| `reqId` | 请求 ID |
| `status` | `running/success` 风格流式状态或历史终态包装 |
| `finished` | 当前 frame 是否是终态片段 |
| `resultMap.agentType` | 当前 agent 类型 |
| `resultMap.eventData` | 现有前端 `combineData` 直接消费的事件 |

### 7.3 事件语义

- `plan_thought`
- `plan`
- `task`，其中 `resultMap.messageType` 继续细分为：
  - `tool_thought`
  - `tool_result`
  - `deep_search`
  - `markdown`
  - `html`
  - `file`
  - `data_analysis`
  - `result`

## 8. Frontend Hydrated Conversation

对应前端运行时模型：`CHAT.ConversationHistory`

### 8.1 职责

- 承接当前 `sessionId` 对应的完整历史恢复结果
- 与实时聊天共享同一套 `ChatView / Dialogue / ActionView` 渲染链

### 8.2 关键映射

| 历史详情字段 | 前端会话字段 |
|--------------|--------------|
| `detail.sessionId` | `conversation.sessionId` |
| `detail.title` | `conversation.title` / `chatTitle` |
| `detail.outputStyle` | `conversation.productType` |
| `detail.deepThink` | `conversation.deepThink` |
| `detail.role` | `conversation.role` |
| `detail.runs[*].replayFrames` | `conversation.chatList[*]` |

### 8.3 恢复规则

- 遍历每个 run 的 `replayFrames`，逐条调用 `combineData`
- 若 frame 中有 `result` / `task_summary`，恢复到底部结论区
- 若当前 `sessionId` 无历史，保持当前空白或初始态，不自动切换到其他会话

## 9. Relationships

```text
Dialogue Session (1)
  └─ Dialogue Run (N)
       ├─ Llm Invocation (N)
       ├─ Tool Invocation (N)
       │    └─ Tool Output * (0..1 per invocation)
       └─ Artifact Record (N)

Dialogue Run Detail (runtime aggregate)
  └─ Replay Fact Bundle
       └─ Replay Frames
            └─ Frontend Hydrated Conversation
```

说明：

- `Dialogue Session` 是列表与详情共享的会话头
- `Dialogue Run` 是历史恢复的业务轮次
- `ReplayFactBundle` 不持久化，只作为 projector 输入
- 前端恢复结果不是新的事实源，只是后端 replay frames 的界面态还原
