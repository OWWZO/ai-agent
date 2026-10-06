CREATE TABLE IF NOT EXISTS ai_agent_kernel_browser_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_key VARCHAR(128) NOT NULL COMMENT '稳定 userId',
    kernel_session_id VARCHAR(64) NOT NULL COMMENT 'Kernel browser session_id',
    kernel_browser_name VARCHAR(255) NOT NULL COMMENT '稳定 Kernel browser name',
    last_used_at DATETIME NOT NULL COMMENT '最近一次成功取得 CDP endpoint 的时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_kernel_browser_owner (owner_key),
    UNIQUE KEY uk_kernel_browser_session (kernel_session_id),
    UNIQUE KEY uk_kernel_browser_name (kernel_browser_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Reactor owner 与 Kernel 云端 browser 外部资源映射（非 Execution Ledger）';
