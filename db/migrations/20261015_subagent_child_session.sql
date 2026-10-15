-- Sub-agent child sessions (ZCode-style hidden sessions).
--
-- 注意：仓库内 Reactor-agent-app/src/main/resources/db/schema.sql 只是**滞后快照**，
-- 可能与线上库结构存在历史差异；本迁移的字段以线上库为准，schema.sql 仅作建表参照。
--
-- 子智能体不再是"父会话里的一段内嵌投影"，而是落库为一条真实会话记录：
--   session_kind='subagent'  → 侧栏列表按 kind 白名单过滤，天然不可见
--   parent_session_id        → 只做审计与授权穿透，不参与工作记忆 hydrate
-- 存量行一律 default 'conversation'，本次迁移不改写任何既有数据。

ALTER TABLE ai_agent_dialogue_session
    ADD COLUMN session_kind VARCHAR(32) NOT NULL DEFAULT 'conversation'
        COMMENT '会话类型：conversation=用户会话；subagent=子智能体隐藏会话' AFTER user_id,
    ADD COLUMN parent_session_id VARCHAR(64) NULL
        COMMENT '父会话ID（仅审计与授权穿透；子会话不继承父工作记忆）' AFTER session_kind,
    ADD INDEX idx_dialogue_session_kind (session_kind, deleted);
