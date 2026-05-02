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
mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ExecutionLedgerQueryServiceTest,ReplayProjectorTest,ConversationHistoryControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

如需进一步验证真实执行链：

```powershell
chcp 65001
mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

## 4. 前端验证命令

```powershell
chcp 65001
cd ui
npm run test -- conversationHistory.test.ts chat.test.ts RecentSessionList.test.tsx RunStatus.test.tsx
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
3. 确认左侧对话区会显示明确的终态条（如“已停止”或“执行失败”）
4. 确认右侧工作区在切到对应任务时仍显示相同终态
5. 确认页面仍显示结束前最后可见细节

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
- `BaseAgentResponseHandler` 只复用共享 projector 的 `eventData`，不会把 realtime 顶层 `agentType` 覆盖成 `history`
- 前端存在独立的历史 hydrate helper，并复用现有 `combineData`

## 9. 最终实现备注

- 历史 LLM 的 `plan_thought` 会直接投影为顶层 `eventData.messageType = "plan_thought"`。
- 其他 LLM thought / result 仍以顶层 `task` 包装，真实逻辑类型放在 `eventData.resultMap.messageType`。
- 最近会话列表默认 `20` 条，最大 `100` 条，并按 `lastActiveAt DESC, id DESC` 排序。
- artifact 正常场景显式返回 `missing: false`；若只有文件名但没有可用稳定链接，则返回 `missing: true` 和 `missingReason`。

## 10. 独立回归记录（2026-05-02）

### T016 / 场景 A、B、E

- 后端命令：`mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ConversationHistoryControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
- 后端结果：`ConversationHistoryControllerTest` 共 `5` 个用例全部通过，覆盖会话详情按时间顺序恢复、`finalSummaryText` 兜底以及无历史返回空结果。
- 前端命令：`cd ui && npm run test -- conversationHistory.test.ts RecentSessionList.test.tsx`
- 前端结果：`2` 个测试文件、`4` 个用例全部通过，覆盖 `replayFrames -> ConversationHistory` 恢复、无历史保持空白以及手动选择近期会话入口。

### T023 / 场景 C

- 后端命令：`mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ReplayProjectorTest,ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`
- 后端结果：`ReplayProjectorTest`、`ReactExecutionLedgerIntegrationTest`、`PlanSolveExecutionLedgerIntegrationTest` 共 `6` 个用例全部通过，覆盖 realtime/history `eventData` 同构、`plan_thought` 语义一致、失败/停止 run 终态回放与产物细节保留。
- 前端命令：`cd ui && npm run test -- chat.test.ts RunStatus.test.tsx`
- 前端结果：`2` 个测试文件、`11` 个用例全部通过，覆盖历史 hydrate 后的失败/停止态展示以及左右区域共享终态提示条。

### T030 / 场景 D、E

- 后端命令：`mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ExecutionLedgerQueryServiceTest,ConversationHistoryControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
- 后端结果：`ExecutionLedgerQueryServiceTest` 与 `ConversationHistoryControllerTest` 共 `9` 个用例全部通过，覆盖近期会话默认 `20` 条、`last_active_at` 倒序、摘要与详情统计一致，以及无历史时不自动切换其他会话。
- 前端命令：`cd ui && npm run test -- RecentSessionList.test.tsx conversationHistory.test.ts`
- 前端结果：`2` 个测试文件、`4` 个用例全部通过，覆盖近期会话列表摘要展示、点击切换详情和空历史场景下仅提供手动选择入口。
