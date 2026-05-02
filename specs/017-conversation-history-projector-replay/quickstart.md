# Quickstart: Conversation History Projector Replay

## 1. 前置准备

- 确认当前分支为 `017-conversation-history-projector-replay`
- 确认数据库可执行新增 `ai_agent_dialogue_session` DDL
- 准备至少 3 类样本会话：
  - 成功完成且带工具与文件产物
  - 失败或强制停止
  - 没有显式最终回答、只有总结

## 2. 推荐实现顺序

1. 后端新增会话主表与写侧统计维护
2. 扩展 `ExecutionLedgerQueryService` 与 `ReplayProjector`
3. 新增会话历史 controller / VO / tests
4. 前端增加 history API、类型与 hydrate helper
5. 跑回归并做手工验收

## 3. 后端验证命令

```powershell
chcp 65001
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ExecutionLedgerQueryServiceTest,ReplayProjectorTest,ConversationHistoryControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

如需进一步验证真实执行链：

```powershell
chcp 65001
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

## 4. 前端验证命令

```powershell
chcp 65001
cd ui
npm run test -- conversationHistory.test.ts chat.test.ts
npm run build
```

## 5. 启动应用

```powershell
chcp 65001
mvn -pl ai-agent-station-study-app spring-boot:run
```

## 6. 手工验收流程

### 场景 A: 刷新后自动恢复当前会话

1. 进入一条已有多轮历史的会话
2. 记录当前 `sessionId`
3. 刷新页面或重新打开首页
4. 确认页面恢复的是同一 `sessionId` 的完整多轮内容
5. 确认 runs 顺序与原始执行顺序一致

### 场景 B: 缺少显式最终回答时仍有清晰结论

1. 构造一条只落 `finalSummaryText`、没有稳定 `result` 事件的 run
2. 打开该会话历史详情
3. 确认页面底部结论区仍有可读结果，而不是空白

### 场景 C: 失败或停止轮次可读回显

1. 构造一条失败或强制停止的会话
2. 打开历史详情
3. 确认页面仍显示结束前最后可见细节
4. 确认详情与摘要中能看到明确终态

### 场景 D: 近期会话列表与详情一致

1. 准备多条状态不同、最近活动不同的会话
2. 请求近期会话列表
3. 确认默认只返回 20 条，且按最近活动倒序
4. 随机进入其中一条详情
5. 确认标题、最新状态、最近活动和轮次统计一致

### 场景 E: 当前 `sessionId` 无历史时不自动切换

1. 打开一个新的或无历史的 `sessionId`
2. 触发首页初始化恢复
3. 确认页面保持当前空白或初始界面
4. 确认只提供手动选择近期会话的入口，不自动切到其他会话

## 7. 数据库抽查 SQL

```sql
SELECT session_id, title, status, latest_request_id, latest_query_text,
       run_count, finished_run_count, failed_run_count, started_at, last_active_at
FROM ai_agent_dialogue_session
ORDER BY last_active_at DESC, id DESC
LIMIT 20;

SELECT request_id, session_id, status, query_text, final_summary_text, create_time
FROM ai_agent_dialogue_run
WHERE session_id = ?
ORDER BY create_time ASC, id ASC;

SELECT id, run_id, invocation_seq, agent_name, response_text, status
FROM ai_agent_llm_invocation
WHERE run_id = ?
ORDER BY invocation_seq ASC, id ASC;

SELECT id, run_id, llm_invocation_id, tool_name, dispatch_index, status
FROM ai_agent_tool_invocation
WHERE run_id = ?
ORDER BY dispatch_index ASC, id ASC;

SELECT id, run_id, tool_invocation_id, file_name, storage_key, preview_url, download_url
FROM ai_agent_artifact
WHERE run_id = ?
ORDER BY id ASC;
```

## 8. 回放与恢复抽查

实现完成后，以下关键点应成立：

```powershell
chcp 65001
rg "queryRecentSessionRuns|projectHistory|buildIncrResult|combineData|hydrateConversationFromReplayFrames" ai-agent-station-study-domain ai-agent-station-study-trigger ui/src
```

检查目标：

- `ExecutionLedgerQueryService` 已补会话级查询能力
- `ReplayProjector` 已承接 `agent_name` 语义映射与最终答案 fallback
- `BaseAgentResponseHandler` 不再独自维护完整历史/实时分叉语义
- 前端存在独立的历史 hydrate helper，并复用现有 `combineData`
