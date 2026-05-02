# Contract: Conversation History API

## 1. Session List Endpoint

`GET /api/agent/conversation/sessions?limit={limit}`

### Query Rules

- `limit` 可选，默认 `20`
- 最大值限制为 `100`
- 默认按 `lastActiveAt DESC, id DESC` 返回

### Response Shape

```json
{
  "code": "0000",
  "info": "success",
  "data": [
    {
      "sessionId": "session-history-001",
      "title": "项目风险分析",
      "status": "SUCCESS",
      "latestQueryText": "继续补充方案",
      "runCount": 2,
      "finishedRunCount": 1,
      "failedRunCount": 1,
      "startedAt": "2026-05-01T10:00:00",
      "lastActiveAt": "2026-05-01T10:06:10"
    }
  ]
}
```

### Contract Rules

1. `data` 只返回轻量摘要，不返回总结正文和 replay frames。
2. 默认只返回最近 `20` 条。
3. 排序必须与详情里的最近活动、终态和轮次统计保持一致。
4. 本期列表能力按“受控内部环境”假设开放；后续若引入 owner/tenant 过滤，应只收紧查询条件，不改返回结构。

## 2. Session Detail Endpoint

`GET /api/agent/conversation/sessions/{sessionId}`

### Response Shape

```json
{
  "code": "0000",
  "info": "success",
  "data": {
    "sessionId": "session-history-001",
    "title": "项目风险分析",
    "status": "FAILED",
    "outputStyle": "chat",
    "deepThink": false,
    "role": {
      "agentId": "role-1",
      "agentName": "默认助手",
      "available": true,
      "defaultRole": true
    },
    "runCount": 2,
    "finishedRunCount": 1,
    "failedRunCount": 1,
    "startedAt": "2026-05-01T10:00:00",
    "lastActiveAt": "2026-05-01T10:06:10",
    "runs": [
      {
        "requestId": "req-history-001",
        "status": "SUCCESS",
        "queryText": "先分析项目风险",
        "finalSummaryText": "第一轮总结",
        "startedAt": "2026-05-01T10:00:00",
        "finishedAt": "2026-05-01T10:01:30",
        "replayFrames": [
          {
            "reqId": "req-history-001",
            "status": "success",
            "finished": true,
            "resultMap": {
              "agentType": "history",
              "multiAgent": {},
              "eventData": {
                "taskId": "task-1",
                "taskOrder": 1,
                "messageType": "task",
                "messageOrder": 1,
                "resultMap": {
                  "messageType": "tool_thought",
                  "toolThought": "先搜资料",
                  "isFinal": true
                }
              }
            }
          }
        ]
      }
    ]
  }
}
```

### Contract Rules

1. `runs` 必须按原始时间顺序返回，不允许为方便展示而重排。
2. `replayFrames` 必须按历史展示顺序返回，并尽量保持与实时 SSE `eventData` 同构。
3. 如果某个 run 没有显式最终回答事件，但存在 `finalSummaryText`，服务端必须补一个可读的最终结果 frame。
4. 失败、超时、停止的 run 也必须返回最后可见细节和明确终态。
5. 文件、报告或其他产物引用必须继续使用稳定引用；若已失效，应通过字段显式表达不可用状态。
6. 历史 `plan_thought` 必须直接投影为顶层 `eventData.messageType = "plan_thought"`；其余 thought/result 保持顶层 `task` 包装。
7. artifact 正常场景也要显式返回 `missing: false`，避免前端自行推断。

## 3. Error Semantics

### Session Not Found

- 当 `sessionId` 不存在或没有可恢复历史时，建议返回业务空结果或明确的“无历史”语义
- 首页调用方不得因此自动跳转到其他会话

### Artifact Missing

- 缺失产物时不得静默吞掉
- 当前实现会在对应 artifact/ref 上返回：
  - `missing: true`
  - `missingReason: "artifact_not_found"` 或等价可读原因

## 4. Compatibility Notes

1. 实时 SSE 接口不变。
2. 该详情契约服务的是“历史恢复到现有 UI”，不是通用执行账本导出接口。
3. 若未来补充 owner/tenant 维度，应保持列表/详情 JSON shape 稳定，避免迫使前端重写 hydrate 逻辑。
