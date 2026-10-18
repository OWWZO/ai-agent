-- 20261018: openui 修复编辑意图表（plans/038 §10.4）
-- 用途：前端验收通过的 SEARCH/REPLACE 修复意图落库；刷新/重开按 key 重放，不再调模型。
-- 手工执行（项目无 Flyway/Liquibase）：ai-agent-station 库。

CREATE TABLE IF NOT EXISTS `ai_agent_openui_repair_intent` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `session_id`    VARCHAR(64)  NOT NULL COMMENT '会话ID',
    `message_id`    VARCHAR(64)  NOT NULL COMMENT '消息身份（会话 requestId）',
    `program_hash`  CHAR(64)     NOT NULL COMMENT '程序原文规范化后 SHA-256',
    `intent_index`  INT          NOT NULL COMMENT '同一 key 内的应用顺序（0..n）',
    `search_text`   MEDIUMTEXT   NOT NULL COMMENT 'SEARCH 原文片段（须唯一）',
    `replace_text`  MEDIUMTEXT            COMMENT 'REPLACE 替换文本（空=删除）',
    `model`         VARCHAR(128)          COMMENT '产出意图的模型名',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_openui_replay` (`session_id`, `message_id`, `program_hash`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='openui 修复编辑意图（重放用）';
