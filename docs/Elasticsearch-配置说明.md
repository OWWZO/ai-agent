# Elasticsearch（ES）配置说明

项目中 ES 主要用于 **genie-tool（Python）** 的 table_rag 能力，在 `useElastic=true` 时做表/列召回。Java 端通过 HTTP 调用 genie-tool，不直接连 ES。

---

## 一、Python genie-tool（jd-agent）中的 ES 配置

配置位置：**jd-agent/joyagent-jdgenie-data_agent/genie-tool/.env**

### 1. 环境变量说明

| 环境变量 | 说明 | 示例 |
|---------|------|------|
| `TR_ES_CONFIGS_HOST` | ES 地址（必填，否则不初始化 ES 客户端） | `localhost:9200` 或 `http://localhost:9200` |
| `TR_ES_CONFIGS_SCHEME` | 协议 | `http` 或 `https` |
| `TR_ES_CONFIGS_USER` | 用户名（可选） | `elastic` |
| `TR_ES_CONFIGS_PASSWORD` | 密码（可选） | 你的密码 |
| `TR_ES_CONFIGS_INDEX` | 表 RAG 使用的索引名 | 如 `table_rag_index` |
| `TR_ES_RECALL_TOP_K` | ES 召回条数 | 默认 `15` |

### 2. 配置示例

**本地 ES（无认证）：**
```env
TR_ES_CONFIGS_HOST=localhost:9200
TR_ES_CONFIGS_SCHEME=http
TR_ES_CONFIGS_USER=
TR_ES_CONFIGS_PASSWORD=
TR_ES_CONFIGS_INDEX=table_rag_index
TR_ES_RECALL_TOP_K=15
```

**带认证的 ES：**
```env
TR_ES_CONFIGS_HOST=your-es-host:9200
TR_ES_CONFIGS_SCHEME=https
TR_ES_CONFIGS_USER=elastic
TR_ES_CONFIGS_PASSWORD=your_password
TR_ES_CONFIGS_INDEX=table_rag_index
TR_ES_RECALL_TOP_K=15
```

### 3. 不配置 ES 时的行为

- 若 **不配置** 或 `TR_ES_CONFIGS_HOST` 为空：genie-tool 会跳过 ES 客户端初始化，**table_rag 仍可用**（仅不使用 ES 召回）。
- 调用 table_rag 时建议传 `useElastic=false`，避免依赖 ES。

---

## 二、本地快速启动 ES（Docker）

ai-agent 仓库已提供 ELK 的 docker-compose，可用于本地 ES。

### 1. 启动 ES

```bash
cd ai-agent-station-study/docs/dev-ops
docker-compose -f docker-compose-elk.yml up -d elasticsearch
```

### 2. 验证

```bash
curl http://localhost:9200
```

### 3. 与 genie-tool 对接

在 genie-tool 的 `.env` 中配置：

```env
TR_ES_CONFIGS_HOST=localhost:9200
TR_ES_CONFIGS_SCHEME=http
TR_ES_CONFIGS_USER=
TR_ES_CONFIGS_PASSWORD=
TR_ES_CONFIGS_INDEX=你的索引名
TR_ES_RECALL_TOP_K=15
```

索引需按 table_rag 的检索需求自行创建并写入数据（含 `modelCode`、`value` 等字段，并使用 ik 分词等）。

---

## 三、Java 端（ai-agent）说明

- **ai-agent** 不直接连接 ES；JDGenie 相关能力通过 HTTP 调用 **genie-tool**。
- 若需在 Java 工程里单独使用 ES（如日志、检索等），可自行增加 Spring Data Elasticsearch 等依赖，并在 `application.yml` 中配置 `spring.elasticsearch.*`，与上述 genie-tool 的 ES 配置互不影响。

---

## 四、小结

| 场景 | 配置位置 | 必填项 |
|------|----------|--------|
| genie-tool 使用 ES 做 table_rag | jd-agent/genie-tool/.env | `TR_ES_CONFIGS_HOST`（要启用 ES 时） |
| 本地起 ES 做开发/联调 | docker-compose-elk.yml | - |

不配置或留空 `TR_ES_CONFIGS_HOST` 时，table_rag 仍可正常工作，仅不使用 ES 召回。
