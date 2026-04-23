# Contract: Summary Normalization Rules

## Scope

本契约定义“结构化总结专用”文本规范化规则。它是前端显示层的临时修复，不等价于修改原始内容。

## Input

| Field | Description |
|-------|-------------|
| `rawText` | 原始总结文本 |
| `scope.scene` | `structured_summary` 或 `default` |
| `isStreaming` | 是否流式渲染 |

## Rule Activation

- 仅当 `scope.scene = structured_summary` 时允许启用本契约中的增强规则
- `scope.scene = default` 时不得默认应用本期增强修复

## Mandatory Repairs

### 1. Line Ending Normalization

- 移除 BOM
- 将 `CRLF / CR` 统一为 `LF`

### 2. Code Fence Protection

- 先按 fenced code block 切段
- 代码块内禁止执行标题、列表和段落边界修复

### 3. Heading Repair

允许修复以下模式：

- `###1）经典必去`
- `##你如果想要更好用的玩法`
- 标题符号直接贴在中文句尾后的情况

修复方式只允许：

- 补空格
- 补换行
- 在必要时补空一行形成独立段落

### 4. List Repair

允许修复以下模式：

- `-计划玩几天`
- `-住在中山路附近还是曾厝垵附近`
- 列表标记直接贴在中文句尾后的情况

修复方式只允许：

- 补空格
- 补换行

## Output Guarantees

- 不得删除正文内容
- 不得重排原始段落顺序
- 已合法 Markdown 只能做语义等价的轻量修正
- 输出必须同时适用于 `Streamdown` 和 `ReactMarkdown`

## Unsupported Repairs

以下内容不在本期修复范围：

- 自动重写表格结构
- 自动补全引用块、任务列表等更复杂 Markdown 语法
- 根据语义猜测新增不存在的标题或列表项
