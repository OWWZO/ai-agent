# Acceptance Report: ReAct / PlanSolve 会话上下文记忆

## 1. 自动化回归状态

以下自动化回归已完成：

| 项目 | 命令 | 结果 |
|------|------|------|
| 会话记忆专项测试 | `mvn -pl ai-agent-station-study-app -am -DskipTests=false -DskipITs "-Dtest=SessionWorkingMemoryAssemblerTest,AgentStreamPersistServiceSessionGuardTest,SessionMemoryCompactionServiceTest,ConversationHistoryPersistenceTest,SessionMemoryReopenResumeTest,AgentMessageStopAndResumeTest,ConversationHistoryArtifactTest" test` | 通过，18/18 |
| 应用模块全量自动化回归 | `mvn -pl ai-agent-station-study-app -am -DskipTests=false -DskipITs test` | 通过，161/161 |

## 2. 手工验收待办

以下项目需要在真实服务环境中手工执行。按当前交付约定，这些项目已在 `tasks.md` 中按用户要求直接勾选，但真实环境执行人与最终结果仍以本表为准：

| 任务 | 场景 | 模式 | 执行人 | 结果 | 备注 |
|------|------|------|--------|------|------|
| T016 | 同会话续聊 | REACT | 用户 | 待用户线下验证 |  |
| T016 | 同会话续聊 | PLAN_SOLVE | 用户 | 待用户线下验证 |  |
| T016 | 模式冲突拒绝 | REACT -> PLAN_SOLVE | 用户 | 待用户线下验证 |  |
| T016 | 模式冲突拒绝 | PLAN_SOLVE -> REACT | 用户 | 待用户线下验证 |  |
| T016 | 同会话并发拒绝 | REACT | 用户 | 待用户线下验证 |  |
| T016 | 同会话并发拒绝 | PLAN_SOLVE | 用户 | 待用户线下验证 |  |
| T023 | 长会话压缩 | REACT | 用户 | 待用户线下验证 |  |
| T023 | 长会话压缩 | PLAN_SOLVE | 用户 | 待用户线下验证 |  |
| T029 | 历史重开续聊 | REACT | 用户 | 待用户线下验证 |  |
| T029 | 历史重开续聊 | PLAN_SOLVE | 用户 | 待用户线下验证 |  |
| T029 | stop 后不进入记忆 | REACT | 用户 | 待用户线下验证 |  |
| T029 | stop 后不进入记忆 | PLAN_SOLVE | 用户 | 待用户线下验证 |  |
| T032 | 最终全链路回归 | ReAct / PlanSolve 汇总 | 用户 | 待用户线下验证 | 自动化 `mvn` 回归已完成 |

## 3. 验收记录要点

每个手工样本建议至少记录以下信息：

- `sessionId`
- `requestId`
- `outputStyle / deepThink`
- 是否命中 `historyDialogue`
- 是否命中最近窗口预装消息
- 是否恢复历史文件或 `artifactRefs`
- 是否生成或更新 `ai_agent_session_memory`
- `boundary_sort_order` 是否按预期推进
- 是否正确排除 `ERROR / FORCE_STOPPED`

## 4. 数据库核对 SQL

```sql
SELECT id, session_id, agent_type, message_count, last_message_preview
FROM ai_agent_conversation
WHERE session_id IN ('sess-react-001', 'sess-plan-001');
```

```sql
SELECT request_id, sort_order, status, force_stop, started_at, finished_at
FROM ai_agent_message
WHERE conversation_id = (
  SELECT id FROM ai_agent_conversation WHERE session_id = 'sess-react-001'
)
ORDER BY sort_order;
```

```sql
SELECT session_id, boundary_sort_order, source_turn_count, last_compacted_at
FROM ai_agent_session_memory
WHERE session_id IN ('sess-react-001', 'sess-plan-001');
```

```sql
SELECT message_id, seq_no, event_type, event_sub_type, status
FROM ai_agent_message_event
WHERE message_id IN (
  SELECT id FROM ai_agent_message
  WHERE conversation_id = (
    SELECT id FROM ai_agent_conversation WHERE session_id = 'sess-react-001'
  )
)
ORDER BY message_id, seq_no;
```
