# Contract: Structured Tool Output Persistence

## Purpose

定义 rich tool 终态结构化输出的统一写入契约。该契约只服务后端内部执行链路和 direct tool call 落账，不对外暴露 HTTP API。

## Writer Interface

```java
public interface ToolOutputWriter {

    void write(ToolOutputPersistCommand command);
}
```

## Persist Command

```java
@Data
@Builder
public class ToolOutputPersistCommand {

    private Long toolInvocationId;
    private Long runId;
    private String requestId;
    private String sessionId;
    private String toolCallId;
    private String toolName;
    private Integer status;
    private String errorMsg;
    private ToolStructuredOutput structuredOutput;
}
```

## Field Rules

| Field | Required | Notes |
|--------|----------|-------|
| `toolInvocationId` | conditional | agent 主链路必填；direct tool call 可为空 |
| `runId` | conditional | agent 主链路必填；direct tool call 可为空 |
| `requestId` | yes | 所有 rich tool 终态写入都必须有 |
| `sessionId` | conditional | 有会话上下文时填写 |
| `toolCallId` | yes | direct lookup 主键之一 |
| `toolName` | yes | writer 路由所需；失败场景下即使 `structuredOutput` 最小化也必须能识别工具类型 |
| `status` | yes | 仅允许终态 `SUCCESS / FAILED / TIMEOUT` |
| `errorMsg` | no | 失败/超时时建议填写 |
| `structuredOutput` | conditional | 成功场景必填；失败/超时可为最小化 typed output，但不应回退为 JSON 字符串 |

## Lifecycle Rules

1. rich tool 终态完成后，由执行链路先更新主账本公共列，再调用 `ToolOutputWriter.write(...)`
2. direct tool call 场景不依赖主账本存在，但仍必须写 `requestId + toolCallId + toolName + status`
3. 每次调用最多生成 1 条终态输出记录，不保存 RUNNING 过程态

## Routing Rules

- `deep_search` -> `ai_agent_tool_output_deep_search`
- `file_tool` -> `ai_agent_tool_output_file_tool`
- `code_interpreter` -> `ai_agent_tool_output_code_interpreter`
- `report_tool` -> `ai_agent_tool_output_report_tool`
- `data_analysis` -> `ai_agent_tool_output_data_analysis`
- `multimodalagent_tool` -> `ai_agent_tool_output_multimodal_agent`
- `image_generation_tool` -> `ai_agent_tool_output_image_generation`
- `script_runner_tool` -> `ai_agent_tool_output_script_runner`

## Identity & Dedup Rules

1. `tool_invocation_id` 非空时，同一工具调用只能落 1 行
2. `request_id + tool_call_id` 在单张表内唯一
3. direct tool call 允许 `tool_invocation_id / run_id / session_id` 同时为空，但不能缺 `request_id + tool_call_id + tool_name`
4. 写入策略采用 first-write-wins：
   - 首次终态写入成功
   - 后续重复终态写入忽略
   - 必须记录冲突日志，便于排查重复回调/重复 finish

## Normalization Rules

1. 所有带文件结果的 `file_refs_json` 在“无文件”场景下写 `[]`
2. `deep_search.stages_json` 只保存已实际完成且有内容的阶段
3. `report_tool` 与 `data_analysis` 的正文统一命名为 `content`
4. `script_runner.stdout/stderr` 与 `code_interpreter` 长文本字段使用 `MEDIUMTEXT`

## Failure Rules

1. 失败/超时也必须写一条终态输出记录
2. 当没有完整业务内容时，可以写最小化 typed output；解释性由 `status + errorMsg + llmObservation` 保底
3. 不允许通过构造“错误 JSON”来模拟失败结果

## Observability Expectations

- writer 至少记录：
  - `requestId`
  - `runId`
  - `toolCallId`
  - `toolName`
  - 冲突类型（重复终态 / 多表命中等）
- 对重复终态写入要有明确 warn/error 日志
- 数据库写失败不能要求调用方回退到旧 `output_json`
