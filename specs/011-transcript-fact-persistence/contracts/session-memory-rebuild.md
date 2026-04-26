# Contract: Session Memory Rebuild

## Input Sources

### Turn Ledger

- `ai_agent_message.query`
- `ai_agent_message.response`
- `ai_agent_message.files_json`
- `ai_agent_message.generated_files_json`
- `ai_agent_message.status`

### Fact Block Ledger

- `ai_agent_message_event.*`

## Output Model

`SessionTurnMemory`

### Required Blocks

| Source | Output Block |
|--------|--------------|
| `query` | `USER_INPUT` |
| `response` | `ASSISTANT_ANSWER` |
| 上传文件 | `ARTIFACT_REFERENCE` |
| 生成文件 | `ARTIFACT_REFERENCE` |
| `event_type=assistant_thought` | `ASSISTANT_THOUGHT` |
| `event_type=tool_use` | `TOOL_USE` |
| `event_type=tool_result` | `TOOL_RESULT` |
| `event_type=artifact_reference` | `ARTIFACT_REFERENCE` |

## Rules

1. `ai_agent_message.response` 是最终回答真相源，不要求事件账本重复保存一份完整回答块。
2. `generated_files_json` 必须进入 `artifactRefs` 聚合结果，确保续聊时能感知本轮产物。
3. `payload_json.referenceOnly=true` 的块进入上下文时只保留摘要与稳定引用，不回灌大体量正文。
4. 若某个事实块缺失可选字段，恢复逻辑必须安全降级，不能导致整轮恢复失败。
5. 会话记忆恢复与历史详情投影共享同一份事实块语义，但输出形态不同：
   - 历史详情输出 canonical render payload
   - 会话记忆输出 `TranscriptContextBlock`

## Final Rebuild Example

### Turn Ledger Input

```json
{
  "query": "继续基于刚才周报补充风险项",
  "response": "我补充了 3 条风险项，并保留了报告稳定引用。",
  "generated_files_json": [
    {
      "fileName": "weekly-report.md",
      "resourceKey": "artifact/weekly-report-md",
      "previewUrl": "https://file/preview/weekly-report.md",
      "downloadUrl": "https://file/download/weekly-report.md"
    }
  ]
}
```

### Fact Block Input

```json
{
  "event_type": "tool_result",
  "payload_json": {
    "blockType": "tool_result",
    "toolUseId": "tool-call-1",
    "toolName": "deep_search",
    "summary": "已生成最终 Markdown 报告，请通过稳定引用打开。",
    "referenceOnly": true,
    "artifactRefs": [
      {
        "displayName": "weekly-report.md",
        "resourceKey": "artifact/weekly-report-md",
        "previewUrl": "https://file/preview/weekly-report.md",
        "downloadUrl": "https://file/download/weekly-report.md"
      }
    ]
  }
}
```

### Restored Output

```text
USER_INPUT: 继续基于刚才周报补充风险项
TOOL_RESULT: 已生成最终 Markdown 报告，请通过稳定引用打开。
ARTIFACT_REFERENCE: weekly-report.md
ASSISTANT_ANSWER: 我补充了 3 条风险项，并保留了报告稳定引用。
```

## Final Implementation Notes

1. `historyDialogue`、结构化 `messages`、`sessionFiles` 都来自同一份 `SessionWorkingMemory` 恢复结果。
2. `generated_files_json` 与事件中的 `artifactRefs` 会去重合并，避免同一个产物在续聊上下文里重复出现两次。
