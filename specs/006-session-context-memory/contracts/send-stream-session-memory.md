# Contract: `/api/agent/message/send-stream` 会话上下文复原与守卫语义

## Scope

本契约定义 `POST /api/agent/message/send-stream` 在 `REACT / PLAN_SOLVE` 模式下的会话守卫、working memory 重建和内部消息注入行为。

## External Request

外部请求体保持不变，继续沿用现有 `MessageSendReqVO`：

```json
{
  "sessionId": "sess-001",
  "requestId": "req-002",
  "query": "继续基于上一次结果补充",
  "deepThink": 0,
  "outputStyle": "html",
  "filesJson": "[]",
  "aiAgentId": null
}
```

## Guard Order

服务端在插入占位消息前，必须按顺序执行以下守卫：

| Step | Rule | If Failed |
|------|------|-----------|
| 1 | 通过 `sessionId` 查询会话；不存在则创建新会话 | 继续 |
| 2 | 校验 `REACT / PLAN_SOLVE` 模式与 `ai_agent_conversation.agent_type` 一致 | 返回 `mode_conflict`，要求新建会话 |
| 3 | 校验当前会话不存在 `STREAMING` 消息 | 返回 `session_busy`，要求等待或先停止 |
| 4 | 守卫通过后才允许插入占位消息并重建 working memory | 进入正常执行 |

## Guard Failure Result

为保持前端 SSE 调用方式不变，守卫失败继续返回“立即结束的 SSE 终态结果”，不新增第二套 JSON 接口：

```json
{
  "finished": true,
  "status": "session_busy",
  "errorMsg": "当前会话仍在执行中，请等待完成或先停止当前轮次"
}
```

或：

```json
{
  "finished": true,
  "status": "mode_conflict",
  "errorMsg": "当前会话已绑定 REACT/PLAN_SOLVE，请新建会话后再切换模式"
}
```

**Failure Requirements**

- 不创建新的 `ai_agent_message` 占位行
- 不刷新 `ai_agent_session_memory`
- 不发起下游 `/AutoAgent` 请求

## Success Path

守卫通过后，服务端执行顺序如下：

1. 插入占位 `AgentMessage`
2. 对非 `CHAT` 模式调用 `IAgentSessionMemoryService.rebuildWorkingMemory(...)`
3. 将 `workingMemory.historyDialogue` 注入 `AgentRequest.historyDialogue`
4. 将 `workingMemory` 派生出的 richer preloaded messages 注入 `AgentRequest.messages`
5. 将 `workingMemory.restoredFiles` 与当前轮上传文件合并注入 `AgentRequest.sessionFiles`
6. 发起 `/AutoAgent` 调用
7. 流结束后按 turn 状态决定是否刷新既有摘要快照

## Internal Request Contract

外部请求不变，但内部发送给 `/AutoAgent` 的 `AgentRequest` 需要支持 richer preloaded messages。逻辑示例如下：

```json
{
  "sessionId": "sess-001",
  "requestId": "trace-001",
  "query": "继续补充",
  "historyDialogue": "已压缩历史摘要...",
  "messages": [
    { "role": "user", "messageType": "user_input", "content": "上一轮的用户问题" },
    { "role": "assistant", "messageType": "assistant_thought", "content": "先确认需要补充 MCP 相关资料" },
    {
      "role": "assistant",
      "messageType": "tool_use",
      "content": "准备调用 deep_search",
      "toolCalls": [
        {
          "id": "tool-use-1",
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
      "toolCallId": "tool-use-1",
      "content": "搜索得到 3 条重点结果，详见引用文件 report.html",
      "referenceOnly": true
    },
    { "role": "assistant", "messageType": "assistant_answer", "content": "上一轮最终回答" }
  ],
  "sessionFiles": [
    {
      "fileName": "report.html",
      "domainUrl": "https://file.example.com/report.html"
    }
  ]
}
```

**Internal Message Strategy**

- `AgentRequest.Message` 采用兼容扩展方案：保留 `role + content`，并新增 `messageType / toolCalls / toolCallId / artifactRefs / referenceOnly / files`
- 不采用“把完整 transcript 压成 markdown 塞进 `content`”或新增仅供字符串承载的 `transcriptJson` 旁路字段
- `RootNode` 与 `Step1SopRecallAndPrepareNode` 负责把这份 richer message 链转换为现有 Agent 内部 `Message` 结构

## Turn Status Semantics

| Turn Status | 写入 `ai_agent_message` / `ai_agent_message_event` | 进入下一轮 working memory | 刷新 `ai_agent_session_memory` |
|------------|-----------------------------------------------------|---------------------------|--------------------------------|
| `COMPLETED` | 是 | 是 | 继续沿用现有刷新逻辑 |
| `ERROR` | 是 | 否 | 否 |
| `FORCE_STOPPED` | 是 | 否 | 否 |

## Scope Boundary

- `CHAT` 模式仍走现有滑动窗口逻辑
- 已被 `ai_agent_session_memory` 边界覆盖的旧历史仍由 `historyDialogue` 摘要承担
- 本期不修改 compaction 阈值、摘要格式、snapshot upsert 规则
