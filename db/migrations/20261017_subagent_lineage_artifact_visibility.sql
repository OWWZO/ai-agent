-- 034 — 子 Agent 产物血缘可见性：tool_invocation 血缘索引
-- 背景：plans/034-subagent-artifact-lineage-visibility.md
-- L1 分支 2（卡片血缘）与 L2 fold 查询都要按 parent_tool_call_id 反查，
-- 现有 idx_tool_parent_call (run_id, parent_tool_call_id, deleted) 与 uk_run_tool_call
-- (run_id, tool_call_id) 前导列都是 run_id，按 parent_tool_call_id / tool_call_id 单独查
-- 走不上（EXPLAIN 实证：驱动表 ai_agent_artifact type=ALL 全表扫 + hash join）。
-- 本迁移补 (parent_tool_call_id, deleted) 前导索引，让血缘 join 走
-- pr(session) → p(idx_tool_run_create) → c(本索引) → a(idx_artifact_tool) 索引链。
--
-- 执行约定（项目无 Flyway，手工执行）：
--   /c/Program Files/MySQL/MySQL Server 8.0/bin/mysql -h 127.0.0.1 -P 3306 -uroot -p123456 ai-agent-station
-- 生产执行选低峰；大表用 ALGORITHM=INPLACE, LOCK=NONE。

-- ── 步骤 1：核对索引不存在（期望空结果）────────────────────────────────────────
SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'ai-agent-station'
  AND TABLE_NAME = 'ai_agent_tool_invocation'
  AND COLUMN_NAME IN ('parent_tool_call_id')
GROUP BY INDEX_NAME;

-- ── 步骤 2：加索引（本地库直接执行；生产加 , ALGORITHM=INPLACE, LOCK=NONE）──────────
ALTER TABLE ai_agent_tool_invocation
    ADD INDEX idx_tool_parent_call_id (parent_tool_call_id, deleted);

-- ── 步骤 3：验证索引落地 ──────────────────────────────────────────────────────
SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'ai-agent-station'
  AND TABLE_NAME = 'ai_agent_tool_invocation'
  AND COLUMN_NAME IN ('parent_tool_call_id')
GROUP BY INDEX_NAME;
-- 期望：除 idx_tool_parent_call 外多出 idx_tool_parent_call_id = parent_tool_call_id,deleted

-- ── 步骤 4：EXPLAIN 复核（驱动表不应再是 ai_agent_artifact 全表扫）────────────────
EXPLAIN
SELECT a.id
FROM ai_agent_artifact a
INNER JOIN ai_agent_tool_invocation c ON c.id = a.tool_invocation_id
INNER JOIN ai_agent_tool_invocation p ON p.tool_call_id = c.parent_tool_call_id
INNER JOIN ai_agent_dialogue_run pr ON pr.id = p.run_id
WHERE a.deleted = 0 AND a.visibility = 'visible'
  AND c.deleted = 0 AND p.deleted = 0 AND pr.deleted = 0
  AND c.sub_agent_id IS NOT NULL
  AND pr.session_id = 'session-1792128660698-540';
-- 期望：c 表走 idx_tool_parent_call_id 或 p→c 索引嵌套，a 表 type=eq_ref/range（非 ALL）

-- ── 回滚 ─────────────────────────────────────────────────────────────────────
-- ALTER TABLE ai_agent_tool_invocation DROP INDEX idx_tool_parent_call_id;
