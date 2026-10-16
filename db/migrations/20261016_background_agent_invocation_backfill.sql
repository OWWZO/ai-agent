-- 修复后台子 Agent 派发的存量脏账本行：ACK 回执被抢先写成终态，结算 observation 从未落库。
--
-- 背景（plans/032-background-agent-ledger-stale-running-fix.md）：
--   异步派发（run_in_background=true）的 ACK 回执含 "status":"running"，旧代码经
--   ToolExecutionPipeline.finishToolInvocation 直接把 tool_invocation 行写成终态
--   （status=1、finished_at 已写）；随后 BackgroundSubAgentExecutor.persistLedgerSettlement
--   的真正结算更新被 mapper 守卫（status=0 AND finished_at IS NULL）挡掉，affected=0 静默丢失。
--   历史回放把陈旧 ACK 当现状，前端「已运行」从派发时刻无限累加。
--   P0 已修复写入侧（ACK 只写 provisional RUNNING）；本迁移修已被污染的存量行。
--
-- 目标行识别（避免误伤正常终态行）：
--   status=1（终态）且 llm_oberserve 同时含 "run_in_background":true 与 "status":"running"。
--   注意库列名是历史上拼错的 llm_oberserve，SQL 里照写。
--
-- 执行方式（仓库约定，无 Flyway）：手工执行到本地库 ai-agent-station。
--   执行前先跑下方「迁移前核对」SELECT；执行后再跑一次，dirty_rows 必须为 0。
-- 幂等：两条 UPDATE 都带 LIKE '%"status":"running"%' 条件，重跑只影响未修行。
--
-- ---------------------------------------------------------------------------
-- 迁移前核对（预期 2026-10-16 本地库：dirty_rows=48、joinable=47、missing=1）
-- ---------------------------------------------------------------------------
-- SELECT COUNT(*) AS dirty_rows FROM ai_agent_tool_invocation
-- WHERE status=1 AND llm_oberserve LIKE '%"run_in_background":true%'
--   AND llm_oberserve LIKE '%"status":"running"%';
--
-- SELECT COUNT(*) AS joinable FROM ai_agent_tool_invocation t
-- JOIN ai_agent_background_task b
--   ON JSON_UNQUOTE(JSON_EXTRACT(t.llm_oberserve, '$.task_id')) = b.task_id
-- WHERE t.status=1 AND t.llm_oberserve LIKE '%"run_in_background":true%'
--   AND t.llm_oberserve LIKE '%"status":"running"%';
--
-- SELECT b.status, COUNT(*) AS cnt FROM ai_agent_tool_invocation t
-- JOIN ai_agent_background_task b
--   ON JSON_UNQUOTE(JSON_EXTRACT(t.llm_oberserve, '$.task_id')) = b.task_id
-- WHERE t.status=1 AND t.llm_oberserve LIKE '%"run_in_background":true%'
--   AND t.llm_oberserve LIKE '%"status":"running"%'
-- GROUP BY b.status;

-- 备份目标行（执行 UPDATE 前必做）：
-- CREATE TABLE ai_agent_tool_invocation_bak_20261016 AS
-- SELECT * FROM ai_agent_tool_invocation
-- WHERE status=1 AND llm_oberserve LIKE '%"run_in_background":true%'
--   AND llm_oberserve LIKE '%"status":"running"%';

-- ---------------------------------------------------------------------------
-- 1) 主修复：ACK → 与实时 settle 同形的结算回执（background_task 关联得到的行）
--    status / finished_at / duration_ms 保持终态值不动（派发时已写好，只是 observation 错）。
--    stopped/lost 同形写入（ok=false），不特殊化。
-- ---------------------------------------------------------------------------
UPDATE ai_agent_tool_invocation t
JOIN ai_agent_background_task b
  ON JSON_UNQUOTE(JSON_EXTRACT(t.llm_oberserve, '$.task_id')) = b.task_id
SET t.llm_oberserve = JSON_OBJECT(
      'tool', 'Agent',
      'ok', b.status = 'completed',
      'status', b.status,
      'agentId', b.agent_id,
      'agentType', b.agent_type,
      'childSessionId', JSON_UNQUOTE(JSON_EXTRACT(t.llm_oberserve, '$.childSessionId')),
      'description', b.description,
      'content', COALESCE(b.output, ''),
      'totalToolUseCount', b.total_tool_use_count,
      'totalDurationMs', b.total_duration_ms,
      'run_in_background', true,
      'errorMsg', COALESCE(b.error_msg, '')
    )
WHERE t.status = 1
  AND t.llm_oberserve LIKE '%"run_in_background":true%'
  AND t.llm_oberserve LIKE '%"status":"running"%';

-- ---------------------------------------------------------------------------
-- 2) 兜底：ACK 的 task_id 在 background_task 找不到（任务行已清/从未落库）的行，
--    至少把 observation 的 status 改掉，绝不留 running。
-- ---------------------------------------------------------------------------
UPDATE ai_agent_tool_invocation t
SET t.llm_oberserve = JSON_SET(
      t.llm_oberserve,
      '$.status', 'failed',
      '$.errorMsg', 'ledger settlement lost (backfill: background task row missing)'
    )
WHERE t.status = 1
  AND t.llm_oberserve LIKE '%"run_in_background":true%'
  AND t.llm_oberserve LIKE '%"status":"running"%'
  AND NOT EXISTS (
      SELECT 1 FROM ai_agent_background_task b
      WHERE b.task_id = JSON_UNQUOTE(JSON_EXTRACT(t.llm_oberserve, '$.task_id'))
  );

-- ---------------------------------------------------------------------------
-- 迁移后核对（dirty_rows 必须为 0；抽样看 rewritten observation）
-- ---------------------------------------------------------------------------
-- SELECT COUNT(*) AS dirty_rows FROM ai_agent_tool_invocation
-- WHERE status=1 AND llm_oberserve LIKE '%"run_in_background":true%'
--   AND llm_oberserve LIKE '%"status":"running"%';
--
-- SELECT t.id, b.status, LEFT(t.llm_oberserve, 160) FROM ai_agent_tool_invocation t
-- JOIN ai_agent_background_task b
--   ON JSON_UNQUOTE(JSON_EXTRACT(t.llm_oberserve, '$.task_id')) = b.task_id
-- WHERE t.tool_name='Agent' ORDER BY t.id DESC LIMIT 5;
