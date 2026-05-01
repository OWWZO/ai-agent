# Data Model: 工具输出独立表重构

## 1. ToolInvocationRecord

### Purpose

表示一次工具调用的主追踪账本。重构后它只负责调用事实与终态元数据，不再保存 rich tool 的结构化业务结果。

### Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `id` | `BIGINT` | yes | 主键 |
| `runId` | `BIGINT` | yes | FK -> `DialogueRun.id` |
| `llmInvocationId` | `BIGINT` | yes | FK -> `LlmInvocation.id` |
| `toolCallId` | `VARCHAR(128)` | yes | 模型返回的 toolCallId |
| `dispatchIndex` | `INT` | yes | 同一批工具调用中的原始顺序 |
| `agentName` | `VARCHAR(32)` | yes | 当前 agent 名称 |
| `stepNo` | `INT` | no | 当前步号 |
| `toolName` | `VARCHAR(128)` | yes | 工具名 |
| `toolProvider` | `VARCHAR(64)` | no | `local / mcp / skill` |
| `inputJson` | `JSON` | yes | 工具入参 |
| `llmObservation` | `MEDIUMTEXT` | no | 回传给主智能体继续推理的最终 observation |
| `status` | `TINYINT` | yes | `0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT` |
| `errorMsg` | `TEXT` | no | 错误信息 |
| `startedAt / finishedAt / durationMs` | timing | yes/no | 生命周期字段 |
| `createTime / updateTime / deleted` | meta | yes | 标准审计字段 |

### Validation & Uniqueness

- `(run_id, tool_call_id)` 唯一
- `(llm_invocation_id, dispatch_index)` 唯一
- 重构后明确删除 `output_json`

### State Transitions

`RUNNING -> SUCCESS | FAILED | TIMEOUT`

## 2. ToolStructuredOutput

### Purpose

领域层的强类型工具输出根接口，用来替代 `output_json`，承接 rich tool 的业务语义。

### Contract

```java
public interface ToolStructuredOutput {

    String getToolName();
}
```

### Notes

- 仅 8 类 rich tool 使用
- `ToolInvocationView`、`ToolOutputReader`、projector 都消费该接口或其子类型
- 不允许返回 `PO` 或 `Map<String, Object>` 作为跨层契约

## 3. StructuredToolOutputRecord（概念基类）

### Purpose

描述 8 张输出表共享的终态字段，不对应单独数据库表，而是每张 `ai_agent_tool_output_*` 表都拥有的公共列集合。

### Shared Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `id` | `BIGINT` | yes | 主键 |
| `toolInvocationId` | `BIGINT` | conditional | agent 主链路必填；direct tool call 可为空 |
| `runId` | `BIGINT` | conditional | agent 主链路必填；direct tool call 可为空 |
| `requestId` | `VARCHAR(64)` | yes | 直接检索主键之一 |
| `sessionId` | `VARCHAR(64)` | conditional | 有会话上下文时填写 |
| `toolCallId` | `VARCHAR(128)` | yes | 直接检索主键之一 |
| `status` | `TINYINT` | yes | 仅允许终态 `SUCCESS / FAILED / TIMEOUT` |
| `errorMsg` | `TEXT` | no | 失败/超时错误信息 |
| `createdAt` | `DATETIME` | yes | 首次终态写入时间 |
| `updatedAt` | `DATETIME` | yes | 冲突忽略场景下仍保持首次写入时间或数据库自动更新时间 |

### Validation & Uniqueness

- `uk_tool_invocation(tool_invocation_id)`：`tool_invocation_id` 非空时，一次 agent 工具调用只能对应一条输出记录
- `uk_request_tool_call(request_id, tool_call_id)`：同一请求下同一个 tool call 只能有一条终态输出
- direct lookup 额外要求：同一个 `requestId + toolCallId` 不能跨多张 rich output 表同时出现；若出现，视为数据冲突

### State Transitions

`ABSENT -> SUCCESS | FAILED | TIMEOUT`

一旦写入首个终态，后续重复终态写入仅记录冲突，不允许覆盖。

## 4. ToolOutputLookupKey

### Purpose

表示 direct tool call 的稳定定位信息，不单独落库，但用于 reader 与 query 契约。

### Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `requestId` | `String` | yes | 请求身份 |
| `toolCallId` | `String` | yes | 工具调用身份 |

### Notes

- 对 agent 主链路来说，这组键是 `toolInvocationId` 的补充身份
- 对 direct tool call 来说，这组键是唯一必需的读取条件

## 5. ToolFileRef

### Purpose

统一表达 rich tool 结果中的文件引用，替代各 tool 原来的 `fileInfo` 原生 JSON。

### Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `fileName` | `String` | yes | 文件名 |
| `downloadUrl` | `String` | no | 稳定下载地址 |
| `previewUrl` | `String` | no | 稳定预览地址 |
| `ossUrl` | `String` | no | 对象存储地址 |
| `domainUrl` | `String` | no | 业务域名预览地址 |
| `fileSize` | `Long` | no | 文件大小 |

### Normalization Rules

- 所有带文件结果的输出表都使用 `file_refs_json`
- “无文件产出”统一规范为 `[]`
- replay/detail 展示时，reader/projector 可以再与 `ArtifactView` 稳定链接合并

## 6. DeepSearchToolOutput

### Table

`ai_agent_tool_output_deep_search`

### Purpose

保存深度搜索的查询、阶段轨迹和最终摘要，支撑阶段级 replay。

### Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `query` | `VARCHAR(512)` | no | 原始查询 |
| `answerSummary` | `TEXT` | no | 最终回答摘要 |
| `stages` | `List<DeepSearchStage>` | yes | 由 `stages_json` 持久化，仅保存已实际完成阶段 |

