# Quickstart: 工具输出独立表重构

## 1. 前置条件

- 本地 MySQL 可写
- 当前分支为 `014-tool-output-refactor`
- 已完成 `schema.sql`、typed output 模型、Writer/Reader、projector 与测试用例落地
- 如只编译 `app` 模块前出现 SNAPSHOT 依赖过期，先执行一次 sibling 模块安装

## 2. 执行自动化验证

### 编译领域与基础设施

```powershell
mvn clean compile -pl ai-agent-station-study-domain,ai-agent-station-study-infrastructure -am
```

### 执行账本 / 查询 / projector 回归

```powershell
mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=AgentExecutionLedgerRepositoryTest,ExecutionLedgerQueryServiceTest,ToolInvocationProjectorTest,ReplayProjectorTest
```

### 执行 rich tool 与输出表读写回归

```powershell
mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=DeepSearchLlmObservationTest,MultiModalAgentToolTest,ImageGenerationToolTest,ScriptRunnerToolTest,ToolStructuredOutputWriterTest,ToolStructuredOutputReaderTest
```

## 3. 启动应用

```powershell
mvn -pl ai-agent-station-study-app spring-boot:run
```

## 4. 主账本瘦身检查

执行任一会触发 rich tool 的请求后，检查主账本结构：

```sql
SHOW COLUMNS FROM ai_agent_tool_invocation LIKE 'output_json';
```

预期：

- 查询结果为空，说明主账本已删除 `output_json`

再检查某条工具调用主记录：

```sql
SELECT id, run_id, tool_call_id, tool_name, llm_oberserve, status, error_msg, started_at, finished_at
FROM ai_agent_tool_invocation
WHERE deleted = 0
ORDER BY id DESC
LIMIT 1;
```

预期：

- 主账本只保留调用事实、`llm_oberserve`、终态与错误信息

## 5. rich tool 输出表检查

### `deep_search`

```sql
SELECT tool_invocation_id, request_id, tool_call_id, status, query, answer_summary, stages_json
FROM ai_agent_tool_output_deep_search
ORDER BY id DESC
LIMIT 1;
```

预期：

1. 有且仅有 1 条终态记录
2. `stages_json` 只包含已实际完成阶段
3. 失败/中断场景下 `status` 与主账本一致

### 其他 rich tool

按具体工具检查对应 `ai_agent_tool_output_*` 表，重点关注：

1. `request_id + tool_call_id` 已落账
2. `file_refs_json` 在无文件产出时为 `[]`
3. `status/error_msg` 与主账本一致

## 6. History Replay 验证

运行以下测试：

```powershell
mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=ReplayProjectorTest,ToolInvocationProjectorTest
```

重点预期：

1. rich projector 不再读取 `output_json`
2. `ExecutionLedgerQueryServiceImpl` 会先为 rich tool 补齐 `structuredOutput`
3. `DefaultToolInvocationProjector` 仅依赖 `llmObservation / errorMsg`
4. `DeepSearchToolInvocationProjector` 能按顺序恢复 `extend / search / report`

## 7. Direct Tool Call 检索验证

执行一次没有主账本关联的 direct tool call，随后通过 `ToolOutputReader` 或对应测试读取：

```powershell
mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=ToolStructuredOutputReaderTest
```

预期：

1. 在没有 `tool_invocation_id` 的情况下，仍可通过 `requestId + toolCallId` 命中新表记录
2. 命中结果只来自 8 张新输出表之一
3. 如果同一个 `requestId + toolCallId` 异常命中多张表，reader 会记录冲突日志并返回空结果

## 8. 重复终态写入验证

运行 writer 冲突测试：

```powershell
mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=ToolStructuredOutputWriterTest
```

预期：

1. 首次终态写入成功
2. 后续重复终态写入不覆盖原记录
3. direct tool call 下即使 `tool_invocation_id / run_id / session_id` 为空，也保持同样的首写生效语义
4. 日志中存在冲突记录

## 9. 手工 SQL 排查模板

### 按 `tool_invocation_id` 查主记录

```sql
SELECT *
FROM ai_agent_tool_invocation
WHERE id = ? AND deleted = 0;
```

### 按 `request_id + tool_call_id` 查输出记录

```sql
SELECT *
FROM ai_agent_tool_output_image_generation
WHERE request_id = ?
  AND tool_call_id = ?;
```

将表名替换为实际 rich tool 对应的 `ai_agent_tool_output_*`。

## 10. 常见失败信号

- rich projector 仍然引用 `outputJson`
- `ToolInvocationView` 仍暴露 `outputJson`
- 输出表出现同一 `request_id + tool_call_id` 的多表命中
- `file_refs_json` 在无文件场景下为 `null`
- `deep_search` 为未执行阶段写入占位节点
