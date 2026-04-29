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
