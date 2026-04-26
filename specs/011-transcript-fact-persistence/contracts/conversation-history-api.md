# Contract: Conversation History API

## Endpoint

`GET /api/agent/conversation/detail?sessionId={sessionId}`

## Response Shape

```json
{
  "conversation": {
    "sessionId": "session-001",
    "title": "生成周报",
    "agentType": 1,
    "productType": "chat",
    "messageCount": 2,
    "lastMessagePreview": "请继续完善刚才的周报"
  },
  "turns": [
    {
      "requestId": "req-001",
      "sortOrder": 0,
      "query": "帮我生成一份周报",
      "files": [],
      "generatedFiles": [
        {
          "fileName": "weekly-report.md",
          "fileType": "markdown",
          "resourceKey": "artifact/weekly-report-md",
          "previewUrl": "https://file/preview/weekly-report.md",
          "downloadUrl": "https://file/download/weekly-report.md"
        }
      ],
      "response": "周报已生成",
      "status": 1,
      "forceStop": 0,
      "events": [
        {
          "seqNo": 1,
          "eventType": "markdown",
          "eventSubType": "report",
          "displayArea": "workspace",
          "title": "weekly-report.md",
          "contentText": "已生成最终 Markdown 报告，请通过稳定引用打开。",
          "status": "completed",
          "payload": {
            "messageType": "task",
            "messageId": "tool-result-1",
            "taskId": "task-1",
            "taskOrder": 1,
            "resultMap": {
              "messageType": "markdown",
              "answer": "已生成最终 Markdown 报告，请通过稳定引用打开。",
              "isFinal": true
            },
            "artifactRefs": [
              {
                "displayName": "weekly-report.md",
                "resourceKey": "artifact/weekly-report-md",
                "previewUrl": "https://file/preview/weekly-report.md",
                "downloadUrl": "https://file/download/weekly-report.md",
                "missing": false
              }
            ]
          }
        }
      ]
    }
  ]
}
```

## Contract Rules

1. `turns[].generatedFiles` 直接来自 `ai_agent_message.generated_files_json`。
2. `turns[].events[].payload` 必须是后端投影后的 canonical payload，而不是数据库原始事实块 payload 直出。
3. 前端应能继续使用现有 `restoreTurn -> combineData -> handleTaskData` 渲染链消费该 payload。
4. 文件缺失态必须通过 `generatedFiles` 或 `payload.artifactRefs` 中的 `missing / missingReason` 明确表达。
5. `eventType / eventSubType` 可以表达事实块语义，但前端渲染主要依赖 `payload`。

## Final Implementation Notes

1. `ai_agent_message_event` 中的 `tool_use` 事实块不会直接透传给历史详情；历史详情只暴露最终需要渲染的 canonical payload。
2. `turns[].generatedFiles` 是 turn 账本字段的直接投影，用于会话级文件查询；`payload.artifactRefs` 是事件级强关联引用。
