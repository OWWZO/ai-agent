-- RequestDesktopControl HITL 附属状态。
-- Apply this migration separately to the live ai-agent-station database.

CREATE TABLE IF NOT EXISTS ai_agent_desktop_control (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    control_id           VARCHAR(64)    NOT NULL COMMENT '桌面控制对外ID',
    visitor_id           VARCHAR(64)    NULL COMMENT '访客ID',
    session_id           VARCHAR(64)    NOT NULL COMMENT '会话ID',
    owner_key            VARCHAR(128)   NULL COMMENT '沙箱 ownerKey',
    source_run_id        BIGINT         NULL COMMENT '源 dialogue_run.id',
    source_request_id    VARCHAR(64)    NOT NULL COMMENT '源 requestId',
    tool_invocation_id   BIGINT         NULL COMMENT '源 tool_invocation.id',
    tool_call_id         VARCHAR(128)   NULL COMMENT '模型 toolCallId',
    reason               VARCHAR(1024)  NULL COMMENT '给用户看的原因',
    stream_url           VARCHAR(2048)  NULL COMMENT 'noVNC URL（仅前端；observation 不用）',
    hold_until           DOUBLE         NULL COMMENT '用户操作 hold 到期 unix 秒',
    status               VARCHAR(32)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|RESUME_PENDING|RESUMING|COMPLETED|CANCELLED|FAILED',
    resume_request_id    VARCHAR(64)    NULL COMMENT '续跑 requestId',
    resume_context_json  JSON           NULL COMMENT '瘦续跑上下文（agentId/entryAgent/PlanMode）',
    create_time          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_desktop_control_id (control_id, deleted),
    UNIQUE KEY uk_desktop_control_resume (resume_request_id, deleted),
    KEY idx_desktop_control_session_status (session_id, status, deleted, create_time DESC),
    KEY idx_desktop_control_visitor (visitor_id, deleted, create_time DESC),
    KEY idx_desktop_control_source_request (source_request_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RequestDesktopControl 交互附属状态（非第二账本）';
