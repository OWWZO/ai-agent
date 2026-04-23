# Data Model: 修复 React/PlanSolve 最终总结 Markdown 展示

## 1. StructuredSummarySource（既有运行时来源）

**Purpose**: 表示结构化模式最终总结在前端的原始文本来源，是本期渲染修复的输入对象。

| Field | Type | Notes |
|------|------|-------|
| `messageType` | `"agent_stream" \| "task_summary"` | 总结来源类型 |
| `result` | `string` | 原始总结文本 |
| `resultMap.taskSummary` | `string` | 结构化总结主文本 |
| `requestId` | `string` | 所属请求 |
| `messageTime` | `string` | 展示排序时间 |
| `isFinal` | `boolean` | 是否为完成态 |

**Source Rules**

- 实时总结先以 `agent_stream` 增量写入 `chat.conclusion`
- 任务完成后以 `task_summary` 或最终结果覆盖临时总结
- 历史回放缺少结构化总结事件时，可由 `buildHistoryConclusionFallback()` 用 `turn.response` 补出等价总结来源

## 2. SummaryRenderScope（新增运行时边界）

**Purpose**: 明确本期修复只对“结构化模式最终总结”生效，避免规则误伤其他 Markdown 场景。

| Field | Type | Notes |
|------|------|-------|
| `scene` | `"structured_summary" \| "default"` | 渲染场景标识 |
| `agentType` | `0 \| 1 \| 2 \| undefined` | `1=PLAN_SOLVE, 2=REACT` |
| `isConclusion` | `boolean` | 是否来自最终总结入口 |
| `isStreaming` | `boolean` | 是否仍在流式阶段 |

**Invariants**

- 只有 `scene = structured_summary` 时才允许启用总结专用规范化规则
- 普通 `CHAT` 回复、文件预览、HTML 转 Markdown 等场景默认保持 `default`

## 3. MarkdownNormalizationInput（新增运行时输入）

**Purpose**: 规范化函数的输入载荷，描述原始文本和应使用的修复策略。

| Field | Type | Notes |
|------|------|-------|
| `rawText` | `string` | 原始待展示文本 |
| `scope` | `SummaryRenderScope` | 渲染边界 |
| `preserveCodeFence` | `boolean` | 是否跳过 fenced code block |
| `normalizeLineEndings` | `boolean` | 是否统一换行 |

**Rules**

- `rawText` 为空时直接返回空字符串，不进入复杂修复
- `preserveCodeFence` 在本期必须恒为 `true`
- `scope.scene = default` 时只允许沿用既有轻量规则或直接透传，不得自动套用总结增强修复

## 4. NormalizedSummaryDocument（新增运行时输出）

**Purpose**: 供 `Streamdown` 或 `ReactMarkdown` 消费的最终可展示文本。

| Field | Type | Notes |
|------|------|-------|
| `rawText` | `string` | 原始输入 |
| `normalizedText` | `string` | 修复后的文本 |
| `appliedRepairs` | `string[]` | 实际命中的修复类型 |
| `codeFenceProtected` | `boolean` | 是否已跳过代码块处理 |

**Output Guarantees**

- 不得删掉正文内容
- 不得改写 fenced code block 内的内容
- 对已合法 Markdown，`normalizedText` 只能做语义等价的轻量换行/空格修正

## 5. StructuredSummaryRepairRule（新增规则集合）

**Purpose**: 描述本期允许触发的近似 Markdown 修复类别。

| Rule | Description |
|------|-------------|
| `heading-space-fix` | 把 `###1）`、`##你如果...` 修成可识别标题 |
| `list-space-fix` | 把 `-计划玩几天`、`1）经典必去` 等近似列表修成可识别列表/标题块 |
| `sentence-boundary-break` | 当标题或列表贴在中文句尾后面时补充换行边界 |
| `single-blank-line-upgrade` | 需要时把粘连结构升级为独立段落，避免标题与正文混在一起 |

**Rules Boundary**

- 规则只允许“补空格、补换行、补段落边界”
- 不允许凭空生成新的标题、列表项或重排内容顺序
- 复杂表格、引用块等未在本期范围内的结构不做主动重写

## 6. SummaryRenderContext（既有入口，增强说明）

**Purpose**: `ConclusionSection` 组装总结展示时的上下文，用于决定使用哪种渲染模式。

| Field | Type | Notes |
|------|------|-------|
| `summaryText` | `string` | 当前要展示的总结文本 |
| `summaryStreaming` | `boolean` | 是否流式展示 |
| `attachments` | `CHAT.TFile[]` | 总结附件 |
| `scope` | `SummaryRenderScope` | 本期新增的渲染边界信息 |

**Assembly Order**

1. 从 `chat.conclusion` 解析 `summaryText`
2. 根据 `chat.agentType / deepThink / conclusion.messageType` 计算 `scope`
3. 将 `summaryText + scope` 传入 `MarkdownRenderer`
4. `MarkdownRenderer` 输出 `NormalizedSummaryDocument`
5. `MessageResponse`（流式）或 `ReactMarkdown`（完成态）消费最终文本

## 7. HistorySummaryFallback（既有恢复对象）

**Purpose**: 当历史详情缺少完整总结事件时，用最终回答恢复出的总结视图对象。

| Field | Type | Notes |
|------|------|-------|
| `requestId` | `string` | 所属历史请求 |
| `messageType` | `"task_summary"` | 视作最终总结 |
| `result` | `string` | 从历史 `turn.response` 恢复出的文本 |
| `resultMap.taskSummary` | `string` | 与实时总结统一字段 |

**Compatibility Rules**

- 历史 fallback 不改变后端存储内容，只补齐前端运行时结构
- 一旦被恢复为总结对象，就必须与实时总结共享同一 `SummaryRenderScope`

## Relationships

```text
StructuredSummarySource
  └── SummaryRenderContext
        └── SummaryRenderScope
        └── MarkdownNormalizationInput
              └── StructuredSummaryRepairRule[]
              └── NormalizedSummaryDocument
                    └── Streamdown / ReactMarkdown

HistorySummaryFallback
  └── StructuredSummarySource
```

## Backward Compatibility Rules

- 现有 `task_summary`、`agent_stream`、`turn.response` 字段结构全部保留
- 旧历史数据即使没有完整总结事件，也能通过 fallback 进入相同展示链路
- 非结构化聊天回复默认不进入 `structured_summary` scope
