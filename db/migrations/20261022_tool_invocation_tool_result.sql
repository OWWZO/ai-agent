-- 025 — 工具调用账本拆分 observation 与完整结果
-- 背景：specs/025-tool-observation-token-frugality
-- llm_oberserve 单列同时承担「模型可见文本」与「前端回放 JSON」两份语义，
-- 导致 observation 无法瘦身（模型上下文里塞满 UI/账本专用字段）。
-- 本迁移新增 tool_result 列：
--   tool_result    = 工具完整结果 JSON（历史回放 / 前端卡片 / 右侧预览）
--   llm_oberserve  = 模型实际看到的紧凑投影（与 working_memory 一致）
-- 注意库列名是历史上拼错的 llm_oberserve，SQL 里照写。
--
-- 执行约定（项目无 Flyway，手工执行）：
--   /c/Program Files/MySQL/MySQL Server 8.0/bin/mysql -h 127.0.0.1 -P 3306 -uroot -p123456 ai-agent-station
-- 生产执行选低峰；大表用 ALGORITHM=INPLACE, LOCK=NONE。
-- 必须先执行本迁移，再部署代码：旧代码不读 tool_result，加列对运行中系统无影响；
-- 反向操作会让 insertToolInvocation / updateToolInvocationFinish 因未知列失败。

-- ── 步骤 1：核对列不存在（期望空结果）────────────────────────────────────────
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'ai-agent-station'
  AND TABLE_NAME = 'ai_agent_tool_invocation'
  AND COLUMN_NAME = 'tool_result';

-- ── 步骤 2：加列（本地库直接执行；生产加 , ALGORITHM=INPLACE, LOCK=NONE）──────
ALTER TABLE ai_agent_tool_invocation
    ADD COLUMN tool_result MEDIUMTEXT NULL
    COMMENT '工具完整结果 JSON（历史回放/前端用）；llm_oberserve 存模型实际看到的文本'
    AFTER llm_oberserve;

-- ── 步骤 3：验证列落地 ───────────────────────────────────────────────────────
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'ai-agent-station'
  AND TABLE_NAME = 'ai_agent_tool_invocation'
  AND COLUMN_NAME IN ('llm_oberserve', 'tool_result')
ORDER BY ORDINAL_POSITION;
-- 期望：llm_oberserve 在前，tool_result 紧随其后，均 MEDIUMTEXT / NULL

-- ── 说明：历史行不回填 ───────────────────────────────────────────────────────
-- 老会话 tool_result 为 NULL，读取侧统一 defaultIfBlank(toolResult, llmObservation) 兜底。
-- 不回填的理由：老数据里 llm_oberserve 存的就是完整 JSON，兜底即等价，无需重复写入。

-- ── 回滚 ─────────────────────────────────────────────────────────────────────
-- ALTER TABLE ai_agent_tool_invocation DROP COLUMN tool_result;
