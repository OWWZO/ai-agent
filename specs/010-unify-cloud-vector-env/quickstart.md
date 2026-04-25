# Quickstart

## 1. 配置 MRAG 共享向量环境

如果 MRAG 与 DataAgent 使用不同的 Qdrant，建议把共享 `QDRANT_*` 留给 MRAG：

```powershell
$env:QDRANT_URL="https://your-mrag-cluster.aws.cloud.qdrant.io"
$env:QDRANT_PORT="6334"
$env:QDRANT_API_KEY="your-mrag-qdrant-api-key"
$env:QDRANT_PREFER_GRPC="true"

$env:TEXT_EMBEDDING_TYPE="openai_compatible"
$env:TEXT_EMBEDDING_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:TEXT_EMBEDDING_API_KEY="your-embedding-api-key"
$env:TEXT_EMBEDDING_MODEL_NAME="text-embedding-v4"
$env:TEXT_EMBEDDING_DIMENSION="1024"

$env:TR_ES_CONFIGS_HOST="your-es-host:9200"
$env:TR_ES_CONFIGS_SCHEME="https"
$env:TR_ES_CONFIGS_API_KEY="your-es-api-key"
$env:TR_ES_CONFIGS_INDEX="reactor_model_column_value"
```

说明：

- `QDRANT_*` 作为 MRAG 默认向量配置来源。
- `TEXT_EMBEDDING_*` 仍由 MRAG 与 Java DataAgent 共用，不再要求单独部署独立 embedding 服务。
- `TR_ES_CONFIGS_INDEX` 保持固定为 `reactor_model_column_value`。
- Elastic Cloud 优先推荐 `TR_ES_CONFIGS_API_KEY`；若仍使用自建 ES，也可继续配置 `TR_ES_CONFIGS_USER` / `TR_ES_CONFIGS_PASSWORD`。
- `TR_ES_CONFIGS_HOST` 推荐填写 `host:port`，例如 `my-es.es.us-central1.gcp.elastic.cloud:443`；当前实现也兼容直接传完整 `https://...:443`。

## 2. 配置 DataAgent 专属 Qdrant 与开关

若 DataAgent / `table_rag` 需要使用另一套 Qdrant，补充一组 `DATA_AGENT_QDRANT_*`：

```powershell
$env:DATA_AGENT_QDRANT_ENABLE="true"
$env:DATA_AGENT_QDRANT_URL="https://your-data-agent-cluster.aws.cloud.qdrant.io"
$env:DATA_AGENT_QDRANT_PORT="6334"
$env:DATA_AGENT_QDRANT_API_KEY="your-data-agent-qdrant-api-key"
$env:DATA_AGENT_QDRANT_PREFER_GRPC="true"
$env:DATA_AGENT_ES_ENABLE="true"
```

在 [application-dev.yml](/D:/Java%20Code/ai-agent/ai-agent-station-study/ai-agent-station-study-app/src/main/resources/application-dev.yml) 中，DataAgent 现已改为“只认专属配置，不再回退 MRAG 共享 Qdrant”：

```yaml
autobots:
  data-agent:
    agent-url: ${DATA_AGENT_AGENT_URL:http://127.0.0.1:1601}
    force-refresh: ${DATA_AGENT_FORCE_REFRESH:false}
    qdrantConfig:
      enable: ${DATA_AGENT_QDRANT_ENABLE:false}
      url: ${DATA_AGENT_QDRANT_URL:}
      host: ${DATA_AGENT_QDRANT_HOST:}
      port: ${DATA_AGENT_QDRANT_PORT:6334}
      apiKey: ${DATA_AGENT_QDRANT_API_KEY:}
      preferGrpc: ${DATA_AGENT_QDRANT_PREFER_GRPC:true}
    es-config:
      enable: ${DATA_AGENT_ES_ENABLE:${TR_ES_CONFIGS_ENABLE:false}}
      host: ${DATA_AGENT_ES_HOST:${TR_ES_CONFIGS_HOST:}}
      scheme: ${DATA_AGENT_ES_SCHEME:${TR_ES_CONFIGS_SCHEME:http}}
      user: ${DATA_AGENT_ES_USER:${TR_ES_CONFIGS_USER:}}
      password: ${DATA_AGENT_ES_PASSWORD:${TR_ES_CONFIGS_PASSWORD:}}
      apiKey: ${DATA_AGENT_ES_API_KEY:${TR_ES_CONFIGS_API_KEY:}}
```

