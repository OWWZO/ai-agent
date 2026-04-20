# Contract: Session Memory Rebuild

## Scope

本契约定义 `REACT / PLAN_SOLVE` 在每次新请求前，如何从既有数据库账本重建 working memory，并把它转成 prompt 摘要、preloaded messages 和 stable files。

## Inputs

```json
{
  "sessionId": "sess-001",
  "requestId": "req-002",
  "agentType": 2,
  "query": "继续补充",
  "filesJson": "[]"
}
```

## Logical Output

逻辑上需要装配出一份 block-aware 的 `SessionWorkingMemory`：

```json
{
  "sessionId": "sess-001",
  "agentType": 2,
  "summaryText": "边界之前的历史摘要",
  "historyDialogue": "历史摘要 + 关键事实 + 稳定文件提示",
  "boundarySortOrder": 6,
  "recentTurns": [
    {
      "requestId": "req-007",
      "sortOrder": 7,
      "blocks": [
        { "blockType": "USER_INPUT", "role": "user", "text": "继续分析 MCP 工具差异" },
        { "blockType": "ASSISTANT_THOUGHT", "role": "assistant", "text": "需要先复用上次搜索结果" },
        {
          "blockType": "TOOL_USE",
          "role": "assistant",
          "toolUseId": "tool-1",
          "toolName": "deep_search",
          "toolArgumentsJson": "{\"query\":\"Spring AI MCP 最新更新\"}"
        },
        {
          "blockType": "TOOL_RESULT",
          "role": "tool",
          "toolUseId": "tool-1",
          "text": "已获取 3 条搜索结果，详见稳定引用",
          "referenceOnly": true
        },
        { "blockType": "ASSISTANT_ANSWER", "role": "assistant", "text": "上一轮最终回答" }
      ]
    }
  ],
  "preloadedMessages": [
    { "role": "user", "messageType": "user_input", "content": "继续分析 MCP 工具差异" },
    { "role": "assistant", "messageType": "assistant_thought", "content": "需要先复用上次搜索结果" },
    {
      "role": "assistant",
      "messageType": "tool_use",
      "content": "准备调用 deep_search",
      "toolCalls": [
        {
          "id": "tool-1",
          "type": "function",
          "function": {
            "name": "deep_search",
            "arguments": "{\"query\":\"Spring AI MCP 最新更新\"}"
          }
        }
      ]
    },
    {
      "role": "tool",
      "messageType": "tool_result",
      "toolCallId": "tool-1",
      "content": "已获取 3 条搜索结果，详见稳定引用",
      "referenceOnly": true
    },
    { "role": "assistant", "messageType": "assistant_answer", "content": "上一轮最终回答" }
  ],
  "restoredFiles": [
    {
      "fileName": "report.html",
      "domainUrl": "https://file.example.com/report.html"
    }
  ]
}
```

## Build Algorithm

| Step | Rule |
|------|------|
| 1 | 先按 `sessionId` 读取 `ai_agent_session_memory`，获取 `summary / facts / artifact refs / boundary_sort_order` |
| 2 | 只查询 `boundary_sort_order` 之后最近 N 轮 `status = COMPLETED` 的 `ai_agent_message` |
| 3 | 按 messageId 批量读取这些 turn 的完整 final events，保持 `message_id ASC, seq_no ASC` |
| 4 | 用 `ConversationEventPayloadNormalizer` 规范化 `payload_json` |
| 5 | 将 `query + files_json + ordered events + response` 组装成 `SessionTurnMemory.blocks` |
| 6 | 聚合快照与最近窗口中的 `artifactRefs`，恢复 `restoredFiles` |
| 7 | 用 `summaryText + facts + restoredFiles` 生成 `historyDialogue` |
| 8 | 从 `recentTurns.blocks` 派生 `preloadedMessages`，供 `AgentRequest.messages` 使用 |

## Event To Block Mapping

| Source | Block Type | Notes |
|--------|------------|-------|
| `AgentMessage.query` | `USER_INPUT` | 当前轮用户输入 |
| `AgentMessage.files_json` | `ARTIFACT_REFERENCE` | 当前轮上传文件转稳定引用 |
| `event_type=plan_thought / tool_thought` | `ASSISTANT_THOUGHT` | 保留原始思考文本 |
| 事件 payload 中的 tool call 信息 | `TOOL_USE` | 尽量还原 `toolUseId / toolName / arguments` |
| `event_type=tool_result / deep_search / code / file / data_analysis / browser ...` | `TOOL_RESULT` 或 `ARTIFACT_REFERENCE` | 根据 payload 类型恢复结果、引用和关键摘要 |
| `AgentMessage.response` | `ASSISTANT_ANSWER` | 当前轮最终回答 |

## Payload Parsing & Correlation Rules

| Rule | Contract |
|------|----------|
| canonical payload | rebuild 路径必须先经过 `ConversationEventPayloadNormalizer`，再做 `event -> block` 映射 |
| `toolUseId` 提取优先级 | `payload.toolUseId -> payload.toolCall.id -> payload.tool.id -> messageId:seqNo` |
| `toolName` 提取优先级 | `payload.toolName -> payload.toolCall.function.name -> event_sub_type -> event_type` |
| `tool_result` 配对 | 先按同一 `toolUseId` 配对；若缺失，再按同轮最近未闭合 `TOOL_USE` 回退 |
| repeated tool calls | 同一 `toolName` 多次调用时，只能按 `toolUseId + seqNo` 区分，禁止按工具名合并 |
| long outputs | `deepsearch report`、超长 `stdout/stderr`、大 `diff` 等必须标记 `referenceOnly=true`，仅保留关键结果与稳定引用 |

## Long Output Policy

- `deepsearch report` 正文、超长 `stdout/stderr`、大 `diff`、大文件读取结果默认不整段进入 `preloadedMessages`
- 这类内容在 block 上标记为 `referenceOnly = true`
- `TOOL_RESULT.content` 仅保留关键结果摘要、结果类型和可回取位置
- 相关文件/报告仍通过 `restoredFiles` 和 `artifactRefs` 继续复用

## Injection Rules

### Prompt Injection

- `summaryText + facts + restoredFiles` 生成 `historyDialogue`
- `historyDialogue` 继续注入 prompt 模板中的 `{{history_dialogue}}`

### Agent Memory Injection

- `preloadedMessages` 注入 `AgentRequest.messages`
- `AgentRequest.Message` 采用结构化扩展方案：保留 `content`，并新增 `messageType / toolCalls / toolCallId / artifactRefs / referenceOnly / files`
- 不采用“把完整 transcript 序列化到单个 markdown 字符串”或单独 `transcriptJson` 字段再下游二次解析的方案
- `RootNode` 与 `Step1SopRecallAndPrepareNode` 必须把 richer `AgentRequest.Message` 转回现有 `agent.dto.Message`

### File Injection

- `restoredFiles` 注入 `AgentRequest.sessionFiles`
- `sessionFiles` 继续在节点层转换为 `AgentContext.productFiles / restoredFiles`

## Fallback Rules

- 无 snapshot：退化为“最近 completed turns + events + files”
- 无 events：退化为 `query + response`
- payload 仅有 legacy `fileInfo/fileList`：先规范化为 `artifactRefs[]`
- 已被 snapshot 边界覆盖的旧历史：继续只通过 `summaryText / facts / restoredFiles` 表达

## Non-Goals

- 不回溯重建 `boundary_sort_order` 之前的完整 event chain
- 不改写 `SessionMemoryCompactionService`
- 不对 `CHAT` 模式引入同样的 transcript rebuild 机制
