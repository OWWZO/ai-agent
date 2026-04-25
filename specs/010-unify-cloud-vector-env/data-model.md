# Data Model

## Overview

本期不新增数据库表结构，重点演进的是共享配置契约、运行时可用性状态、内部 embedding 代理接口以及显式刷新执行模型。数据模型以“配置如何解析”“能力如何降级”“刷新如何判定成功/失败”为核心。

## Entities

### 1. SharedCloudVectorConfig

统一描述 MRAG 与 DataAgent 默认复用的云端向量环境。

| Field | Type | Required | Validation / Rule | Notes |
|------|------|----------|-------------------|-------|
| `qdrantUrl` | `String` | no | 可为空；非空时必须是合法 `http/https` URL | 优先表达托管 Qdrant 地址 |
| `qdrantPort` | `Integer` | no | 缺省回退 6334 | 供 URL 无显式端口时使用 |
| `qdrantApiKey` | `String` | no | 云端场景通常必填 | 供 Java/Python 共享认证 |
| `qdrantPreferGrpc` | `Boolean` | no | 缺省 `true` | 兼容既有 MRAG 直连方式 |
| `embeddingType` | `String` | yes | 需匹配 MRAG 已支持 provider 类型 | 与 `TEXT_EMBEDDING_TYPE` 对应 |
| `embeddingBaseUrl` | `String` | yes | 必须为可访问的 provider 基地址 | 由 `reactor-tool` 适配调用 |
| `embeddingApiKey` | `String` | no | 取决于 provider | 支持云端密钥认证 |
| `embeddingModelName` | `String` | yes | 非空 | Java / Python 共用同一文本向量模型 |
| `embeddingDimension` | `Integer` | yes | 必须与远端 collection 维度匹配 | 维度不匹配时需失败或降级 |
| `esHost` | `String` | no | 可为空；启用列值召回时必须可连通 | 对应 `TR_ES_CONFIGS_HOST` |
| `esScheme` | `String` | no | `http` 或 `https`；缺省 `http` | 供 Java 构造云端 ES 客户端 |
| `esUser` | `String` | no | 取决于实例 | 共享 ES 认证 |
| `esPassword` | `String` | no | 取决于实例 | 共享 ES 认证 |

### 2. VectorOverrideProfile

描述某条检索链路的专属覆盖配置及其优先级。

| Field | Type | Required | Validation / Rule | Notes |
|------|------|----------|-------------------|-------|
| `scope` | `Enum` | yes | `table_rag` / `legacy_embedding` | 当前仅两类 override |
| `enabled` | `Boolean` | yes | 显式配置即视为开启 | 决定是否覆盖共享配置 |
| `qdrantHost` | `String` | no | 与 `scope=table_rag` 搭配 | 兼容旧本地 Qdrant 模式 |
| `qdrantPort` | `Integer` | no | 合法端口 | 与旧配置兼容 |
| `qdrantApiKey` | `String` | no | 可为空 | 私有覆盖认证 |
| `embeddingUrl` | `String` | no | 合法 HTTP URL | 对应旧 `TR_EMBEDDING_URL` |
| `precedence` | `Integer` | yes | 数值越小优先级越高 | 专属覆盖高于共享配置 |

### 3. DataAgentQdrantProfile

Java DataAgent 侧最终用于建立 Qdrant 连接的解析结果。

| Field | Type | Required | Validation / Rule | Notes |
|------|------|----------|-------------------|-------|
| `source` | `Enum` | yes | `shared_url` / `shared_host_port` / `override_host_port` | 标记配置来源，便于排障 |
| `host` | `String` | yes | 非空 | 从 URL 或 host 解析得到 |
| `port` | `Integer` | yes | 合法端口 | URL 未显式指定时取默认值 |
| `tlsEnabled` | `Boolean` | yes | `https` 推断为 `true` | 直接影响 gRPC 连接 |
| `apiKey` | `String` | no | 云端场景建议非空 | 透传给 SDK |
| `preferGrpc` | `Boolean` | yes | 缺省 `true` | 与现有 MRAG 行为保持一致 |
| `collectionName` | `String` | yes | 固定 `reactor_model_schema` | 本期不允许变更 |

### 4. EmbeddingProxyRequest

Java 调用 `reactor-tool` 的内部文本向量请求体。

| Field | Type | Required | Validation / Rule | Notes |
|------|------|----------|-------------------|-------|
| `inputs` | `List<String>` | yes | 非空；每项不能为空白 | 支持批量文本向量 |
| `normalize` | `Boolean` | yes | 缺省 `true` | `true` 时返回前做 L2 归一化 |

