-- 026 — 下线 genui 树工具链，删除其专用输出表
-- 背景：界面通道已由 openui 承担，genui 树默认不装配后代码与数据长期滞留。
--       refactor(genui) 已整体删除工具、账本模型、回放投影器、DAO / PO / Mapper，
--       本迁移清理对应的两张专用输出表。
--
-- 涉及表：
--   ai_agent_tool_output_emit_ui_tree   （98 行）
--   ai_agent_tool_output_emit_ui_patch  （12 行）
--
-- 前置条件：必须先部署代码，再执行本迁移。
--   反向操作（先删表后部署）会让 insertToolInvocation 侧的 ToolOutputWriter
--   仍在 EMIT_UI_TREE / EMIT_UI_PATCH 分支上写这两张表，直接报 table doesn't exist。
--   新代码已移除全部读写路径，删表对运行中系统无影响。
--
-- 数据不可恢复：DROP 之后 110 行结构化树数据即消失。
--   历史会话的 ai_agent_tool_invocation.llm_oberserve 仍保留完整 JSON，
--   但回放时不再有专用投影器，会落到 DefaultToolInvocationProjector 兜底展示原文。
--   如需保留，先自行备份这两张表。
--
-- 执行约定（项目无 Flyway，手工执行）：
--   /c/Program Files/MySQL/MySQL Server 8.0/bin/mysql -h 127.0.0.1 -P 3306 -uroot -p123456 ai-agent-station
-- 生产执行选低峰。

-- ── 步骤 1：确认新代码确实不再引用这两张表 ───────────────────────────────────
-- 在代码库里搜索以下关键字，应全部无结果：
--   ToolOutputEmitUiTreeDao / ToolOutputEmitUiPatchDao
--   GenUiTreeToolOutput / GenUiPatchToolOutput
--   GenUiTreeToolInvocationProjector / GenUiPatchToolInvocationProjector
--   ToolOutputNames.EMIT_UI_TREE / ToolOutputNames.EMIT_UI_PATCH
-- 若仍有命中，说明部署的不是本次重构后的代码，立即中止。

-- ── 步骤 2：核对表存在且记录数符合预期 ─────────────────────────────────────
SELECT TABLE_NAME, TABLE_ROWS, ENGINE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'ai-agent-station'
  AND TABLE_NAME IN ('ai_agent_tool_output_emit_ui_tree', 'ai_agent_tool_output_emit_ui_patch');

SELECT COUNT(*) AS tree_rows FROM ai_agent_tool_output_emit_ui_tree;
SELECT COUNT(*) AS patch_rows FROM ai_agent_tool_output_emit_ui_patch;

-- ── 步骤 3：备份（可选手动执行；生产务必执行）────────────────────────────────
-- CREATE TABLE ai_agent_tool_output_emit_ui_tree_bak_20261023
--   AS SELECT * FROM ai_agent_tool_output_emit_ui_tree;
-- CREATE TABLE ai_agent_tool_output_emit_ui_patch_bak_20261023
--   AS SELECT * FROM ai_agent_tool_output_emit_ui_patch;

-- ── 步骤 4：删表 ────────────────────────────────────────────────────────────
DROP TABLE IF EXISTS ai_agent_tool_output_emit_ui_tree;
DROP TABLE IF EXISTS ai_agent_tool_output_emit_ui_patch;

-- ── 步骤 5：验证（期望空结果）──────────────────────────────────────────────
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'ai-agent-station'
  AND TABLE_NAME LIKE '%emit_ui%';

-- ── 回滚 ──────────────────────────────────────────────────────────────────
-- 没有代码回滚就没有数据回滚。完整回滚需：先还原两张表的 DDL 与数据
-- （见 db/schema.sql 历史版本或步骤 3 的备份），再回退 refactor(genui) 提交。
