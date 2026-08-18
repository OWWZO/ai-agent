-- =============================================================================
-- Reactor-agent persistence source of truth
--
-- Conversation / history / memory / replay main path = Execution Ledger only:
--   ai_agent_dialogue_session, ai_agent_dialogue_run,
--   ai_agent_llm_invocation, ai_agent_tool_invocation,
--   ai_agent_artifact, ai_agent_tool_output_*,
--   ai_agent_visitor_identity, ai_agent_featured_conversation
--
-- Agent assembly config (not execution facts):
--   ai_agent_sub_agent_definition — custom SubAgent definitions for Agent tool
--
-- Session task surfaces (not ledger facts):
--   ai_agent_session_todo — Todo V2 list (TaskCreate/List)
--   ai_agent_background_task — background Agent/shell tasks (TaskOutput/Stop)
--
-- Do NOT add as a second main path:
--   ai_agent_message*, ai_agent_turn, ai_agent_transcript_block,
--   ai_agent_display_event, ai_agent_session_memory
-- =============================================================================

CREATE TABLE IF NOT EXISTS admin_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id     VARCHAR(64)  NOT NULL COMMENT '用户ID（唯一标识）',
    username    VARCHAR(50)  NOT NULL COMMENT '用户名（登录账号）',
    password    VARCHAR(128) NOT NULL COMMENT '密码（加密存储）',
    status      TINYINT(1)   NULL DEFAULT 1 COMMENT '状态(0:禁用,1:启用,2:锁定)',
    create_time DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id),
    KEY idx_create_time (create_time),
    KEY idx_status (status)
) COMMENT='管理员用户表';

CREATE TABLE IF NOT EXISTS ai_agent (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    agent_id    VARCHAR(64)  NOT NULL COMMENT '智能体ID',
    agent_name  VARCHAR(50)  NOT NULL COMMENT '智能体名称',
    description VARCHAR(255) NULL COMMENT '描述',
    channel     VARCHAR(32)  NULL COMMENT '渠道类型(agent，chat_stream)',
    strategy    VARCHAR(64)  NULL COMMENT '执行策略(auto、flow)',
    status      TINYINT(1)   NULL DEFAULT 1 COMMENT '状态(0:禁用,1:启用)',
    create_time DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_id (agent_id)
) COMMENT='AI智能体配置表';

CREATE TABLE IF NOT EXISTS ai_agent_draw_config (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    config_id   VARCHAR(64)   NOT NULL COMMENT '配置ID（唯一标识）',
    config_name VARCHAR(100)  NOT NULL COMMENT '配置名称',
    description VARCHAR(500)  NULL COMMENT '配置描述',
    agent_id    VARCHAR(64)   NULL COMMENT '关联的智能体ID（来自ai_agent表）',
    config_data LONGTEXT      NOT NULL COMMENT '完整的拖拉拽配置JSON数据（包含nodes和edges）',
    version     INT           NULL DEFAULT 1 COMMENT '配置版本号',
    status      TINYINT(1)    NULL DEFAULT 1 COMMENT '状态(0:禁用,1:启用)',
    create_by   VARCHAR(64)   NULL COMMENT '创建人',
    update_by   VARCHAR(64)   NULL COMMENT '更新人',
    create_time DATETIME      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_id (config_id),
    KEY idx_agent_id (agent_id),
    KEY idx_config_name (config_name),
    KEY idx_status (status)
) COMMENT='AI智能体拖拉拽配置主表';

CREATE TABLE IF NOT EXISTS ai_agent_flow_config (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    agent_id    VARCHAR(64)  NOT NULL COMMENT '智能体ID',
    client_id   VARCHAR(64)  NOT NULL COMMENT '客户端ID',
    client_name VARCHAR(64)  NULL COMMENT '客户端名称',
    client_type VARCHAR(64)  NULL COMMENT '客户端类型',
    sequence    INT          NOT NULL COMMENT '序列号(执行顺序)',
    step_prompt TEXT         NULL COMMENT '步骤提示词',
    status      INT          NULL DEFAULT 1 COMMENT '状态；0无效，1有效',
    create_time DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_client_seq (agent_id, client_id, sequence)
) COMMENT='智能体-客户端关联表';

CREATE TABLE IF NOT EXISTS ai_agent_task_schedule (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    agent_id        BIGINT       NOT NULL COMMENT '智能体ID',
    task_name       VARCHAR(64)  NULL COMMENT '任务名称',
    description     VARCHAR(255) NULL COMMENT '任务描述',
    cron_expression VARCHAR(50)  NOT NULL COMMENT '时间表达式(如: 0/3 * * * * *)',
    task_param      TEXT         NULL COMMENT '任务入参配置(JSON格式)',
    status          TINYINT(1)   NULL DEFAULT 1 COMMENT '状态(0:无效,1:有效)',
    create_time     DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_agent_id (agent_id)
) COMMENT='智能体任务调度配置表';

CREATE TABLE IF NOT EXISTS ai_client (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    client_id   VARCHAR(64)   NOT NULL COMMENT '客户端ID',
    client_name VARCHAR(50)   NOT NULL COMMENT '客户端名称',
    description VARCHAR(1024) NULL COMMENT '描述',
    status      TINYINT(1)    NULL DEFAULT 1 COMMENT '状态(0:禁用,1:启用)',
    create_time DATETIME      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY client_id (client_id)
) COMMENT='AI客户端配置表';

CREATE TABLE IF NOT EXISTS ai_client_advisor (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    advisor_id   VARCHAR(64)   NOT NULL COMMENT '顾问ID',
    advisor_name VARCHAR(50)   NOT NULL COMMENT '顾问名称',
    advisor_type VARCHAR(50)   NOT NULL COMMENT '顾问类型(PromptChatMemory/RagAnswer/SimpleLoggerAdvisor等)',
    order_num    INT           NULL DEFAULT 0 COMMENT '顺序号',
    ext_param    VARCHAR(2048) NULL COMMENT '扩展参数配置，json 记录',
    status       TINYINT(1)    NULL DEFAULT 1 COMMENT '状态(0:禁用,1:启用)',
    create_time  DATETIME      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_advisor_id (advisor_id)
) COMMENT='顾问配置表';

CREATE TABLE IF NOT EXISTS ai_client_api (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增主键ID',
    api_id           VARCHAR(64)   NOT NULL COMMENT '全局唯一配置ID',
    base_url         VARCHAR(255)  NOT NULL COMMENT 'API基础URL',
    api_key          VARCHAR(255)  NOT NULL COMMENT 'API密钥',
    completions_path VARCHAR(255)  NOT NULL COMMENT '补全API路径',
    embeddings_path  VARCHAR(255)  NOT NULL COMMENT '嵌入API路径',
    status           TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_id (api_id),
    KEY idx_status (status)
) COMMENT='OpenAI API配置表';

CREATE TABLE IF NOT EXISTS ai_client_config (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    source_type VARCHAR(32)   NOT NULL COMMENT '源类型（model、client）',
    source_id   VARCHAR(64)   NOT NULL COMMENT '源ID（如 chatModelId、chatClientId 等）',
    target_type VARCHAR(32)   NOT NULL COMMENT '目标类型(tool_mcp,advisor,prompt,model）',
    target_id   VARCHAR(64)   NOT NULL COMMENT '目标ID（如 openAiApiId、chatModelId、systemPromptId、advisorId 等）',
    ext_param   VARCHAR(1024) NULL COMMENT '扩展参数（JSON格式）',
    status      TINYINT(1)    NULL DEFAULT 1 COMMENT '状态(0:禁用,1:启用)',
    create_time DATETIME      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_source_id (source_id),
    KEY idx_target_id (target_id)
) COMMENT='AI客户端统一关联配置表';

