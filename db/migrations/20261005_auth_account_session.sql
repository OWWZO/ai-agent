-- Preserve the legacy visitor rows and turn the table into the account table in place.
DROP TABLE IF EXISTS admin_user;

RENAME TABLE ai_agent_visitor_identity TO user_account;

ALTER TABLE user_account
    CHANGE COLUMN visitor_id user_id VARCHAR(64) NOT NULL COMMENT '稳定用户ID，供 JWT sub 和业务 ownership 使用',
    ADD COLUMN login_name VARCHAR(64) NULL COMMENT '规范化登录名（trim + lower-case）' AFTER username,
    ADD COLUMN password_hash VARCHAR(255) NULL COMMENT 'BCrypt 密文；legacy 回填前允许为空' AFTER login_name,
    ADD COLUMN nickname VARCHAR(64) NULL COMMENT '展示昵称' AFTER password_hash,
    ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT 'USER 或 ADMIN' AFTER nickname,
    ADD COLUMN last_login_at DATETIME NULL COMMENT '最近登录时间' AFTER role,
    DROP INDEX uk_visitor_identity_visitor,
    DROP INDEX uk_visitor_identity_token,
    DROP INDEX idx_visitor_identity_last_seen,
    ADD UNIQUE KEY uk_user_account_user_id (user_id),
    ADD UNIQUE KEY uk_user_account_token_digest (token_digest),
    ADD UNIQUE KEY uk_user_account_login_name (login_name),
    ADD KEY idx_user_account_last_seen (deleted, last_seen_at DESC);

CREATE TABLE IF NOT EXISTS user_auth_session (
    session_id          CHAR(36)     NOT NULL COMMENT 'refresh session UUID',
    user_id             VARCHAR(64)  NOT NULL COMMENT 'user_account.user_id',
    refresh_token_hash  CHAR(64)     NOT NULL COMMENT 'refresh token SHA-256，不保存原文',
    device_name         VARCHAR(128) NULL COMMENT '设备名称',
    ip                  VARCHAR(128) NULL COMMENT '登录 IP',
    user_agent          VARCHAR(512) NULL COMMENT '登录 User-Agent',
    expires_at          DATETIME     NOT NULL COMMENT 'refresh 会话过期时间',
    last_seen_at        DATETIME     NOT NULL COMMENT '最近使用时间',
    revoked_at          DATETIME     NULL COMMENT '吊销时间',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (session_id),
    UNIQUE KEY uk_user_auth_session_refresh_hash (refresh_token_hash),
    KEY idx_user_auth_session_user (user_id, revoked_at, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号 refresh 会话';
