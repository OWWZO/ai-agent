# Quickstart: 对话执行持久化账本

## 1. 前置条件

- 本地 MySQL 可写
- Spring Boot 主应用可启动
- 当前分支为 `013-dialogue-persistence`
- 已完成 `schema.sql`、Mapper、领域服务与测试用例落地

## 2. 执行自动化验证

```powershell
mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=ToolArtifactBindingRuntimeTest,AgentExecutionLedgerRepositoryTest,ExecutionLedgerQueryServiceTest,ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest
```

说明：

- 若本次改动同时涉及 `domain` / `trigger` / `infrastructure` / `api` / `types`，且只执行 `-pl ai-agent-station-study-app`，建议先执行一次 sibling 模块安装以刷新本地 SNAPSHOT：

```powershell
mvn -pl ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-trigger,ai-agent-station-study-api,ai-agent-station-study-types -am install -DskipTests
```

## 3. 启动应用

```powershell
mvn -pl ai-agent-station-study-app spring-boot:run
```

## 4. ReAct 冒烟

向 `/AutoAgent` 发送一条 `agentType=REACT` 的请求，确保请求里带：

- `requestId`
- `sessionId`
- `query`

预期：

1. SSE 正常返回，不因账本写入失败直接中断
2. `ai_agent_dialogue_run` 中出现 1 条对应 `request_id` 的 run
3. `ai_agent_llm_invocation` 至少有 1 条记录
4. 如果发生工具调用，`ai_agent_tool_invocation` 中按 `dispatch_index` 写入
5. 若工具产出文件，`ai_agent_artifact` 中出现对应 `tool_call_id` 的输出记录

## 5. PlanSolve 并发工具冒烟

发送一条 `agentType=PLAN_SOLVE` 且能触发多个工具调用的请求。

预期：

1. 同一次 `askTool()` 返回的多个 tool call 先被顺序登记
2. `ai_agent_tool_invocation` 中同一 `llm_invocation_id` 下的 `dispatch_index` 连续递增
3. 即使工作线程完成顺序不同，`dispatch_index` 仍保持模型原始顺序
4. `started_at` 可以反映真实执行时间线

## 6. 账本链路 SQL 检查

### 按 requestId 查看 run

```sql
SELECT *
FROM ai_agent_dialogue_run
WHERE request_id = ? AND deleted = 0;
```

### 查看该 run 的 LLM 调用

```sql
SELECT *
FROM ai_agent_llm_invocation
WHERE run_id = ?
  AND deleted = 0
ORDER BY invocation_seq;
```

### 查看该 run 的工具调用

```sql
SELECT *
FROM ai_agent_tool_invocation
WHERE run_id = ?
  AND deleted = 0
ORDER BY llm_invocation_id, dispatch_index;
```

### 查看该 run 的产物

```sql
SELECT *
FROM ai_agent_artifact
WHERE run_id = ?
  AND deleted = 0
ORDER BY create_time;
```

## 7. Fail-Open 验证

人为制造一次账本落库失败，例如：

- 临时让某张新表不可写
- 或在测试替身里抛出 `RuntimeException`

预期：

1. 用户主流程仍返回 SSE 结果或最终 summary
2. 日志中出现带 `requestId/runId/toolCallId` 的错误记录
3. 指标中可观察到失败计数或 `successRate` 下降
4. 不会出现半更新的 run 终态伪装成成功

## 8. 去重验证

对同一 `toolCallId` 重复登记同一稳定文件引用。

预期：

- `ai_agent_artifact` 在相同 `run_id + tool_call_id + storage_key` 下只保留一条记录
- `ToolArtifactRegistry` 现有可见文件行为不回归
