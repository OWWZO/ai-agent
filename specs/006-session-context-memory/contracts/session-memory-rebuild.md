# Contract: Session Memory Rebuild

## Scope

本契约定义后端在每次 `REACT / PLAN_SOLVE` 新请求开始前，如何从数据库重建一份可直接注入 Agent 的工作记忆。

## Input

```json
{
  "sessionId": "sess-001",
  "requestId": "req-002",
  "agentType": 2,
  "query": "继续补充",
  "filesJson": "[]"
}
```

## Output

建议内部统一装配为 `SessionWorkingMemory`：

```json
{
  "sessionId": "sess-001",
  "agentType": 2,
  "historyDialogue": "会话摘要与关键事实...",
  "summaryText": "此前已经确认 ...",
  "facts": {
    "goals": [],
    "constraints": [],
    "confirmedConclusions": [],
    "pendingContext": []
  },
  "recentTurns": [
    {
      "requestId": "req-001",
      "sortOrder": 1,
      "userMessage": "上一轮问题",
      "assistantMessage": "上一轮结论"
    }
  ],
  "restoredFiles": [
    {
      "fileName": "report.html",
      "domainUrl": "https://...",
      "ossUrl": "https://..."
    }
  ],
  "boundarySortOrder": 6,
  "needsCompaction": false
}
```

## Build Rules

| Rule | Description |
|------|-------------|
| 会话校验 | 先以 `sessionId` 取会话主档并校验模式归属 |
| 快照优先 | 有快照时先读 `ai_agent_session_memory` |
| 最近窗口 | 只读取边界之后最近若干轮 `COMPLETED` 消息 |
| 事件批量恢复 | 批量读取最近窗口消息对应的 `AgentMessageEvent`，提取稳定 `artifactRefs[]` |
| 异常排除 | `ERROR / FORCE_STOPPED` 不进入 `recentTurns`、`facts`、`summaryText` |
| 无快照退化 | 没有快照时，使用最近完成轮窗口构造最小工作记忆 |

## Injection Rules

### Prompt Injection

- `summaryText + facts` 生成 `historyDialogue`
- `historyDialogue` 注入现有 prompt 模板中的 `{{history_dialogue}}`

### AgentContext Injection

- `restoredFiles` 注入 `AgentContext.productFiles`
- 不恢复到 `taskProductFiles`，因为其职责是“当前任务阶段临时产物”

### BaseAgent Memory Injection

- `recentTurns` 预装入 `PlanningAgent`、`ExecutorAgent`、`ReactImplAgent` 的 `Memory.messages`
- 当前实现只把 `recentTurns` 预装到 `Memory.messages`
- 摘要、facts、恢复文件通过 `historyDialogue` 与 `sessionFiles` 注入，避免重复把摘要再塞进消息数组

## Implemented Rebuild Source

- 摘要快照：`ai_agent_session_memory`
- 最近窗口：`ai_agent_message.status = COMPLETED`
- 稳定产物：`ai_agent_message_event.payload_json.artifactRefs[]`
- legacy `fileInfo/fileList`：读取侧也会再次走 `ConversationEventPayloadNormalizer` 兜底规范化

## Non-Goals

- 不恢复完整 tool-level 历史消息
- 不恢复 `CHAT` 模式的 Spring AI chat memory
- 不跨会话合并用户画像
