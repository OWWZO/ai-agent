-- Remove the retired Agent/client configuration chain and rich output projections.
-- Run after deploying the code that no longer references these tables.

DROP TABLE IF EXISTS ai_agent_tool_output_planning;
DROP TABLE IF EXISTS ai_agent_tool_output_script_runner;
DROP TABLE IF EXISTS ai_agent_tool_output_report_tool;
DROP TABLE IF EXISTS ai_agent_tool_output_file_tool;

DROP TABLE IF EXISTS ai_client_system_prompt;
DROP TABLE IF EXISTS ai_client_rag_order;
DROP TABLE IF EXISTS ai_client_config;
DROP TABLE IF EXISTS ai_client_advisor;
DROP TABLE IF EXISTS ai_client;

DROP TABLE IF EXISTS ai_agent_task_schedule;
DROP TABLE IF EXISTS ai_agent_flow_config;
DROP TABLE IF EXISTS ai_agent_draw_config;
DROP TABLE IF EXISTS ai_agent;
