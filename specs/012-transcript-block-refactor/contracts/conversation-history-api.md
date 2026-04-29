# Contract: Conversation History API

## Endpoint

`GET /api/agent/conversation/detail?sessionId={sessionId}`

## Response Shape

```json
{
  "conversation": {
    "id": 12,
    "sessionId": "session-001",
    "title": "生成周报",
    "agentType": 2,
    "productType": "chat",
    "messageCount": 3,
    "lastMessagePreview": "继续补充风险项"
  },
  "turns": [
    {
      "turnId": 101,
      "requestId": "req-001",
      "sortOrder": 1,
      "query": "继续补充风险项",
      "status": 1,
      "startedAt": "2026-04-28T10:00:00",
      "finishedAt": "2026-04-28T10:00:12",
      "displayEvents": [
        {
          "seqNo": 1,
          "displayType": "user_message",
          "title": "用户输入",
          "contentText": "继续补充风险项",
          "artifactRefs": [],
          "status": "completed",
          "displayArea": "timeline"
        },
        {
          "seqNo": 2,
          "displayType": "tool_call",
          "title": "调用 deep_search",
          "contentText": "执行检索并补充风险项",
          "toolUseId": "tool-1",
          "toolName": "deep_search",
          "toolArguments": {
            "query": "本周项目风险项"
          },
          "status": "running",
          "displayArea": "timeline"
        },
        {
          "seqNo": 3,
          "displayType": "artifact",
          "title": "生成文件",
          "contentText": "已更新 weekly-report.md",
          "artifactRefs": [
            {
              "displayName": "weekly-report.md",
              "resourceKey": "artifact/weekly-report-md",
              "previewUrl": "https://file/preview/weekly-report.md",
              "downloadUrl": "https://file/download/weekly-report.md",
              "missing": false
            }
          ],
          "status": "completed",
          "displayArea": "timeline"
        },
        {
          "seqNo": 4,
          "displayType": "final_answer",
          "title": "回答",
          "contentText": "我已补充 3 条风险项并更新报告。",
          "status": "completed",
          "displayArea": "timeline"
        }
      ]
    }
  ]
}
```

## Contract Rules

1. `turns` 按 `sortOrder` 升序返回。
2. `displayEvents` 按 `seqNo` 升序返回。
3. `displayEvents` 是历史详情的唯一权威读模型；前端不得再把它转换回旧的实时 payload 协议后再渲染。
4. `artifactRefs` 必须直接表达稳定文件引用及其缺失态。
5. `query` 属于 turn 元数据；真正渲染用户输入时应使用 `displayType=user_message` 的事件。
6. `status` 采用 turn 级整型状态；`displayEvents[].status` 采用展示级字符串状态。

## Final Implementation Notes

1. `ConversationDetailRespVO` / `ConversationTurnRespVO` / `ConversationEventRespVO` 可以重命名或重构，但最终语义必须围绕 `turn + displayEvents`。
2. 历史详情读取不再允许依赖 `restoreTurn -> combineData -> handleTaskData` 这种旧历史恢复链。