### 5. EmbeddingProxyResponse

`reactor-tool` 返回给 Java 的批量向量结果。

| Field | Type | Required | Validation / Rule | Notes |
|------|------|----------|-------------------|-------|
| `vectors` | `List<List<Float>>` | yes | 数量需与 `inputs` 一致 | 对应每条输入文本的 embedding |
| `dimension` | `Integer` | no | 若返回则应与配置一致 | 便于日志与调试 |
| `model` | `String` | no | 若返回则应等于共享模型名 | 便于问题定位 |

### 6. RefreshExecution

描述一次显式 `force-refresh` 的执行单元。

| Field | Type | Required | Validation / Rule | Notes |
|------|------|----------|-------------------|-------|
| `enabled` | `Boolean` | yes | 默认 `false` | 只有显式开启才执行 |
| `targetModels` | `List<String>` | yes | 仅允许来自当前 `model-list` | 本次刷新作用域 |
| `status` | `Enum` | yes | `pending` / `running` / `failed` / `completed` | 用于日志与结果判定 |
| `currentStep` | `Enum` | no | `model_info` / `model_schema` / `qdrant` / `es_cleanup` 等 | 失败定位信息 |
| `failureReason` | `String` | no | 失败时必填 | 供运维定位问题 |
| `staleRemoteCleanup` | `Boolean` | yes | 成功后执行 | 删除不再属于当前 `model-list` 的远端数据 |

### 7. RemoteRetrievalState

表达共享云端增强能力在当前运行时是否健康。

| Field | Type | Required | Validation / Rule | Notes |
|------|------|----------|-------------------|-------|
| `schemaVectorAvailable` | `Boolean` | yes | 初始化失败时置 `false` | 控制 Qdrant schema 召回是否可用 |
| `columnValueRecallAvailable` | `Boolean` | yes | ES 初始化失败时置 `false` | 控制列值增强召回 |
| `embeddingAvailable` | `Boolean` | yes | 代理端点不可用时置 `false` | 控制向量化能力 |
| `degraded` | `Boolean` | yes | 任一增强能力不可用即为 `true` | 便于统一告警 |
| `lastError` | `String` | no | 最近一次失败原因 | 启动日志和排障信息 |

## Relationships

- `SharedCloudVectorConfig` 是 MRAG 与 DataAgent 的默认配置源。
- `VectorOverrideProfile` 仅在特定链路显式配置时覆盖 `SharedCloudVectorConfig`。
- `DataAgentQdrantProfile` 由共享配置或 override 解析得到，并服务于 Java Qdrant 客户端初始化。
- 一次 `EmbeddingProxyRequest` 产生一份 `EmbeddingProxyResponse`，用于 Java DataAgent 的 schema 向量化。
- `RefreshExecution` 依赖当前 `model-list`、`SharedCloudVectorConfig` 和 `RemoteRetrievalState` 才能执行。
- `RemoteRetrievalState` 反映常规启动后的增强能力健康度，并决定 DataAgent 是否退回基础模式。

## State Transitions

### 1. 常规启动能力状态

| State | Trigger | Next State | Rule |
|------|---------|------------|------|
| `uninitialized` | 服务启动读取配置 | `available` / `degraded` | 根据共享能力校验结果判定 |
| `available` | 某项增强能力在运行时检查失败 | `degraded` | 记录告警并关闭对应增强能力 |
| `degraded` | 运维修复环境并重新启动 | `available` / `degraded` | 重新校验后决定 |

### 2. 强制刷新执行状态

| State | Trigger | Next State | Rule |
|------|---------|------------|------|
| `pending` | `force-refresh=true` 且启动进入刷新流程 | `running` | 仅处理当前 `model-list` |
| `running` | 当前步骤成功 | `running` / `completed` | 所有步骤完成后进入 `completed` |
| `running` | 任一步失败 | `failed` | 立即终止，不继续后续步骤 |
| `failed` | 运维修复问题后再次执行 | `running` | 已完成步骤不强制回滚 |
| `completed` | 成功完成远端清理 | 结束 | 远端仅保留当前 `model-list` 数据 |

## Validation Notes

- 共享配置缺项时，只允许在“能力未启用”或“存在合法 override”场景下继续；否则必须降级并输出明确告警。
- Qdrant 维度不匹配、ES 缺少前置分析器、embedding 代理不可达，都属于可定位的失败原因，不能吞掉。
- `force-refresh` 默认关闭；任何非布尔或异常配置都应按安全默认值 `false` 处理。
- DataAgent 降级后仍需保持主链路可用，只关闭 schema 向量增强或列值增强召回，不得整体禁用问数服务。