CREATE TABLE IF NOT EXISTS ai_agent_session_capability (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id  VARCHAR(64)  NOT NULL COMMENT '会话 ID',
    kind        VARCHAR(16)  NOT NULL COMMENT 'skill|mcp',
    ref_id      VARCHAR(128) NOT NULL COMMENT 'skill name 或 mcpId',
    enabled     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 关 1 开',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sess_kind_ref (session_id, kind, ref_id),
    KEY idx_session (session_id)
) COMMENT='会话能力差集开关';

CREATE TABLE IF NOT EXISTS ai_client_model (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增主键ID',
    model_id    VARCHAR(64)   NOT NULL COMMENT '全局唯一模型ID',
    api_id      VARCHAR(64)   NOT NULL COMMENT '关联的API配置ID',
    model_usage VARCHAR(128)  NOT NULL DEFAULT '缺省的' COMMENT '模型用途',
    model_name  VARCHAR(64)   NOT NULL COMMENT '模型名称',
    model_type  VARCHAR(32)   NOT NULL COMMENT '模型类型：openai、deepseek、claude',
    supports_thinking TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持深度思考',
    context_window INT NULL DEFAULT NULL COMMENT '上下文窗口 token',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_model_id (model_id),
    KEY idx_api_config_id (api_id),
    KEY idx_status (status)
) COMMENT='聊天模型配置表';

CREATE TABLE IF NOT EXISTS ai_client_rag_order (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rag_id        VARCHAR(50)  NOT NULL COMMENT '知识库ID',
    rag_name      VARCHAR(50)  NOT NULL COMMENT '知识库名称',
    knowledge_tag VARCHAR(50)  NOT NULL COMMENT '知识标签',
    status        TINYINT(1)   NULL DEFAULT 1 COMMENT '状态(0:禁用,1:启用)',
    create_time   DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rag_id (rag_id)
) COMMENT='知识库配置表';

CREATE TABLE IF NOT EXISTS ai_client_system_prompt (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    prompt_id      VARCHAR(64)   NOT NULL COMMENT '提示词ID',
    prompt_name    VARCHAR(128)  NOT NULL COMMENT '提示词名称',
    prompt_content TEXT          NOT NULL COMMENT '提示词内容',
    description    VARCHAR(1024) NULL COMMENT '描述',
    status         TINYINT(1)    NULL DEFAULT 1 COMMENT '状态(0:禁用,1:启用)',
    create_time    DATETIME      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_prompt_id (prompt_id)
) COMMENT='系统提示词配置表';

CREATE TABLE IF NOT EXISTS ai_client_tool_mcp (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    mcp_id           VARCHAR(64)   NOT NULL COMMENT 'MCP名称',
    mcp_name         VARCHAR(50)   NOT NULL COMMENT 'MCP名称',
    transport_type   VARCHAR(20)   NOT NULL COMMENT '传输类型(sse/stdio)',
    transport_config VARCHAR(1024) NULL COMMENT '传输配置(sse/stdio)',
    request_timeout  INT           NULL DEFAULT 180 COMMENT '请求超时时间(分钟)',
    status           TINYINT(1)    NULL DEFAULT 1 COMMENT '状态(0:禁用,1:启用)',
    create_time      DATETIME      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mcp_id (mcp_id)
) COMMENT='MCP客户端配置表';

