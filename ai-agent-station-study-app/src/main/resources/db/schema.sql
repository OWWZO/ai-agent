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