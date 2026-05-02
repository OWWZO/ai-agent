# Contract: Replay Hydration

## Goal

定义“后端历史 replay frames”如何被前端恢复为当前 `CHAT.ConversationHistory`，并约束历史与实时展示保持同构。

## 1. Backend Replay Frame Requirements

每个 `replayFrame` 至少应包含：

```json
{
  "reqId": "req-001",
  "status": "success",
  "finished": true,
  "resultMap": {
    "agentType": "react",
    "multiAgent": {},
    "eventData": {
      "taskId": "task-1",
      "taskOrder": 1,
      "messageType": "task",
      "messageOrder": 1,
      "artifactRefs": [],
      "resultMap": {
        "messageType": "tool_result",
        "isFinal": true
      }
    }
  }
}
```

### Required Semantics

1. `resultMap.eventData` 必须能被前端 `combineData(eventData, currentChat)` 直接消费。
2. `resultMap.eventData.resultMap.messageType` 的语义必须与实时 SSE 一致。
3. `artifactRefs` 放在 `eventData` 顶层时，前端会通过 `buildTaskFromEventData` 统一折叠进任务对象。
4. `result` / `task_summary` 相关 frame 必须足以恢复 `currentChat.conclusion`。
5. 历史 `plan_thought` 是唯一直接使用顶层 `eventData.messageType = "plan_thought"` 的 LLM 事件；其余 LLM 事件继续走顶层 `task`。

## 2. Frontend Hydration Algorithm

前端恢复算法约定如下：

1. 为每个 run 创建一个空 `CHAT.ChatItem`。
2. 按顺序遍历该 run 的 `replayFrames`。
3. 对每个 frame：
   - 读取 `frame.resultMap.eventData`
   - 调用 `combineData(eventData, currentChat)`
   - 若该事件是最终结果事件，则同步更新 `currentChat.conclusion`
   - 将 run 级 `status` 写入 `currentChat.metrics.status`，供终态提示条复用
4. run 结束后，将该 `currentChat` 推入 `conversation.chatList`
5. 最终调用 `handleTaskData` 或现有渲染链完成派生展示

## 3. History/Realtime Isomorphism Rules

1. 历史和实时的主消息类型必须共用同一套来源规则，不允许前端针对历史单独写第二套 `switch`。
2. `tool_thought / plan_thought / result` 的判定必须由后端统一完成。
3. rich tool 的历史结果必须继续通过 structured output + artifact projector 投影，不能退化成纯文本。
4. 历史 frame 顺序稳定性高于“看起来更自然”的二次排序。

## 4. Fallback Rules

### Missing Final Answer

- 如果 run 没有显式 `result` frame，但有 `finalSummaryText`
- 后端必须补一个最终结果 frame
- 前端不负责从 `finalSummaryText` 自行生成结论

### Missing Artifact

- 若 artifact 已不可用
- frame 中对应引用仍要保留
- 但需带上 `missing` / `missingReason`
- 正常可用的 artifact 也应显式返回 `missing: false`

### Empty Session

- 当前 `sessionId` 无历史时
- 前端保持当前空白或初始态
- 不自动切换到最近会话或系统范围其他会话

## 5. Test Contract

以下场景必须通过测试覆盖：

1. `replayFrames -> ConversationHistory` 能恢复多任务结构。
2. 历史与实时对同一类 `eventData` 的渲染结果一致。
3. 没有显式最终回答时，历史仍有结论区。
4. 失败或停止会话仍能恢复最后可见细节。
5. 带 artifact 的工具结果能继续走现有文件预览与展示链。