CREATE TABLE IF NOT EXISTS ai_agent_sub_agent_definition (
    id                      BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    agent_key               VARCHAR(64)    NOT NULL COMMENT 'subagent_type 唯一键，如 code-reviewer',
    display_name            VARCHAR(128)   NULL COMMENT '展示名',
    when_to_use             VARCHAR(512)   NOT NULL COMMENT '何时使用（注入主 Agent 工具描述）',
    system_prompt           MEDIUMTEXT     NOT NULL COMMENT '子 Agent 系统提示词',
    allowed_tools_json      JSON           NULL COMMENT '允许工具名 JSON 数组；含 * 或空表示全部',
    disallowed_tools_json   JSON           NULL COMMENT '额外禁止工具名 JSON 数组',
    max_steps               INT            NULL COMMENT '最大步数；NULL 沿用 React 配置',
    status                  TINYINT        NOT NULL DEFAULT 1 COMMENT '1=启用,0=禁用',
    create_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                 TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sub_agent_key (agent_key, deleted),
    KEY idx_sub_agent_status (status, deleted, update_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可配置子 Agent 定义（Agent 工具调度）';

CREATE TABLE IF NOT EXISTS chat_model_info (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  code varchar(50) NOT NULL COMMENT '模型编码',
  type varchar(10) NOT NULL COMMENT '模型类型TABLE,SQL',
  name varchar(100) DEFAULT NULL COMMENT '模型名称',
  content text NOT NULL COMMENT '模型内容，表或者sql',
  use_prompt text COMMENT '模型使用说明',
  business_prompt text COMMENT '模型业务限定提示词',
  yn tinyint NOT NULL DEFAULT '1' COMMENT '是否有效',
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS chat_model_schema (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  model_code varchar(200) NOT NULL COMMENT '模型编码',
  column_id varchar(1000) NOT NULL COMMENT '字段唯一ID',
  column_name varchar(200) NOT NULL COMMENT '字段中文名',
  column_comment varchar(1000) NOT NULL COMMENT '字段描述',
  few_shot text COMMENT '值枚举逗号分隔',
  data_type varchar(20) DEFAULT NULL COMMENT '字段值类型',
  synonyms varchar(300) DEFAULT NULL COMMENT '同义词',
  vector_uuid varchar(400) DEFAULT NULL COMMENT '向量库数据id',
  default_recall tinyint NOT NULL DEFAULT '0' COMMENT '默认召回',
  analyze_suggest tinyint NOT NULL DEFAULT '0' COMMENT '分析建议0可选，-1禁止用于分析维度，1建议',
  yn tinyint NOT NULL DEFAULT '1' COMMENT '是否有效',
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS sales_data (
    row_id INT PRIMARY KEY COMMENT '行 ID',
    order_id VARCHAR(50) DEFAULT NULL COMMENT '订单 ID',
    order_date DATE  COMMENT '订单日期',
    ship_date DATE COMMENT '发货日期',
    ship_mode VARCHAR(50) DEFAULT NULL COMMENT '邮寄方式',
    customer_id VARCHAR(50) DEFAULT NULL COMMENT '客户 ID',
    customer_name VARCHAR(100) DEFAULT NULL COMMENT '客户名称',
    segment VARCHAR(50) DEFAULT NULL COMMENT '细分',
    city VARCHAR(100) DEFAULT NULL COMMENT '城市',
    state_province VARCHAR(100) DEFAULT NULL COMMENT '省/自治区',
    country VARCHAR(100) DEFAULT NULL COMMENT '国家',
    region VARCHAR(50) DEFAULT NULL COMMENT '地区',
    product_id VARCHAR(50) DEFAULT NULL COMMENT '产品 ID',
    category VARCHAR(50) DEFAULT NULL COMMENT '产品类别',
    sub_category VARCHAR(50) DEFAULT NULL COMMENT '产品子类别',
    product_name VARCHAR(255) DEFAULT NULL COMMENT '产品名称',
    sales DECIMAL(10, 4) DEFAULT NULL COMMENT '销售额',
    quantity INT DEFAULT NULL COMMENT '销售数量',
    discount DECIMAL(10, 4) DEFAULT NULL COMMENT '折扣',
    profit DECIMAL(10, 4) DEFAULT NULL COMMENT '利润'
);

CREATE TABLE IF NOT EXISTS ai_agent_visitor_identity (
    id               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    visitor_id       VARCHAR(64)    NOT NULL COMMENT '匿名访客ID',
    token_digest     VARCHAR(128)   NOT NULL COMMENT 'Cookie token 摘要',
    status           TINYINT        NOT NULL DEFAULT 1 COMMENT '1=有效,0=失效',
    first_seen_at    DATETIME(3)    NOT NULL COMMENT '首次访问时间',
    last_seen_at     DATETIME(3)    NOT NULL COMMENT '最近访问时间',
    last_ip          VARCHAR(128)   NULL COMMENT '最近访问IP',
    last_user_agent  VARCHAR(512)   NULL COMMENT '最近访问UA',
    username         VARCHAR(64)    NULL COMMENT '当前浏览器访客首次命名用户名',
    create_time      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_visitor_identity_visitor (visitor_id),
    UNIQUE KEY uk_visitor_identity_token (token_digest),
    KEY idx_visitor_identity_last_seen (deleted, last_seen_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='匿名访客身份表';

CREATE TABLE IF NOT EXISTS ai_agent_dialogue_run (
    id                      BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    run_uid                 VARCHAR(64)    NOT NULL COMMENT '对外稳定运行ID，首期复用 requestId',
    request_id              VARCHAR(64)    NOT NULL COMMENT '单次请求ID',
    session_id              VARCHAR(64)    NOT NULL COMMENT '会话ID',
    visitor_id              VARCHAR(64)    NULL COMMENT '匿名访客ID',
    entry_agent             VARCHAR(32)    NOT NULL COMMENT '入口执行链 react / plan_solve',
    status                  TINYINT        NOT NULL DEFAULT 0 COMMENT '0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT,4=STOPPED,5=WAITING_INPUT',
    query_text              MEDIUMTEXT     NULL COMMENT '用户原始问题',
    final_summary_text      MEDIUMTEXT     NULL COMMENT '最终总结文本',
    llm_call_count          INT            NOT NULL DEFAULT 0 COMMENT 'LLM 调用次数',
    tool_call_count         INT            NOT NULL DEFAULT 0 COMMENT '工具调用次数',
    artifact_count          INT            NOT NULL DEFAULT 0 COMMENT '产物数量',
    prompt_tokens_total     INT            NOT NULL DEFAULT 0 COMMENT 'LLM 输入 token 总量',
    completion_tokens_total INT            NOT NULL DEFAULT 0 COMMENT 'LLM 输出 token 总量',
    total_tokens_total      INT            NOT NULL DEFAULT 0 COMMENT 'LLM token 总量',
    error_code              VARCHAR(64)    NULL COMMENT '失败码',
    error_msg               TEXT           NULL COMMENT '失败信息',
    started_at              DATETIME(3)    NOT NULL COMMENT 'run 开始时间',
    finished_at             DATETIME(3)    NULL COMMENT 'run 结束时间',
    duration_ms             BIGINT         NULL COMMENT 'run 耗时毫秒',
    create_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                 TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dialogue_run_uid (run_uid),
    UNIQUE KEY uk_dialogue_request_id (request_id),
    KEY idx_dialogue_session_create (session_id, deleted, create_time DESC),
    KEY idx_dialogue_run_visitor_create (visitor_id, deleted, create_time DESC),
    KEY idx_dialogue_entry_status (entry_agent, status, deleted, create_time DESC),
    FULLTEXT KEY ft_dialogue_run_query_summary (query_text, final_summary_text) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话执行总账表';

CREATE TABLE IF NOT EXISTS ai_agent_dialogue_session (
    id                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    session_id         VARCHAR(64)    NOT NULL COMMENT '会话ID',
    visitor_id         VARCHAR(64)    NULL COMMENT '匿名访客ID',
    title              VARCHAR(256)   NOT NULL COMMENT '会话标题',
    status             TINYINT        NOT NULL DEFAULT 0 COMMENT '会话终态，复用 run 状态枚举',
    latest_request_id  VARCHAR(64)    NULL COMMENT '最近一次请求ID',
    latest_query_text  MEDIUMTEXT     NULL COMMENT '最近一次问题预览',
    latest_summary_text MEDIUMTEXT    NULL COMMENT '最近一次总结文本',
    run_count          INT            NOT NULL DEFAULT 0 COMMENT '会话总轮次',
    finished_run_count INT            NOT NULL DEFAULT 0 COMMENT '成功轮次',
    failed_run_count   INT            NOT NULL DEFAULT 0 COMMENT '失败/停止/超时轮次',
    started_at         DATETIME(3)    NULL COMMENT '首轮开始时间',
    last_active_at     DATETIME(3)    NULL COMMENT '最近活跃时间',
    create_time        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dialogue_session_id (session_id, deleted),
    KEY idx_dialogue_session_visitor_active (visitor_id, deleted, last_active_at DESC),
    KEY idx_dialogue_session_active (deleted, last_active_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话主表';

CREATE TABLE IF NOT EXISTS ai_agent_llm_invocation (
    id                BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    run_id            BIGINT         NOT NULL COMMENT '所属 run ID',
    invocation_seq    INT            NOT NULL COMMENT 'run 内递增序号',
    agent_name        VARCHAR(32)    NOT NULL COMMENT '当前 agent 名称',
    step_no           INT            NULL COMMENT '当前步号',
    call_kind         VARCHAR(16)    NOT NULL COMMENT 'ask / askTool',
    streaming         TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否流式',
    model_name        VARCHAR(128)   NULL COMMENT '模型名',
    response_text     MEDIUMTEXT     NULL COMMENT '完整响应文本(content)',
    reasoning_content MEDIUMTEXT     NULL COMMENT '模型原生 CoT / reasoning_content',
    tool_call_count   INT            NOT NULL DEFAULT 0 COMMENT '工具调用数量',
    prompt_tokens     INT            NOT NULL DEFAULT 0 COMMENT 'prompt token',
    completion_tokens INT            NOT NULL DEFAULT 0 COMMENT 'completion token',
    total_tokens      INT            NOT NULL DEFAULT 0 COMMENT 'total token',
    cached_prompt_tokens INT           NULL COMMENT 'prompt_tokens_details.cached_tokens',
    prompt_text_tokens   INT           NULL COMMENT 'prompt_tokens_details.text_tokens',
    prompt_audio_tokens  INT           NULL COMMENT 'prompt_tokens_details.audio_tokens',
    prompt_image_tokens  INT           NULL COMMENT 'prompt_tokens_details.image_tokens',
    completion_text_tokens  INT        NULL COMMENT 'completion_tokens_details.text_tokens',
    completion_audio_tokens INT        NULL COMMENT 'completion_tokens_details.audio_tokens',
    reasoning_tokens        INT        NULL COMMENT 'completion_tokens_details.reasoning_tokens',
    system_fingerprint  VARCHAR(64)    NULL COMMENT 'system 指纹',
    est_total_tokens    INT            NULL COMMENT '粗估总 token',
    est_system_tokens   INT            NULL COMMENT '粗估 system token',
    est_message_tokens  INT            NULL COMMENT '粗估 messages token',
    est_tool_tokens     INT            NULL COMMENT '粗估 tools token',
    message_count       INT            NULL COMMENT 'messages 条数',
    tool_count          INT            NULL COMMENT 'tools 数量',
    cache_status        VARCHAR(32)    NULL COMMENT 'OK/RISK/MISS/UNKNOWN',
    cache_risk_flags    VARCHAR(256)   NULL COMMENT 'systemChanged,toolsChanged,...',
    finish_reason     VARCHAR(32)    NULL COMMENT '完成原因',
    status            TINYINT        NOT NULL DEFAULT 0 COMMENT '0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT',
    error_msg         TEXT           NULL COMMENT '错误信息',
    started_at        DATETIME(3)    NOT NULL COMMENT '开始时间',
    finished_at       DATETIME(3)    NULL COMMENT '结束时间',
    duration_ms       BIGINT         NULL COMMENT '耗时毫秒',
    create_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_invocation_seq (run_id, invocation_seq),
    KEY idx_llm_run_seq (run_id, deleted, invocation_seq),
    KEY idx_llm_model_create (model_name, deleted, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM 调用账本表';

CREATE TABLE IF NOT EXISTS ai_agent_tool_invocation (
    id                BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    run_id            BIGINT         NOT NULL COMMENT '所属 run ID',
    llm_invocation_id BIGINT         NOT NULL COMMENT '来源 LLM invocation ID',
    tool_call_id      VARCHAR(128)   NOT NULL COMMENT '模型返回的 toolCallId',
    parent_tool_call_id VARCHAR(128) NULL COMMENT '父 Agent 工具 toolCallId（子 Agent 嵌套）',
    sub_agent_id      VARCHAR(64)    NULL COMMENT '子 Agent 运行时 id',
    sub_agent_type    VARCHAR(64)    NULL COMMENT '子 Agent 类型',
    sub_agent_description VARCHAR(256) NULL COMMENT '子 Agent 任务短描述',
    dispatch_index    INT            NOT NULL COMMENT '模型原始分发顺序',
    agent_name        VARCHAR(32)    NOT NULL COMMENT '当前 agent 名称',
    step_no           INT            NULL COMMENT '当前步号',
    tool_name         VARCHAR(128)   NOT NULL COMMENT '工具名称',
    tool_provider     VARCHAR(64)    NULL COMMENT '工具提供方 local / mcp',
    input_json        JSON           NOT NULL COMMENT '工具入参 JSON',
    llm_oberserve     MEDIUMTEXT     NULL COMMENT '回传给主智能体的最终 observation',
    status            TINYINT        NOT NULL DEFAULT 0 COMMENT '0=RUNNING,1=SUCCESS,2=FAILED,3=TIMEOUT,5=WAITING_INPUT',
    error_msg         TEXT           NULL COMMENT '错误信息',
    started_at        DATETIME(3)    NOT NULL COMMENT '开始时间',
    finished_at       DATETIME(3)    NULL COMMENT '结束时间',
    duration_ms       BIGINT         NULL COMMENT '耗时毫秒',
    create_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_tool_call (run_id, tool_call_id),
    UNIQUE KEY uk_llm_dispatch (llm_invocation_id, dispatch_index),
    KEY idx_tool_run_create (run_id, deleted, create_time DESC),
    KEY idx_tool_name_create (tool_name, deleted, create_time DESC),
    KEY idx_tool_parent_call (run_id, parent_tool_call_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具调用账本表';

CREATE TABLE IF NOT EXISTS ai_agent_user_question (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    question_id          VARCHAR(64)    NOT NULL COMMENT '问题对外ID',
    visitor_id           VARCHAR(64)    NULL COMMENT '访客ID',
    session_id           VARCHAR(64)    NOT NULL COMMENT '会话ID',
    source_run_id        BIGINT         NULL COMMENT '源 dialogue_run.id',
    source_request_id    VARCHAR(64)    NOT NULL COMMENT '源 requestId',
    tool_invocation_id   BIGINT         NULL COMMENT '源 tool_invocation.id',
    tool_call_id         VARCHAR(128)   NULL COMMENT '模型 toolCallId',
    questions_json       JSON           NOT NULL COMMENT '题目 JSON',
    answers_json         JSON           NULL COMMENT '答案 JSON',
    status               VARCHAR(32)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|RESUME_PENDING|RESUMING|ANSWERED|TIMEOUT|CANCELLED|FAILED',
    expires_at           DATETIME(3)    NULL COMMENT '过期时间',
    resume_request_id    VARCHAR(64)    NULL COMMENT '续跑 requestId',
    resume_context_json  JSON           NULL COMMENT '瘦续跑上下文（agentId/entryAgent/PlanMode）',
    create_time          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_question_id (question_id, deleted),
    UNIQUE KEY uk_user_question_resume (resume_request_id, deleted),
    KEY idx_user_question_session_status (session_id, status, deleted, create_time DESC),
    KEY idx_user_question_visitor (visitor_id, deleted, create_time DESC),
    KEY idx_user_question_source_request (source_request_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AskUserQuestion 交互附属状态（非第二账本）';

CREATE TABLE IF NOT EXISTS ai_agent_plan_approval (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    approval_id          VARCHAR(64)    NOT NULL COMMENT '审批对外ID',
    visitor_id           VARCHAR(64)    NULL COMMENT '访客ID',
    session_id           VARCHAR(64)    NOT NULL COMMENT '会话ID',
    source_run_id        BIGINT         NULL COMMENT '源 dialogue_run.id',
    source_request_id    VARCHAR(64)    NOT NULL COMMENT '源 requestId',
    tool_invocation_id   BIGINT         NULL COMMENT '源 tool_invocation.id',
    tool_call_id         VARCHAR(128)   NULL COMMENT '模型 toolCallId',
    plan_content         MEDIUMTEXT     NOT NULL COMMENT '提交审批的计划正文',
    plan_file_path       VARCHAR(512)   NULL COMMENT '计划文件路径',
    decision_json        JSON           NULL COMMENT '决策 JSON（approved/feedback/editedPlanContent）',
    status               VARCHAR(32)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|RESUME_PENDING|RESUMING|ANSWERED|TIMEOUT|CANCELLED|FAILED',
    expires_at           DATETIME(3)    NULL COMMENT '过期时间',
    resume_request_id    VARCHAR(64)    NULL COMMENT '续跑 requestId',
    resume_context_json  JSON           NULL COMMENT '瘦续跑上下文（agentId/entryAgent/PlanMode）',
    create_time          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_approval_id (approval_id, deleted),
    UNIQUE KEY uk_plan_approval_resume (resume_request_id, deleted),
    KEY idx_plan_approval_session_status (session_id, status, deleted, create_time DESC),
    KEY idx_plan_approval_visitor (visitor_id, deleted, create_time DESC),
    KEY idx_plan_approval_source_request (source_request_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ExitPlanMode 计划审批附属状态（非第二账本）';

CREATE TABLE IF NOT EXISTS ai_agent_tool_output_deep_search (
    id                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id BIGINT         NULL COMMENT '所属 tool invocation ID',
    run_id             BIGINT         NULL COMMENT '所属 run ID',
    request_id         VARCHAR(64)    NOT NULL COMMENT '请求ID',
    session_id         VARCHAR(64)    NULL COMMENT '会话ID',
    tool_call_id       VARCHAR(128)   NOT NULL COMMENT 'toolCallId',
    status             TINYINT        NOT NULL COMMENT '终态状态',
    error_msg          TEXT           NULL COMMENT '错误信息',
    query              VARCHAR(512)   NULL COMMENT '原始查询',
    answer_summary     TEXT           NULL COMMENT '回答摘要',
    stages_json        JSON           NULL COMMENT '阶段结果 JSON',
    created_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_invocation (tool_invocation_id),
    UNIQUE KEY uk_request_tool_call (request_id, tool_call_id),
    KEY idx_run_created (run_id, created_at DESC),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='deep_search 输出表';

CREATE TABLE IF NOT EXISTS ai_agent_tool_output_file_tool (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id   BIGINT         NULL COMMENT '所属 tool invocation ID',
    run_id               BIGINT         NULL COMMENT '所属 run ID',
    request_id           VARCHAR(64)    NOT NULL COMMENT '请求ID',
    session_id           VARCHAR(64)    NULL COMMENT '会话ID',
    tool_call_id         VARCHAR(128)   NOT NULL COMMENT 'toolCallId',
    status               TINYINT        NOT NULL COMMENT '终态状态',
    error_msg            TEXT           NULL COMMENT '错误信息',
    command              VARCHAR(32)    NULL COMMENT '工具命令',
    primary_file_name    VARCHAR(256)   NULL COMMENT '主文件名',
    content_storage_mode VARCHAR(32)    NULL COMMENT '内容存储模式',
    created_at           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    content              MEDIUMTEXT     NULL COMMENT '正文内容',
    preview_url          VARCHAR(1024)  NULL COMMENT '预览地址',
    download_url         VARCHAR(1024)  NULL COMMENT '下载地址',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_invocation (tool_invocation_id),
    UNIQUE KEY uk_request_tool_call (request_id, tool_call_id),
    KEY idx_run_created (run_id, created_at DESC),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='file_tool 输出表';

CREATE TABLE IF NOT EXISTS ai_agent_tool_output_code_interpreter (
    id                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id BIGINT         NULL COMMENT '所属 tool invocation ID',
    run_id             BIGINT         NULL COMMENT '所属 run ID',
    request_id         VARCHAR(64)    NOT NULL COMMENT '请求ID',
    session_id         VARCHAR(64)    NULL COMMENT '会话ID',
    tool_call_id       VARCHAR(128)   NOT NULL COMMENT 'toolCallId',
    status             TINYINT        NOT NULL COMMENT '终态状态',
    error_msg          TEXT           NULL COMMENT '错误信息',
    code_output        MEDIUMTEXT     NULL COMMENT '代码执行输出',
    content            MEDIUMTEXT     NULL COMMENT '正文内容',
    code               MEDIUMTEXT     NULL COMMENT '执行代码',
    `explain`          MEDIUMTEXT     NULL COMMENT '补充解释',
    created_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_invocation (tool_invocation_id),
    UNIQUE KEY uk_request_tool_call (request_id, tool_call_id),
    KEY idx_run_created (run_id, created_at DESC),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='code_interpreter 输出表';

CREATE TABLE IF NOT EXISTS ai_agent_tool_output_report_tool (
    id                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id BIGINT         NULL COMMENT '所属 tool invocation ID',
    run_id             BIGINT         NULL COMMENT '所属 run ID',
    request_id         VARCHAR(64)    NOT NULL COMMENT '请求ID',
    session_id         VARCHAR(64)    NULL COMMENT '会话ID',
    tool_call_id       VARCHAR(128)   NOT NULL COMMENT 'toolCallId',
    status             TINYINT        NOT NULL COMMENT '终态状态',
    error_msg          TEXT           NULL COMMENT '错误信息',
    file_type          VARCHAR(32)    NULL COMMENT '文件类型',
    summary            TEXT           NULL COMMENT '摘要',
    content            MEDIUMTEXT     NULL COMMENT '报告正文',
    created_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_invocation (tool_invocation_id),
    UNIQUE KEY uk_request_tool_call (request_id, tool_call_id),
    KEY idx_run_created (run_id, created_at DESC),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='report_tool 输出表';

CREATE TABLE IF NOT EXISTS ai_agent_tool_output_data_analysis (
    id                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id BIGINT         NULL COMMENT '所属 tool invocation ID',
    run_id             BIGINT         NULL COMMENT '所属 run ID',
    request_id         VARCHAR(64)    NOT NULL COMMENT '请求ID',
    session_id         VARCHAR(64)    NULL COMMENT '会话ID',
    tool_call_id       VARCHAR(128)   NOT NULL COMMENT 'toolCallId',
    status             TINYINT        NOT NULL COMMENT '终态状态',
    error_msg          TEXT           NULL COMMENT '错误信息',
    task               TEXT           NULL COMMENT '任务描述',
    summary            TEXT           NULL COMMENT '摘要',
    content            MEDIUMTEXT     NULL COMMENT '分析正文',
    created_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_invocation (tool_invocation_id),
    UNIQUE KEY uk_request_tool_call (request_id, tool_call_id),
    KEY idx_run_created (run_id, created_at DESC),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='data_analysis 输出表';

CREATE TABLE IF NOT EXISTS ai_agent_tool_output_multimodal_agent (
    id                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id BIGINT         NULL COMMENT '所属 tool invocation ID',
    run_id             BIGINT         NULL COMMENT '所属 run ID',
    request_id         VARCHAR(64)    NOT NULL COMMENT '请求ID',
    session_id         VARCHAR(64)    NULL COMMENT '会话ID',
    tool_call_id       VARCHAR(128)   NOT NULL COMMENT 'toolCallId',
    status             TINYINT        NOT NULL COMMENT '终态状态',
    error_msg          TEXT           NULL COMMENT '错误信息',
    summary            TEXT           NULL COMMENT '摘要',
    markdown_content   MEDIUMTEXT     NULL COMMENT 'Markdown 正文',
    created_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_invocation (tool_invocation_id),
    UNIQUE KEY uk_request_tool_call (request_id, tool_call_id),
    KEY idx_run_created (run_id, created_at DESC),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='multimodal_agent 输出表';

CREATE TABLE IF NOT EXISTS ai_agent_tool_output_image_generation (
    id                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id BIGINT         NULL COMMENT '所属 tool invocation ID',
    run_id             BIGINT         NULL COMMENT '所属 run ID',
    request_id         VARCHAR(64)    NOT NULL COMMENT '请求ID',
    request_source     VARCHAR(32)    NOT NULL COMMENT '请求来源 agent/workspace',
    session_id         VARCHAR(64)    NULL COMMENT '会话ID',
    tool_call_id       VARCHAR(128)   NOT NULL COMMENT 'toolCallId',
    status             TINYINT        NOT NULL COMMENT '终态状态',
    error_msg          TEXT           NULL COMMENT '错误信息',
    prompt             TEXT           NULL COMMENT '提示词',
    mode               VARCHAR(32)    NULL COMMENT '模式',
    summary            TEXT           NULL COMMENT '摘要',
    size               VARCHAR(32)    NULL COMMENT '输出尺寸',
    batch_count        INT            NULL COMMENT '批次数量',
    source_image_count INT            NULL COMMENT '参考图数量',
    mask_image_count   INT            NULL COMMENT '蒙版图数量',
    used_fallback      TINYINT(1)     NULL COMMENT '是否走降级路径',
    created_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_invocation (tool_invocation_id),
    UNIQUE KEY uk_request_tool_call (request_id, tool_call_id),
    KEY idx_request_source_created (request_source, created_at DESC),
    KEY idx_run_created (run_id, created_at DESC),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='image_generation_tool 输出表';

CREATE TABLE IF NOT EXISTS ai_agent_tool_output_script_runner (
    id                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id BIGINT         NULL COMMENT '所属 tool invocation ID',
    run_id             BIGINT         NULL COMMENT '所属 run ID',
    request_id         VARCHAR(64)    NOT NULL COMMENT '请求ID',
    session_id         VARCHAR(64)    NULL COMMENT '会话ID',
    tool_call_id       VARCHAR(128)   NOT NULL COMMENT 'toolCallId',
    status             TINYINT        NOT NULL COMMENT '终态状态',
    error_msg          TEXT           NULL COMMENT '错误信息',
    skill_name         VARCHAR(128)   NULL COMMENT '技能名',
    script_name        VARCHAR(128)   NULL COMMENT '脚本名',
    runtime            VARCHAR(32)    NULL COMMENT '运行时',
    success            TINYINT(1)     NULL COMMENT '脚本是否成功',
    exit_code          INT            NULL COMMENT '退出码',
    stdout             MEDIUMTEXT     NULL COMMENT '标准输出',
    stderr             MEDIUMTEXT     NULL COMMENT '标准错误',
    summary            TEXT           NULL COMMENT '摘要',
    created_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_invocation (tool_invocation_id),
    UNIQUE KEY uk_request_tool_call (request_id, tool_call_id),
    KEY idx_run_created (run_id, created_at DESC),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='script_runner_tool 输出表';


CREATE TABLE IF NOT EXISTS ai_agent_tool_output_canvas_publish (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id   BIGINT         NULL COMMENT '所属 tool invocation ID',
    run_id               BIGINT         NULL COMMENT '所属 run ID',
    request_id           VARCHAR(64)    NOT NULL COMMENT '请求ID',
    session_id           VARCHAR(64)    NULL COMMENT '会话ID',
    tool_call_id         VARCHAR(128)   NOT NULL COMMENT 'toolCallId',
    status               TINYINT        NOT NULL COMMENT '终态状态',
    error_msg            TEXT           NULL COMMENT '错误信息',
    title                VARCHAR(500)   NULL COMMENT '画布标题',
    mode                 VARCHAR(32)    NULL COMMENT '发布模式 html/embed/gen_ui',
    primary_file_name    VARCHAR(256)   NULL COMMENT '主文件名',
    preview_url          VARCHAR(1024)  NULL COMMENT '预览地址',
    download_url         VARCHAR(1024)  NULL COMMENT '下载地址',
    open_in_panel        TINYINT        NULL COMMENT '是否打开面板',
    salvaged             TINYINT        NULL COMMENT '是否截断恢复',
    created_at           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_invocation (tool_invocation_id),
    UNIQUE KEY uk_request_tool_call (request_id, tool_call_id),
    KEY idx_run_created (run_id, created_at DESC),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='canvas_publish 输出表';

CREATE TABLE IF NOT EXISTS ai_agent_tool_output_emit_ui_tree (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id   BIGINT         NULL COMMENT '所属 tool invocation ID',
    run_id               BIGINT         NULL COMMENT '所属 run ID',
    request_id           VARCHAR(64)    NOT NULL COMMENT '请求ID',
    session_id           VARCHAR(64)    NULL COMMENT '会话ID',
    tool_call_id         VARCHAR(128)   NOT NULL COMMENT 'toolCallId',
    status               TINYINT        NOT NULL COMMENT '终态状态',
    error_msg            TEXT           NULL COMMENT '错误信息',
    canvas_id            VARCHAR(128)   NULL COMMENT '画布ID',
    salvaged             TINYINT        NULL COMMENT '是否截断恢复',
    tree_json            MEDIUMTEXT     NULL COMMENT 'GenUI tree JSON',
    created_at           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_invocation (tool_invocation_id),
    UNIQUE KEY uk_request_tool_call (request_id, tool_call_id),
    KEY idx_run_created (run_id, created_at DESC),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='emit_ui_tree 输出表';

CREATE TABLE IF NOT EXISTS ai_agent_tool_output_emit_ui_patch (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id   BIGINT         NULL COMMENT '所属 tool invocation ID',
    run_id               BIGINT         NULL COMMENT '所属 run ID',
    request_id           VARCHAR(64)    NOT NULL COMMENT '请求ID',
    session_id           VARCHAR(64)    NULL COMMENT '会话ID',
    tool_call_id         VARCHAR(128)   NOT NULL COMMENT 'toolCallId',
    status               TINYINT        NOT NULL COMMENT '终态状态',
    error_msg            TEXT           NULL COMMENT '错误信息',
    canvas_id            VARCHAR(128)   NULL COMMENT '画布ID',
    seq                  INT            NULL COMMENT '补丁序号',
    patches_json         MEDIUMTEXT     NULL COMMENT 'JSON Patch 数组',
    created_at           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_invocation (tool_invocation_id),
    UNIQUE KEY uk_request_tool_call (request_id, tool_call_id),
    KEY idx_run_created (run_id, created_at DESC),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='emit_ui_patch 输出表';

CREATE TABLE IF NOT EXISTS ai_agent_tool_output_planning (
    id                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tool_invocation_id BIGINT         NULL COMMENT '所属 tool invocation ID',
    run_id             BIGINT         NULL COMMENT '所属 run ID',
    request_id         VARCHAR(64)    NOT NULL COMMENT '请求ID',
    session_id         VARCHAR(64)    NULL COMMENT '会话ID',
    tool_call_id       VARCHAR(128)   NOT NULL COMMENT 'toolCallId',
    status             TINYINT        NOT NULL COMMENT '终态状态',
    error_msg          TEXT           NULL COMMENT '错误信息',
    command            VARCHAR(32)    NOT NULL COMMENT 'planning 命令',
    before_plan_json   JSON           NULL COMMENT '执行前计划快照',
    after_plan_json    JSON           NULL COMMENT '执行后计划快照',
    current_step       TEXT           NULL COMMENT '当前可执行步骤',
    current_step_index INT            NULL COMMENT '当前可执行步骤索引',
    auto_advanced      TINYINT(1)     NULL COMMENT '是否自动推进',
    auto_finished      TINYINT(1)     NULL COMMENT '是否自动结束',
    created_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_invocation (tool_invocation_id),
    UNIQUE KEY uk_request_tool_call (request_id, tool_call_id),
    KEY idx_run_created (run_id, created_at DESC),
    KEY idx_status_created (status, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='planning 输出表';

CREATE TABLE IF NOT EXISTS ai_agent_artifact (
    id                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    run_id             BIGINT         NULL COMMENT '所属 run ID',
    request_id         VARCHAR(64)    NULL COMMENT '所属请求ID，兼容非 run 场景',
    tool_invocation_id BIGINT         NULL COMMENT '所属 tool invocation，输入文件为空',
    tool_call_id       VARCHAR(128)   NULL COMMENT '所属 toolCallId，输入文件为空',
    artifact_role      VARCHAR(16)    NOT NULL COMMENT 'input / output',
    visibility         VARCHAR(16)    NOT NULL COMMENT 'visible / internal',
    source_type        VARCHAR(32)    NOT NULL COMMENT 'user_upload / tool_output',
    source_name        VARCHAR(128)   NULL COMMENT '来源名称',
    file_name          VARCHAR(256)   NOT NULL COMMENT '文件名',
    storage_key        VARCHAR(512)   NOT NULL DEFAULT '' COMMENT '稳定资源 key',
    download_url       VARCHAR(1024)  NULL COMMENT '下载地址',
    preview_url        VARCHAR(1024)  NULL COMMENT '预览地址',
    mime_type          VARCHAR(128)   NULL COMMENT 'MIME 类型',
    file_size          BIGINT         NULL COMMENT '文件大小',
    file_hash          VARCHAR(128)   NULL COMMENT '文件哈希',
    metadata_json      JSON           NULL COMMENT '扩展元数据',
    create_time        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_tool_storage (request_id, tool_call_id, storage_key),
    KEY idx_artifact_run_create (run_id, deleted, create_time DESC),
    KEY idx_artifact_request_tool (request_id, tool_call_id, deleted, create_time DESC),
    KEY idx_artifact_tool (tool_invocation_id, deleted, create_time DESC),
    KEY idx_artifact_role (artifact_role, visibility, deleted, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行输入输出产物账本表';

CREATE TABLE IF NOT EXISTS ai_agent_featured_conversation (
    id                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    featured_id        VARCHAR(64)    NOT NULL COMMENT '公共精品ID',
    session_id         VARCHAR(64)    NOT NULL COMMENT '原会话ID',
    title              VARCHAR(256)   NOT NULL COMMENT '展示标题',
    summary            TEXT           NULL COMMENT '展示摘要',
    cover_resource_key VARCHAR(512)   NULL COMMENT '封面资源key',
    cover_url          VARCHAR(1024)  NULL COMMENT '封面预览地址',
    tags_json          JSON           NULL COMMENT '标签数组',
    sort_order         INT            NOT NULL DEFAULT 0 COMMENT '排序值',
    status             VARCHAR(16)    NOT NULL DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE',
    published_by       VARCHAR(64)    NULL COMMENT '发布人',
    published_at       DATETIME(3)    NULL COMMENT '发布时间',
    updated_by         VARCHAR(64)    NULL COMMENT '最后更新人',
    updated_at         DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '业务更新时间',
    create_time        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_featured_conversation_featured_id (featured_id),
    UNIQUE KEY uk_featured_conversation_session_id (session_id),
    KEY idx_featured_conversation_status_sort (status, deleted, sort_order DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='精品会话发布表';

CREATE TABLE IF NOT EXISTS ai_agent_prompt_memory_stream (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    session_id          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '会话ID（策展流可作来源元数据）',
    owner_type          VARCHAR(16)  NOT NULL DEFAULT 'SESSION' COMMENT 'USER/VISITOR/SESSION',
    owner_id            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '用户级归属键；SESSION 模式可等于 session_id',
    memory_scope        VARCHAR(32)  NOT NULL COMMENT '记忆作用域 curated/user/react...',
    prompt_contract_id  VARCHAR(128) NOT NULL COMMENT '提示词契约ID',
    tool_contract_id    VARCHAR(128) NOT NULL COMMENT '工具契约ID',
    latest_turn_seq     INT          NOT NULL DEFAULT 0 COMMENT '最近已发布轮次',
    active_request_id   VARCHAR(64)  NULL COMMENT '当前持有租约的请求ID',
    lease_expire_at     DATETIME(3)  NULL COMMENT '写入租约过期时间',
    version             INT          NOT NULL DEFAULT 0 COMMENT '乐观版本号',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_prompt_memory_stream_owner (owner_type, owner_id, memory_scope, prompt_contract_id, tool_contract_id),
    KEY idx_prompt_memory_stream_session (session_id, memory_scope, deleted),
    KEY idx_prompt_memory_stream_lease (active_request_id, lease_expire_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词记忆流头（含用户级策展）';

CREATE TABLE IF NOT EXISTS ai_agent_ltm_curated_entry (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    stream_id           BIGINT       NULL COMMENT '可选 FK -> prompt_memory_stream.id',
    owner_type          VARCHAR(16)  NOT NULL COMMENT 'USER/VISITOR',
    owner_id            VARCHAR(64)  NOT NULL COMMENT '归属键',
    scope               VARCHAR(32)  NOT NULL COMMENT 'curated/user',
    content             TEXT         NOT NULL COMMENT '条目正文',
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PENDING_APPROVAL/DELETED',
    source_session_id   VARCHAR(64)  NULL COMMENT '写入来源 session',
    source_request_id   VARCHAR(64)  NULL COMMENT '写入来源 request',
    write_origin        VARCHAR(32)  NULL COMMENT 'tool/background_review/system',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    KEY idx_ltm_curated_owner_scope (owner_type, owner_id, scope, deleted, status),
    KEY idx_ltm_curated_stream (stream_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户级策展记忆条目（无向量）';

CREATE TABLE IF NOT EXISTS ai_agent_prompt_memory_turn (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    stream_id           BIGINT       NOT NULL COMMENT '所属记忆流ID',
    request_id          VARCHAR(64)  NOT NULL COMMENT '请求ID',
    run_id              BIGINT       NULL COMMENT '关联 dialogue_run.id',
    turn_seq            INT          NOT NULL COMMENT '流内递增轮次',
    baseline_turn_seq   INT          NOT NULL COMMENT '构建前已发布基线轮次',
    status              TINYINT      NOT NULL COMMENT '0构建中 1已就绪 2已失效',
    message_count       INT          NOT NULL DEFAULT 0 COMMENT '本轮增量消息数',
    started_at          DATETIME(3)  NULL COMMENT '构建开始时间',
    finished_at         DATETIME(3)  NULL COMMENT '发布完成时间',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_prompt_memory_turn_request (request_id),
    UNIQUE KEY uk_prompt_memory_turn_stream_seq (stream_id, turn_seq),
    KEY idx_prompt_memory_turn_stream_ready (stream_id, status, deleted, turn_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词记忆发布轮次';

CREATE TABLE IF NOT EXISTS ai_agent_prompt_memory_message (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    turn_id             BIGINT       NOT NULL COMMENT '所属记忆轮次ID',
    seq_no              INT          NOT NULL COMMENT '轮次内消息顺序',
    role                VARCHAR(16)  NOT NULL COMMENT '消息角色',
    content             LONGTEXT     NULL COMMENT '文本内容',
    base64_image        LONGTEXT     NULL COMMENT '图片base64内容',
    tool_call_id        VARCHAR(128) NULL COMMENT '工具调用ID',
    tool_calls_json     JSON         NULL COMMENT '工具调用数组',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_prompt_memory_message_turn_seq (turn_id, seq_no),
    KEY idx_prompt_memory_message_turn (turn_id, deleted, seq_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词记忆消息行';

CREATE TABLE IF NOT EXISTS ai_agent_working_memory_turn (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    session_id         VARCHAR(64)  NOT NULL COMMENT '会话ID',
    memory_scope       VARCHAR(64)  NOT NULL DEFAULT 'main' COMMENT 'main 或 sub:{agentId}',
    request_id         VARCHAR(64)  NOT NULL COMMENT '请求ID',
    run_id             BIGINT       NULL COMMENT '关联 dialogue_run.id',
    turn_seq           INT          NOT NULL COMMENT 'scope 内从 1 递增',
    entry_agent        VARCHAR(32)  NOT NULL COMMENT 'react / plan_solve / sub_*',
    status             TINYINT      NOT NULL DEFAULT 1 COMMENT '1=READY,0=BUILDING,2=INVALID',
    schema_version     INT          NOT NULL DEFAULT 1 COMMENT '投影协议版本',
    message_count      INT          NOT NULL DEFAULT 0 COMMENT '消息条数',
    token_estimate     INT          NOT NULL DEFAULT 0 COMMENT 'token 估算',
    started_at         DATETIME(3)  NULL COMMENT '开始时间',
    finished_at        DATETIME(3)  NULL COMMENT '结束时间',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wm_turn_request (request_id, deleted),
    UNIQUE KEY uk_wm_turn_session_scope_seq (session_id, memory_scope, turn_seq, deleted),
    KEY idx_wm_turn_session (session_id, deleted, turn_seq DESC),
    KEY idx_wm_turn_session_scope (session_id, memory_scope, deleted, turn_seq DESC),
    KEY idx_wm_turn_run (run_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作记忆轮次头（投影）';

CREATE TABLE IF NOT EXISTS ai_agent_working_memory_message (
    id                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    session_id           VARCHAR(64)   NOT NULL COMMENT '会话ID',
    memory_scope         VARCHAR(64)   NOT NULL DEFAULT 'main' COMMENT 'main 或 sub:{agentId}',
    turn_id              BIGINT        NOT NULL COMMENT 'FK -> working_memory_turn.id',
    request_id           VARCHAR(64)   NOT NULL COMMENT '请求ID',
    run_id               BIGINT        NULL COMMENT '关联 dialogue_run.id',
    seq_no               INT           NOT NULL COMMENT 'turn 内从 0 递增',
    role                 VARCHAR(16)   NOT NULL COMMENT 'USER/ASSISTANT/TOOL/SYSTEM',
    content              MEDIUMTEXT    NULL COMMENT '消息文本',
    reasoning_content    MEDIUMTEXT    NULL COMMENT '模型原生 CoT / reasoning_content',
    tool_call_id         VARCHAR(128)  NULL COMMENT 'TOOL 关联 id',
    tool_calls_json      JSON          NULL COMMENT 'ASSISTANT.toolCalls',
    base64_image         MEDIUMTEXT    NULL COMMENT '预留',
    message_kind         VARCHAR(32)   NOT NULL COMMENT 'query/assistant/tool_observation/final_summary/system_note',
    visibility           VARCHAR(32)   NOT NULL DEFAULT 'ALL' COMMENT '首期固定 ALL',
    token_estimate       INT           NOT NULL DEFAULT 0 COMMENT 'token 估算',
    create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wm_msg_turn_seq (turn_id, seq_no, deleted),
    KEY idx_wm_msg_session_seq (session_id, deleted, turn_id, seq_no),
    KEY idx_wm_msg_session_scope (session_id, memory_scope, deleted, turn_id, seq_no),
    KEY idx_wm_msg_request (request_id, deleted, seq_no),
    KEY idx_wm_msg_visibility (session_id, visibility, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作记忆消息行（自包含 hydrate）';

-- ��������ѹ���¼�����ƣ����ԡ�ǰ�� token��ժҪ���ġ�ѹ����ͶӰ���գ�
CREATE TABLE IF NOT EXISTS ai_agent_working_memory_compaction (
    id                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '����ID',
    session_id            VARCHAR(64)   NOT NULL COMMENT '�ỰID',
    trigger_request_id    VARCHAR(64)   NOT NULL COMMENT '����ѹ���ĵ�ǰ����ID',
    compact_request_id    VARCHAR(128)  NULL COMMENT 'д�� working_memory �� compact request_id',
    strategy              VARCHAR(32)   NOT NULL COMMENT 'micro-only/session-memory/full-llm/drop-oldest',
    status                TINYINT       NOT NULL DEFAULT 1 COMMENT '1=SUCCESS,2=FAILED',
    before_tokens         INT           NOT NULL DEFAULT 0 COMMENT 'ѹ��ǰ token ����',
    after_tokens          INT           NOT NULL DEFAULT 0 COMMENT 'ѹ���� token ����',
    before_message_count  INT           NOT NULL DEFAULT 0 COMMENT 'ѹ��ǰ��Ϣ����',
    after_message_count   INT           NOT NULL DEFAULT 0 COMMENT 'ѹ������Ϣ����',
    threshold_tokens      INT           NOT NULL DEFAULT 0 COMMENT '������ֵ',
    summary_text          MEDIUMTEXT    NULL COMMENT 'ע���� summary ���ģ����У�',
    before_messages_json  MEDIUMTEXT    NULL COMMENT 'ѹ��ǰ Message �б� JSON����ƣ�',
    after_messages_json   MEDIUMTEXT    NULL COMMENT 'ѹ���� Message �б� JSON�����/���ؽ���',
    error_message         VARCHAR(1024) NULL COMMENT 'ʧ��ԭ��',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '����ʱ��',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '����ʱ��',
    deleted               TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '��ɾ��',
    PRIMARY KEY (id),
    KEY idx_wm_compaction_session (session_id, deleted, id DESC),
    KEY idx_wm_compaction_trigger (trigger_request_id, deleted),
    KEY idx_wm_compaction_strategy (session_id, strategy, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='��������ѹ���¼���Ʊ�';

-- LTM memory-only fork 执行观测（压缩前 flush / background review）
CREATE TABLE IF NOT EXISTS ai_agent_ltm_fork_execution (
    id                      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    session_id              VARCHAR(64)   NOT NULL COMMENT '会话ID',
    trigger_request_id      VARCHAR(64)   NULL COMMENT '触发本 fork 的父 requestId',
    fork_request_id         VARCHAR(128)  NULL COMMENT 'fork 自身 requestId',
    fork_kind               VARCHAR(32)   NOT NULL COMMENT 'flush / bg-review',
    status                  TINYINT       NOT NULL DEFAULT 1 COMMENT '1=SUCCESS 2=FAILED 3=SKIPPED 4=TIMEOUT',
    skip_reason             VARCHAR(64)   NULL COMMENT '跳过原因',
    owner_type              VARCHAR(16)   NULL COMMENT 'USER/VISITOR',
    owner_id                VARCHAR(64)   NULL COMMENT '归属键',
    user_turns              INT           NULL COMMENT '触发时 user turn 数（flush）',
    snapshot_message_count  INT           NULL COMMENT '重放消息条数',
    max_steps               INT           NULL COMMENT 'fork 最大步数',
    timeout_seconds         BIGINT        NULL COMMENT 'fork 超时秒数',
    duration_ms             BIGINT        NULL COMMENT '执行耗时毫秒',
    entries_before          INT           NULL COMMENT 'fork 前 curated+user 活跃条数',
    entries_after           INT           NULL COMMENT 'fork 后 curated+user 活跃条数',
    applied_count           INT           NOT NULL DEFAULT 0 COMMENT '估算新增条数 after-before',
    error_message           VARCHAR(1024) NULL COMMENT '失败/超时信息',
    detail_json             MEDIUMTEXT    NULL COMMENT '扩展观测 JSON',
    create_time             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                 TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    KEY idx_ltm_fork_session (session_id, deleted, id DESC),
    KEY idx_ltm_fork_trigger (trigger_request_id, deleted),
    KEY idx_ltm_fork_kind_status (fork_kind, status, deleted),
    KEY idx_ltm_fork_owner (owner_type, owner_id, deleted, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LTM memory-only fork 执行观测';

-- 会话 Todo + 后台运行任务（非 Execution Ledger）
CREATE TABLE IF NOT EXISTS ai_agent_session_todo (
    id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    session_id      VARCHAR(64)    NOT NULL COMMENT '会话ID（listId）',
    task_id         VARCHAR(32)    NOT NULL COMMENT 'Todo 任务ID（会话内自增序号）',
    subject         VARCHAR(512)   NOT NULL COMMENT '短标题',
    description     MEDIUMTEXT     NOT NULL COMMENT '任务详情',
    active_form     VARCHAR(256)   NULL COMMENT '进行时文案',
    owner           VARCHAR(128)   NULL COMMENT '负责人/owner',
    status          VARCHAR(32)    NOT NULL DEFAULT 'pending' COMMENT 'pending|in_progress|completed',
    blocks_json     JSON           NULL COMMENT '阻塞的任务 id 列表',
    blocked_by_json JSON           NULL COMMENT '被阻塞依赖 id 列表',
    metadata_json   JSON           NULL COMMENT '扩展元数据',
    seq_no          INT            NOT NULL DEFAULT 0 COMMENT '排序序号（通常=task_id 数值）',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_todo (session_id, task_id, deleted),
    KEY idx_session_todo_session (session_id, deleted, seq_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话 Todo 任务列表（TaskCreate/List）';

CREATE TABLE IF NOT EXISTS ai_agent_background_task (
    id                     BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    session_id             VARCHAR(64)    NOT NULL COMMENT '会话ID',
    task_id                VARCHAR(32)    NOT NULL COMMENT '后台任务ID',
    type                   VARCHAR(32)    NOT NULL DEFAULT 'generic' COMMENT 'local_agent|local_shell|generic',
    status                 VARCHAR(32)    NOT NULL DEFAULT 'running' COMMENT 'running|completed|stopped|failed',
    description            VARCHAR(512)   NULL COMMENT '任务描述',
    command                VARCHAR(512)   NULL COMMENT '命令/子类型摘要',
    agent_id               VARCHAR(64)    NULL COMMENT '子 Agent id',
    agent_type             VARCHAR(64)    NULL COMMENT 'subagent_type',
    prompt                 MEDIUMTEXT     NULL COMMENT '派发 prompt',
    output                 MEDIUMTEXT     NULL COMMENT '终态输出摘要',
    error_msg              VARCHAR(1024)  NULL COMMENT '错误信息',
    total_tool_use_count   INT            NULL COMMENT '工具调用次数',
    total_duration_ms      BIGINT         NULL COMMENT '耗时毫秒',
    started_at_ms          BIGINT         NULL COMMENT '开始时间 epoch ms',
    ended_at_ms            BIGINT         NULL COMMENT '结束时间 epoch ms',
    create_time            DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time            DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_bg_task (session_id, task_id, deleted),
    KEY idx_session_bg_session (session_id, deleted, update_time DESC),
    KEY idx_session_bg_status (session_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台运行任务（Agent run_in_background / TaskOutput）';
