# Contract: Structured Summary Rendering

## Scope

本契约定义 `REACT / PLAN_SOLVE` 最终总结在前端的展示行为。它只约束结构化模式最终总结，不扩展到普通聊天回复或其他 Markdown 渲染场景。

## Rendering Entry

```text
Dialogue.ConclusionSection
  -> MarkdownRenderer
     -> MessageResponse (streaming)
     -> ReactMarkdown (final)
```

## Input Sources

| Source | Description |
|--------|-------------|
| `chat.conclusion.messageType = agent_stream` | 总结阶段的实时增量文本 |
| `chat.conclusion.messageType = task_summary` | 总结完成后的最终文本 |
| `buildHistoryConclusionFallback(turn.response)` | 历史详情缺少总结事件时的回放补偿文本 |

## Required Rendering Sequence

1. 从 `chat.conclusion` 或历史 fallback 解析当前总结文本
2. 判断当前是否属于 `REACT / PLAN_SOLVE` 最终总结入口
3. 仅在该入口下启用结构化总结规范化规则
4. 将规范化后的文本交给：
   - `MessageResponse` / `Streamdown`（流式）
   - `ReactMarkdown + remark-gfm`（完成态）

## Guarantees

- 实时流式、完成态和历史回放必须共享同一展示语义
- 近似 Markdown 至少支持标题缺少空格、列表缺少空格、标题/列表贴句尾三类修复
- fenced code block、内联代码和已合法 Markdown 结构不得被误改
- 为空时继续沿用现有空内容兜底，不引入新的白屏或异常状态

## Explicit Non-Goals

- 不修改后端 SSE 事件结构
- 不修改数据库中的历史总结文本
- 不改变普通 `CHAT` 回复、文件预览或其他 Markdown 内容的默认展示行为

## Compatibility

- `task_summary`、`agent_stream`、历史 fallback 的字段结构保持不变
- 非结构化模式会话继续沿用现有回复渲染逻辑
