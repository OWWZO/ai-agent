# Contract: Fact Event Storage

## Table

`ai_agent_message_event`

## Storage Semantics

每条记录表示单轮请求中的一个后端事实块，而不是前端最终态快照。

## Column Contract

| Column | Meaning |
|--------|---------|
| `message_id` | 所属轮次 |
| `seq_no` | 块顺序 |
| `event_type` | 事实块主类型 |
| `event_sub_type` | 来源细分 |
| `display_area` | 最小展示区域提示 |
| `task_id` | 任务链标识 |
| `task_order` | 任务内顺序 |
| `title` | 块标题/摘要 |
| `content_text` | 块正文摘要 |
| `payload_json` | 事实数据与资源引用 |
| `status` | 该轮持久化终态 |

## event_type Enumeration

| Value | Meaning |
|-------|---------|
| `assistant_thought` | 助手思考文本 |
| `plan_snapshot` | 计划或任务快照 |
| `tool_use` | 工具调用事实 |
| `tool_result` | 工具结果事实 |
| `artifact_reference` | 产物引用事实 |

## payload_json Requirements

1. 必须只保存后端事实、稳定引用和必要渲染元数据。
2. 不应直接保存前端本地状态字段。
3. 对 HTML/Markdown/PPT/报告等大体量内容，只保存稳定引用与摘要，不保存全文。
4. 若块与工具链相关，应尽量包含：
   - `toolUseId`
   - `toolName`
   - `toolArguments`
   - `referenceOnly`
   - `artifactRefs`
5. 若块与计划/任务相关，应包含重建历史与记忆所需的最小结构字段，而不是整份 UI 快照。

## Final Stored Example

```json
{
  "blockType": "tool_result",
  "sourceType": "markdown",
  "sourceSubType": "report",
  "messageId": "semantic-tool-result-1",
  "taskId": "task-1",
  "taskOrder": 1,
  "toolUseId": "tool-call-1",
  "toolName": "deep_search",
  "toolArguments": {
    "query": "本周项目进展"
  },
  "summary": "已生成最终 Markdown 报告，请通过稳定引用打开。",
  "referenceOnly": true,
  "artifactRefs": [
    {
      "displayName": "weekly-report.md",
      "resourceKey": "artifact/weekly-report-md",
      "previewUrl": "https://file/preview/weekly-report.md",
      "downloadUrl": "https://file/download/weekly-report.md",
      "missing": false
    }
  ],
  "resultData": {
    "messageType": "markdown",
    "answer": "已生成最终 Markdown 报告，请通过稳定引用打开。",
    "isFinal": true
  }
}
```

## Final Implementation Notes

1. `title` / `content_text` 只保留可读摘要；完整渲染契约在读取时由后端投影生成。
2. 同一种新来源类型（如 `knowledge`、`browser`、`data_analysis`）继续复用 `tool_result` 这类通用事实块形状，不新增前端快照字段。
