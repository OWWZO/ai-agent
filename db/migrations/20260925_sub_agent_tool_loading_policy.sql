-- 子 Agent 可独立配置工具继承/自定义策略及 deferred 工具集合。
ALTER TABLE ai_agent_sub_agent_definition
    ADD COLUMN tool_policy_mode VARCHAR(16) NOT NULL DEFAULT 'inherit'
        COMMENT '工具装配策略：inherit|custom' AFTER disallowed_tools_json,
    ADD COLUMN deferred_tools_json JSON NULL
        COMMENT 'custom 模式下延迟加载的工具名 JSON 数组' AFTER tool_policy_mode;
