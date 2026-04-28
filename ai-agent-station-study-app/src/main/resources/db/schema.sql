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

CREATE TABLE IF NOT EXISTS ai_agent_message (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    conversation_id  BIGINT       NOT NULL COMMENT 'FK -> ai_agent_conversation.id',
    request_id       VARCHAR(64)  NOT NULL COMMENT '前端请求UUID,每轮唯一',
    sort_order       INT          NOT NULL DEFAULT 0 COMMENT '轮次序号(0-based)',
    query            TEXT         NOT NULL COMMENT '用户问题',
    files_json       JSON         NULL     COMMENT '上传文件列表JSON [{name,url,type,size}]',
    generated_files_json JSON     NULL     COMMENT '本轮生成文件列表JSON，结构复用 FileInformation',
    agent_type       TINYINT      NOT NULL COMMENT '0=CHAT, 1=PLAN_SOLVE, 2=REACT',
    response         MEDIUMTEXT   NULL     COMMENT '单轮最终回答/上下文文本',
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
    UNIQUE KEY uk_conversation_sort (conversation_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent 消息表(每轮对话一行)';

CREATE TABLE IF NOT EXISTS ai_agent_message_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    message_id      BIGINT       NOT NULL COMMENT 'FK -> ai_agent_message.id',
    seq_no          INT          NOT NULL COMMENT '单轮事件顺序',
    event_type      VARCHAR(32)  NOT NULL COMMENT '事件类型',
    event_sub_type  VARCHAR(32)  NULL     COMMENT '事件子类型',
    display_area    VARCHAR(32)  NOT NULL DEFAULT 'timeline' COMMENT '展示区域',
    task_id         VARCHAR(64)  NULL     COMMENT '关联taskId',
    task_order      INT          NULL     COMMENT '任务内顺序',
    tool_use_id     VARCHAR(128) NULL     COMMENT '工具调用实例ID',
    tool_name       VARCHAR(128) NULL     COMMENT '工具名称',
    tool_arguments_json JSON     NULL     COMMENT '工具参数快照JSON',
    title           VARCHAR(256) NULL     COMMENT '显示标题',
    content_text    MEDIUMTEXT   NULL     COMMENT '展示文本',
    reference_only  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否只保留引用，不内联正文',
    artifact_refs_json JSON      NULL     COMMENT '标准化产物引用JSON',
    structured_data_json JSON    NULL     COMMENT '标准化结构化事实JSON',
    payload_json    JSON         NULL     COMMENT '扩展字段JSON，仅承载未标准化的最小补充信息',
    status          VARCHAR(16)  NOT NULL DEFAULT 'completed' COMMENT 'completed/partial/error',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_seq (message_id, seq_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent 事件事实账本表';

CREATE TABLE IF NOT EXISTS ai_agent_session_memory (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    conversation_id     BIGINT       NOT NULL COMMENT 'FK -> ai_agent_conversation.id',
    session_id          VARCHAR(64)  NOT NULL COMMENT '会话ID，同一会话允许存在多条快照版本',
    agent_type          TINYINT      NOT NULL COMMENT '0=CHAT, 1=PLAN_SOLVE, 2=REACT',
    summary_text        MEDIUMTEXT   NULL     COMMENT '结构化 session memory 摘要',
    artifact_refs_json  JSON         NULL     COMMENT '归档后的稳定文件/产物引用',
    boundary_sort_order INT          NULL     COMMENT '本版本快照覆盖的最后一轮顺序',
    source_turn_count   INT          NOT NULL DEFAULT 0 COMMENT '本版本快照覆盖轮次数',
    last_compacted_at   DATETIME     NULL     COMMENT '本版本压缩时间',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    KEY idx_session_latest (session_id, deleted, id),
    KEY idx_conversation_history (conversation_id, deleted, id),
    KEY idx_conversation_boundary (conversation_id, deleted, boundary_sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent 会话记忆快照表';

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

CREATE TABLE IF NOT EXISTS ai_agent_image_generation_record (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    request_id         VARCHAR(64)  NOT NULL COMMENT '单次生成请求ID',
    result_index       INT          NOT NULL DEFAULT 0 COMMENT '同批次结果图序号(0-based)',
    device_id          VARCHAR(128) NOT NULL COMMENT '匿名设备标识',
    user_id            BIGINT       NULL     COMMENT '认证用户ID(预留)',
    prompt             TEXT         NOT NULL COMMENT '生成提示词',
    mode               VARCHAR(16)  NOT NULL COMMENT '生成模式 images/edits',
    size               VARCHAR(32)  NULL     COMMENT '输出尺寸',
    batch_count        INT          NOT NULL DEFAULT 1 COMMENT '本批次生成图片总数',
    source_image_count INT          NOT NULL DEFAULT 0 COMMENT '参考图数量',
    mask_image_count   INT          NOT NULL DEFAULT 0 COMMENT '蒙版图数量',
    used_fallback      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否走兼容降级接口',
    file_name          VARCHAR(255) NULL     COMMENT '结果图片文件名',
    oss_url            VARCHAR(1024) NULL    COMMENT '文件下载地址或对象存储地址',
    domain_url         VARCHAR(1024) NULL    COMMENT '文件预览地址',
    download_url       VARCHAR(1024) NULL    COMMENT '稳定下载地址',
    file_size          BIGINT       NULL     COMMENT '文件大小(字节)',
    mime_type          VARCHAR(128) NULL     COMMENT '结果图片MIME类型',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_result (request_id, result_index),
    KEY idx_device_create (device_id, deleted, create_time DESC),
    KEY idx_user_create (user_id, deleted, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生图工作台结果明细表';