说明：

- `force-refresh` 默认必须为 `false`。
- DataAgent 仍使用固定 collection `reactor_model_schema` 与 index `reactor_model_column_value`。
- Java `EmbeddingService` 默认应指向 `reactor-tool` 的 `/v1/tool/embedding/text`。
- Python `table_rag` 的直连 Qdrant 默认只会复用 `DATA_AGENT_QDRANT_*`，不会再回退到 MRAG 的 `QDRANT_*`，避免两套知识库混用。

## 3. 如需兼容旧环境，按需保留 override

只有以下场景才建议继续配置 override：

- `table_rag` 还要再单独连接第三套 Qdrant：配置 `TR_QDRANT_HOST` / `TR_QDRANT_PORT` / `TR_QDRANT_API_KEY`
- `table_rag` 仍需独立 embedding HTTP 服务：配置 `TR_EMBEDDING_URL`
- 纯本地旧环境未迁云：继续使用旧 host/port 模式

若未配置这些 override，`table_rag` 默认按 `TR_QDRANT_* > DATA_AGENT_QDRANT_*` 顺序回退；若两者都未配置，则视为未配置 DataAgent 向量召回。

## 4. 启动 `reactor-tool`

在仓库根目录执行：

```powershell
cd reactor-tool
uv run python server.py
```

启动后先验证两个接口：

```powershell
curl.exe -X POST "http://127.0.0.1:1601/v1/tool/embedding/text" ^
  -H "Content-Type: application/json" ^
  -d "{\"inputs\":[\"customer_id 含义\",\"订单状态字段说明\"],\"normalize\":true}"
```

```powershell
curl.exe -X POST "http://127.0.0.1:1601/v1/tool/table_rag" ^
  -H "Content-Type: application/json" ^
  -d "{\"query\":\"查询订单主表的客户字段定义\"}"
```

期望结果：

- `/v1/tool/embedding/text` 返回两条 1024 维向量
- `/v1/tool/table_rag` 在未配置 `TR_QDRANT_*` 时，仍能通过 `DATA_AGENT_QDRANT_*` 完成召回；若 `DATA_AGENT_QDRANT_*` 也未配置，则不会误连 MRAG 库

## 5. 启动 Java 主服务

回到仓库根目录执行：

```powershell
mvn -pl ai-agent-station-study-app spring-boot:run
```

常规启动验收点：

- 若 `DATA_AGENT_QDRANT_*` 与 ES 配置正确，启动日志中应显示 DataAgent schema 向量/列值增强可用
- 若某项增强能力校验失败，服务仍可启动，但日志必须明确标记该能力已降级

## 6. 首次迁移时执行一次显式强制刷新

从“旧环境”切到“统一云端增强版”时，执行一次显式刷新：

```powershell
$env:DATA_AGENT_FORCE_REFRESH="true"
mvn -pl ai-agent-station-study-app spring-boot:run
```

期望结果：

- 刷新范围仅包含当前 `model-list` 中声明的模型
- 成功后远端 Qdrant `reactor_model_schema` 与 ES `reactor_model_column_value` 完整重建
- 旧的、已不在 `model-list` 中的远端模型数据被清理

执行完成后，请把 `DATA_AGENT_FORCE_REFRESH` 恢复为 `false`，避免后续常规重启误触发重建。

## 7. 做完整闭环验收

建议按以下顺序验收：

1. 调用 MRAG 文档检索，确认共享 embedding 与共享 Qdrant 正常工作。
2. 调用 `table_rag`，确认未配置 `TR_QDRANT_*` 时可回退 `DATA_AGENT_QDRANT_*`。
3. 发起一次 DataAgent 问数请求，确认 schema 向量召回和列值增强召回可用。
4. 人为制造异常样本，例如错误 Qdrant API Key、错误 embedding key、ES 无分析器，确认服务给出明确失败或降级结果。

通过以上四步后，可视为“统一云端向量环境”闭环已经完成。
