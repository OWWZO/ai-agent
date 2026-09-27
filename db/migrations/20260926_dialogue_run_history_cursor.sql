-- Keyset pagination for session history runs.
-- Apply this migration separately to the live ai-agent-station database.

ALTER TABLE ai_agent_dialogue_run
    ADD KEY idx_dialogue_session_cursor (session_id, deleted, create_time, id);
