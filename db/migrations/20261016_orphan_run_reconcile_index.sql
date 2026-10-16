-- 启动对账（orphan run reconcile）扫描索引。
--
-- 背景：SessionBackgroundTaskHub / ActiveAgentRunRegistry / AgentStreamProjection 全部活在 JVM 内存，
-- kill -9 / OOM / 滚动发布会让账本留下「说在跑、世上已无人执行」的孤儿：
--   ai_agent_dialogue_run.status=0（RUNNING）永不落终态
--   ai_agent_tool_invocation.status=0 且 finished_at IS NULL
--   ai_agent_background_task.status='running'
-- 启动对账器（BackgroundRunReconciler）一次性扫这三类并收敛到终态，扫描条件为
-- 「状态仍为运行态 AND started_at < 启动时刻 - 宽限窗」，故补状态前导的复合索引。
--
-- 注意：仓库内 Reactor-agent-app/src/main/resources/db/schema.sql 只是**滞后快照**，
-- 以线上库为准；本迁移只加索引，不改列、不改数据。

ALTER TABLE ai_agent_dialogue_run
    ADD INDEX idx_dialogue_run_reconcile (status, deleted, started_at);

ALTER TABLE ai_agent_tool_invocation
    ADD INDEX idx_tool_invocation_reconcile (status, deleted, finished_at, started_at);

ALTER TABLE ai_agent_background_task
    ADD INDEX idx_background_task_reconcile (status, deleted, started_at_ms);
