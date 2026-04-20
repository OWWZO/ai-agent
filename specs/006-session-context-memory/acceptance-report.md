# Acceptance Report: ReAct / PlanSolve 完整链路会话上下文复原

## 1. 自动化回归状态

| 项目 | 命令 | 结果 |
|------|------|------|
| transcript / working memory 定向回归 | `mvn -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=SessionWorkingMemoryAssemblerTest,SessionMemoryReopenResumeTest,ConversationHistoryPersistenceTest,ConversationHistoryArtifactTest,AgentStreamPersistServiceSessionGuardTest,AgentMessageStopAndResumeTest,AgentStreamPersistWorkingMemoryMessagesTest,SessionTranscriptBlockAssemblerTest" test` | 通过，21/21 |
| 应用模块全量回归 | `mvn -pl ai-agent-station-study-app -am -DskipTests=false test` | 未作为本次交付门禁继续执行。首次尝试暴露 `application-dev.yml` 中 `summary.system_prompt` 的非法多行 YAML，已改为合法 block scalar；修复后再次全量执行时，用户因赶时间主动中断，不再继续跑非关键测试。 |

## 2. 当前已确认结论

- working memory 现在会在 snapshot 边界之后，直接从 `ai_agent_message + ai_agent_message_event` 恢复 ordered transcript blocks，而不是只保留 `query + response`。
- `AgentRequest.Message` 已能携带 `messageType / toolCalls / toolCallId / artifactRefs / referenceOnly / files`，并继续传到 ReAct / PlanSolve 的内部 `agent.dto.Message`。
- `deep_search report`、文件产物、长输出结果会以 `referenceOnly + artifactRefs/files` 形式进入上下文，不再把整段正文直接塞回 working memory。
- 历史重开回放与写入侧 payload 规范化共用 `ConversationEventPayloadNormalizer`，legacy `fileInfo/fileList` 会统一收口到 `artifactRefs`。
- `FORCE_STOPPED` 轮次不会触发 session memory refresh；模式冲突、会话并发冲突的守卫测试仍然通过。
- 为了避免单条脏 event 拖垮整轮续聊，`SessionTranscriptBlockAssembler`、`SessionWorkingMemoryAssembler`、`AgentStreamPersistServiceImpl` 已补充按轮/按块的日志与降级兜底。

## 3. 尚未执行的手工验收

以下 quickstart 场景尚未在真实服务环境中逐项手工跑完：

| 任务 | Quickstart 章节 | 状态 |
|------|------------------|------|
| T012 | 第 2、6、7 节 | 待手工验证 |
| T016 | 第 3 节 | 待手工验证 |
| T021 | 第 4、5、8 节 | 待手工验证 |
| T024 | 最终全链路汇总 | 待手工验证 |

## 4. 数据库核对 SQL

```sql
SELECT session_id, boundary_sort_order, source_turn_count
FROM ai_agent_session_memory
WHERE session_id IN ('sess-react-ledger-001', 'sess-plan-ledger-001');
```

```sql
SELECT request_id, sort_order, status, force_stop, started_at, finished_at
FROM ai_agent_message
WHERE conversation_id = (
  SELECT id FROM ai_agent_conversation WHERE session_id = 'sess-react-ledger-001'
)
ORDER BY sort_order;
```

```sql
SELECT message_id, seq_no, event_type, event_sub_type, status
FROM ai_agent_message_event
WHERE message_id IN (
  SELECT id FROM ai_agent_message
  WHERE conversation_id = (
    SELECT id FROM ai_agent_conversation WHERE session_id = 'sess-react-ledger-001'
  )
)
ORDER BY message_id, seq_no;
```
