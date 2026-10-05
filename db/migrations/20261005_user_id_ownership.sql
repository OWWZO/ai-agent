-- Rename runtime ownership columns from anonymous visitor identity to account user id.
-- Existing values are preserved; this migration does not infer account mappings.

ALTER TABLE ai_agent_dialogue_run
    CHANGE COLUMN visitor_id user_id VARCHAR(64) NULL COMMENT '用户ID',
    DROP INDEX idx_dialogue_run_visitor_create,
    ADD INDEX idx_dialogue_run_user_create (user_id, deleted, create_time DESC);

ALTER TABLE ai_agent_dialogue_session
    CHANGE COLUMN visitor_id user_id VARCHAR(64) NULL COMMENT '用户ID',
    DROP INDEX idx_dialogue_session_visitor_active,
    ADD INDEX idx_dialogue_session_user_active (user_id, deleted, last_active_at DESC);

ALTER TABLE ai_agent_user_question
    CHANGE COLUMN visitor_id user_id VARCHAR(64) NULL COMMENT '用户ID',
    DROP INDEX idx_user_question_visitor,
    ADD INDEX idx_user_question_user (user_id, deleted, create_time DESC);

ALTER TABLE ai_agent_desktop_control
    CHANGE COLUMN visitor_id user_id VARCHAR(64) NULL COMMENT '用户ID',
    DROP INDEX idx_desktop_control_visitor,
    ADD INDEX idx_desktop_control_user (user_id, deleted, create_time DESC);

ALTER TABLE ai_agent_plan_approval
    CHANGE COLUMN visitor_id user_id VARCHAR(64) NULL COMMENT '用户ID',
    DROP INDEX idx_plan_approval_visitor,
    ADD INDEX idx_plan_approval_user (user_id, deleted, create_time DESC);
