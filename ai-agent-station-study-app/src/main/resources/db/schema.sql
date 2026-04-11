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

-- ========================
-- 对话历史持久化
-- ========================

CREATE TABLE IF NOT EXISTS ai_agent_conversation (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    session_id      VARCHAR(64)  NOT NULL COMMENT '前端生成的会话UUID',
    device_id       VARCHAR(128) NOT NULL COMMENT '匿名设备标识(fingerprint)',
    user_id         BIGINT       NULL     COMMENT '认证用户ID(匿名时为NULL)',
    title           VARCHAR(256) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    agent_type      TINYINT      NOT NULL COMMENT '0=CHAT, 1=PLAN_SOLVE(深度思考), 2=REACT(深度研究)',
    product_type    VARCHAR(32)  NOT NULL DEFAULT 'chat' COMMENT '产品形态: chat/html/docs/ppt/table',
    ai_agent_id     VARCHAR(64)  NULL     COMMENT 'chat 会话绑定的 Fix 角色ID',
    ai_agent_name_snapshot VARCHAR(128) NULL COMMENT 'chat 角色名称快照，保障历史展示稳定',
    message_count   INT          NOT NULL DEFAULT 0 COMMENT '消息轮数(冗余字段,避免COUNT)',
    pinned          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否置顶',
    last_message_preview VARCHAR(200) NULL COMMENT '最后消息预览',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除 0:正常 1:已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_id (session_id),
    KEY idx_device_id (device_id, deleted, update_time DESC),
    KEY idx_user_id (user_id, deleted, update_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent 会话表';

ALTER TABLE ai_agent_conversation
    ADD COLUMN IF NOT EXISTS ai_agent_id VARCHAR(64) NULL COMMENT 'chat 会话绑定的 Fix 角色ID' AFTER product_type,
    ADD COLUMN IF NOT EXISTS ai_agent_name_snapshot VARCHAR(128) NULL COMMENT 'chat 角色名称快照，保障历史展示稳定' AFTER ai_agent_id;

CREATE TABLE IF NOT EXISTS ai_agent_message (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    conversation_id  BIGINT       NOT NULL COMMENT 'FK -> ai_agent_conversation.id',
    session_id       VARCHAR(64)  NOT NULL COMMENT '冗余会话ID,便于前端查询',
    request_id       VARCHAR(64)  NOT NULL COMMENT '前端请求UUID,每轮唯一',
    sort_order       INT          NOT NULL DEFAULT 0 COMMENT '轮次序号(0-based)',
    query            TEXT         NOT NULL COMMENT '用户问题',
    files_json       JSON         NULL     COMMENT '上传文件列表JSON [{name,url,type,size}]',
    agent_type       TINYINT      NOT NULL COMMENT '0=CHAT, 1=PLAN_SOLVE, 2=REACT',
    response         MEDIUMTEXT   NULL     COMMENT 'Chat模式: LLM纯文本回答',
    thought          MEDIUMTEXT   NULL     COMMENT '深度思考: 推理过程文本(plan_thought)',
    plan_json        JSON         NULL     COMMENT '深度思考: Plan对象(title/steps/stages)',
    tasks_json       MEDIUMTEXT   NULL     COMMENT 'Task[][] 二维数组JSON(含resultMap完整数据)',
    multi_agent_json JSON         NULL     COMMENT 'MultiAgent元数据',
    conclusion_json  JSON         NULL     COMMENT '最终结论/摘要Task',
    plan_list_json   JSON         NULL     COMMENT 'PlanItem[]计划列表',
    render_snapshot_json MEDIUMTEXT NULL   COMMENT '版本化渲染快照',
    metrics_json     JSON         NULL     COMMENT '执行指标',
    status           TINYINT      NOT NULL DEFAULT 0 COMMENT '0=流式中,1=完成,2=错误,3=强制停止',
    force_stop       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否强制停止',
    started_at       DATETIME     NULL     COMMENT '流开始时间',
    finished_at      DATETIME     NULL     COMMENT '流结束时间',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除 0:正常 1:已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_id (request_id),
    KEY idx_conversation_sort (conversation_id, sort_order),
    KEY idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent 消息表(每轮对话一行)';

CREATE TABLE IF NOT EXISTS ai_agent_message_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    message_id      BIGINT       NOT NULL COMMENT 'FK -> ai_agent_message.id',
    conversation_id BIGINT       NOT NULL COMMENT '冗余会话ID',
    session_id      VARCHAR(64)  NOT NULL COMMENT '冗余sessionId',
    request_id      VARCHAR(64)  NOT NULL COMMENT '冗余requestId',
    seq_no          INT          NOT NULL COMMENT '单轮事件顺序',
    event_type      VARCHAR(32)  NOT NULL COMMENT '事件类型',
    event_sub_type  VARCHAR(32)  NULL     COMMENT '事件子类型',
    display_area    VARCHAR(32)  NOT NULL DEFAULT 'timeline' COMMENT '展示区域',
    task_id         VARCHAR(64)  NULL     COMMENT '关联taskId',
    task_order      INT          NULL     COMMENT '任务内顺序',
    message_id_ext  VARCHAR(128) NULL     COMMENT '上游messageId',
    title           VARCHAR(256) NULL     COMMENT '显示标题',
    content_text    MEDIUMTEXT   NULL     COMMENT '展示文本',
    payload_json    JSON         NULL     COMMENT '原始事件负载',
    artifact_id     BIGINT       NULL     COMMENT '二期关联artifact',
    is_final        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否最终态',
    status          VARCHAR(16)  NOT NULL DEFAULT 'completed' COMMENT 'completed/partial/error',
    started_at      DATETIME     NULL     COMMENT '开始时间',
    ended_at        DATETIME     NULL     COMMENT '结束时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_seq (request_id, seq_no),
    KEY idx_message_id (message_id, seq_no),
    KEY idx_conversation_id (conversation_id),
    KEY idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent 消息事件表';

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