### DeepSearchStage

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `stage` | `String` | yes | `extend / search / report` |
| `queries` | `List<String>` | no | `extend` 阶段的拆解查询 |
| `results` | `List<DeepSearchQueryResult>` | no | `search` 阶段搜索结果 |
| `answer` | `String` | no | `report` 阶段回答 |

### DeepSearchQueryResult

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `query` | `String` | yes | 单条搜索 query |
| `docs` | `List<DeepSearchDoc>` | yes | 搜索文档摘要 |

### DeepSearchDoc

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `title` | `String` | no | 标题 |
| `link` | `String` | no | 链接 |
| `summary` | `String` | no | 内容摘要 |

### Rules

- 不写入未执行阶段占位数据
- 失败/中断场景仍可保留已完成阶段

## 7. FileToolOutput

### Table

`ai_agent_tool_output_file_tool`

### Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `command` | `VARCHAR(16)` | no | `upload / get` 等命令 |
| `primaryFileName` | `VARCHAR(256)` | no | 主文件名，便于检索 |
| `contentStorageMode` | `VARCHAR(32)` | no | 如 `artifact_only` |
| `fileRefs` | `List<ToolFileRef>` | yes | 对应 `file_refs_json` |

## 8. CodeInterpreterToolOutput

### Table

`ai_agent_tool_output_code_interpreter`

### Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `codeOutput` | `MEDIUMTEXT` | no | 代码执行输出 |
| `content` | `MEDIUMTEXT` | no | 最终内容 |
| `code` | `MEDIUMTEXT` | no | 生成/执行代码 |
| `explain` | `MEDIUMTEXT` | no | 补充解释 |
| `fileRefs` | `List<ToolFileRef>` | yes | 结果文件 |

## 9. ReportToolOutput

### Table

`ai_agent_tool_output_report_tool`

### Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `fileType` | `VARCHAR(32)` | no | `html / markdown / ppt` |
| `summary` | `TEXT` | no | 摘要 |
| `content` | `MEDIUMTEXT` | no | 原 `data` 正名后的正文 |
| `fileRefs` | `List<ToolFileRef>` | yes | 报告文件 |

## 10. DataAnalysisToolOutput

### Table

`ai_agent_tool_output_data_analysis`

### Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `task` | `TEXT` | no | 分析任务描述 |
| `summary` | `TEXT` | no | 摘要 |
| `content` | `MEDIUMTEXT` | no | 分析正文 |
| `fileRefs` | `List<ToolFileRef>` | yes | 产出文件 |

## 11. MultimodalAgentToolOutput

### Table

`ai_agent_tool_output_multimodal_agent`

### Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `summary` | `TEXT` | no | markdown 摘要 |
| `markdownContent` | `MEDIUMTEXT` | no | 完整 markdown 内容 |
| `fileRefs` | `List<ToolFileRef>` | yes | markdown 产物文件 |

## 12. ImageGenerationToolOutput

### Table

`ai_agent_tool_output_image_generation`

### Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `prompt` | `TEXT` | no | 生图/改图提示词 |
| `mode` | `VARCHAR(32)` | no | `images / edits` |
| `summary` | `TEXT` | no | 结果摘要 |
| `fileRefs` | `List<ToolFileRef>` | yes | 结果图片 |

## 13. ScriptRunnerToolOutput

### Table

`ai_agent_tool_output_script_runner`

### Fields

| Field | Type | Required | Notes |
|--------|------|----------|-------|
| `skillName` | `VARCHAR(128)` | no | skill 名称 |
| `scriptName` | `VARCHAR(128)` | no | 脚本名 |
| `runtime` | `VARCHAR(32)` | no | 运行时类型 |
| `success` | `TINYINT(1)` | no | 脚本层是否成功 |
| `exitCode` | `INT` | no | 退出码 |
| `stdout` | `MEDIUMTEXT` | no | 标准输出 |
| `stderr` | `MEDIUMTEXT` | no | 标准错误 |
| `summary` | `TEXT` | no | 结果摘要 |
| `fileRefs` | `List<ToolFileRef>` | yes | 输出文件 |

## 14. Relationships

```text
DialogueRun 1 --- N ToolInvocationRecord
ToolInvocationRecord 1 --- 0..1 Specific Tool Output Row
Specific Tool Output Row 1 --- 0..N ToolFileRef (JSON array)
DeepSearchToolOutput 1 --- 0..N DeepSearchStage
DeepSearchStage(search) 1 --- 0..N DeepSearchQueryResult
DeepSearchQueryResult 1 --- 0..N DeepSearchDoc
```

说明：

- “Specific Tool Output Row” 指 8 张 `ai_agent_tool_output_*` 表中的某一张
- 同一次 rich tool 调用只能命中其中 1 张表

## 15. Query Shapes

### Query 1: Agent Replay / Detail by `toolInvocationId`

- 输入：`toolName + toolInvocationId`
- 输出：对应 typed output 子类型
- 用途：history replay、执行详情 enrich

### Query 2: Direct Tool Call Lookup

- 输入：`requestId + toolCallId`
- 输出：唯一 `ToolOutputView(toolName, status, errorMsg, structuredOutput)`
- 语义：reader 在 8 张新表内固定扇出查询，不读取主账本

### Query 3: Recent Tool Invocation Detail

- 输入：`requestId`
- 输出：`ExecutionRunDetail`，其中每条 `ToolInvocationView` 都可附带 `structuredOutput`

## 16. Invariants

- rich tool 的结构化输出永远不再回写主账本 `output_json`
- 8 张输出表只保存终态记录，不保存 RUNNING 过程态
- `deep_search` 的 `stages_json` 只表示真实发生过的阶段
- `file_refs_json` 的空数组与 `status/error_msg` 共同表达“无文件产出但结果有效/失败”
