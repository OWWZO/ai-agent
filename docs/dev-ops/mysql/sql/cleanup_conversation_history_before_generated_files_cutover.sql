-- 对话文件索引模型切换前的旧历史数据清理脚本
-- 适用场景：
-- 1. 上线 `generated_files_json` + event 引用式 payload 之前
-- 2. 明确不保留旧历史兼容，允许删除既有对话数据
--
-- 执行前请先完成数据库备份。

START TRANSACTION;

-- 先删依赖消息/会话的会话记忆快照，避免残留旧 artifact 聚合结构
DELETE FROM ai_agent_session_memory;

-- 再删事件账本和消息账本
DELETE FROM ai_agent_message_event;
DELETE FROM ai_agent_message;

-- 最后删会话主表
DELETE FROM ai_agent_conversation;

COMMIT;

-- 如需重置自增主键，可在确认目标环境允许后单独执行：
-- ALTER TABLE ai_agent_session_memory AUTO_INCREMENT = 1;
-- ALTER TABLE ai_agent_message_event AUTO_INCREMENT = 1;
-- ALTER TABLE ai_agent_message AUTO_INCREMENT = 1;
-- ALTER TABLE ai_agent_conversation AUTO_INCREMENT = 1;
