-- Session-monotonic SSE seq watermark. In-memory clock occupies numbers per frame;
-- this column is GREATEST-updated on durable frames / stream complete.

ALTER TABLE ai_agent_dialogue_session
    ADD COLUMN event_seq BIGINT NOT NULL DEFAULT 0 COMMENT '会话内 SSE 高水位，delta 占号但不逐帧回写'
    AFTER last_active_at;
