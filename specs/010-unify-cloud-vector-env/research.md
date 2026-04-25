# Phase 0 Research

## Decision 1: 共享云端配置采用单一契约，`table_rag` 仅保留专属覆盖而不是再维护第二套默认值

**Decision**: 以共享 `QDRANT_*`、`TEXT_EMBEDDING_*`、`TR_ES_CONFIGS_*` 作为 MRAG 默认配置契约；DataAgent / `table_rag` 改为显式使用 `DATA_AGENT_QDRANT_*`，`table_rag` 仅在明确提供 `TR_QDRANT_*` 或旧 `TR_EMBEDDING_URL` 时走专属覆盖，不再回退 MRAG 的共享 Qdrant 配置。  
**Rationale**: 本次需求的核心是统一配置入口并降低运维重复劳动。若 `table_rag` 继续持有一套独立默认值，运维仍然需要同时维护两套连接信息，无法达成“单套配置启用两条检索链路”的目标。保留 override 只用于兼容旧部署和局部差异化。  
**Alternatives considered**:
- 维持 DataAgent / MRAG / `table_rag` 三套平行默认配置：兼容性最高，但完全偏离本期目标。
- 强制删除所有专属覆盖：实现更干净，但会破坏存量环境的渐进迁移能力。

## Decision 2: Java 云端 Qdrant 接入采用“URL 解析 + 端口/TLS 推断”的兼容策略

**Decision**: 扩展 Java `QdrantConfig` 支持 `url` 与 `preferGrpc`，客户端初始化优先解析 `QDRANT_URL` 的 scheme、host、port，再结合 `QDRANT_PORT` 和 `preferGrpc` 生成 gRPC 客户端；保留旧 `host` / `port` / `apiKey` 直连方式。  
**Rationale**: 托管 Qdrant 的关键差异是 TLS 与域名化入口，而不是简单 host/port。以 URL 为主可以优雅覆盖 `https`、自定义端口和云端域名；同时保留 host/port 兼容旧本地环境，避免一次性破坏历史配置。  
**Alternatives considered**:
- 继续只支持 host/port：无法稳妥表达托管实例的 TLS 语义。
- 直接把完整 URL 透传给 Java SDK 的 HTTP 客户端：会偏离现有 gRPC 接入路径，并增加 DataAgent 侧改动面。

## Decision 3: 共享文本向量能力通过 `reactor-tool` 内部代理端点暴露，而不是要求独立 embedding 服务

**Decision**: 在 `reactor-tool` 新增固定内部端点 `POST /v1/tool/embedding/text`，Java `EmbeddingService` 默认调用该端点，复用 MRAG 当前的 `TEXT_EMBEDDING_*` 适配层。  
**Rationale**: MRAG 已经具备稳定的文本 embedding 提供能力和环境契约，再额外部署独立 embedding 服务只会增加运维面和配置漂移。通过 `reactor-tool` 代理统一输出 Java 需要的批量向量格式，可以最大化复用既有能力，同时把 provider 差异继续封装在 Python 侧。  
**Alternatives considered**:
- Java 直接调用外部 embedding provider：会复制一套 provider 适配逻辑，违背“共享现有能力”的原则。
- 保持必须配置 `TR_EMBEDDING_URL`：能用，但继续要求额外独立服务，不满足规格。

## Decision 4: ES 统一配置继续沿用 `TR_ES_CONFIGS_*`，但 Java 必须补齐 `scheme` 支持并修正字段名

**Decision**: 共享 Elasticsearch 配置沿用现有 `TR_ES_CONFIGS_HOST/USER/PASSWORD`，补充 `TR_ES_CONFIGS_SCHEME` 供 Java 使用；同时修复 Java 列值召回查询字段由 `model_code` 改为 `modelCode`。  
**Rationale**: Python 侧已经在使用 `TR_ES_CONFIGS_*`，继续复用可避免再造新契约。Java 目前把协议写死为 `http`，不适用于托管 ES；字段名不一致则会导致配置接通后召回仍为空，属于必须一并收敛的正确性问题。  
**Alternatives considered**:
- 新增一套 `DATA_AGENT_ES_*` 专属环境变量：职责更直观，但又产生重复配置。
- 保持字段名现状只改连接参数：连接成功后仍可能召回失败，无法形成完整闭环。

## Decision 5: 强制刷新采用“显式开启、失败即终止、成功后清理陈旧远端数据”的迁移策略

**Decision**: 新增 `force-refresh` 开关，默认关闭；开启时以当前 `model-list` 为唯一目标，依次重建问数模型元数据、schema 向量和列值索引，任一步失败即终止并标记整次刷新失败；全部成功后删除远端不再属于当前 `model-list` 的旧数据。  
**Rationale**: 棕地迁移场景最怕“服务能启动，但远端数据半新半旧”。默认关闭可避免日常重启误触发破坏性动作；失败即终止可阻止错误继续扩散；成功后清理陈旧数据可让远端状态与当前配置严格对齐。  
**Alternatives considered**:
- 每次启动自动重建：风险过高，违背显式控制要求。
- 刷新失败时回滚已完成步骤：实现复杂且成本高，规格也未要求强回滚。
- 成功后不清理陈旧数据：会留下脏数据，影响后续召回一致性。

## Decision 6: 常规启动失败策略采用“服务继续启动，增强能力降级为不可用”

**Decision**: 在非 `force-refresh` 常规启动中，共享 Qdrant、embedding、ES 任一已启用能力初始化或校验失败时，只记录明确告警并把对应增强能力标记为不可用；DataAgent 主流程继续提供基础 schema / 基础问数模式。  
**Rationale**: 规格已经明确要求常规启动不因增强能力失败而阻断整体服务。这种策略更符合生产环境的可用性诉求，同时让故障面局限在增强召回，而不是扩大成整站不可用。  
**Alternatives considered**:
- 启动即 fail-fast：问题暴露最直接，但会让单点环境问题阻断整个主服务。
- 静默忽略失败继续标记为可用：最危险，会制造“看似健康”的隐性故障。

## Decision 7: 集合名、索引名和旧 override 契约保持稳定，避免迁移期运维资产失效

**Decision**: DataAgent 继续使用固定 Qdrant collection `reactor_model_schema` 与 ES index `reactor_model_column_value`；旧 `TR_EMBEDDING_URL` 与 `TR_QDRANT_*` override 继续保留，但文档和默认路径改为推荐共享配置。  
**Rationale**: 命名稳定是棕地系统迁移的重要前提。若在统一环境时顺手改集合名、索引名或直接删除旧 override，会同步打碎现有运维脚本、排障经验和回滚路径，收益远小于风险。  
**Alternatives considered**:
- 顺带重命名集合/索引：理论上更“统一”，但不符合规格约束。
- 立即移除旧 override：配置更简洁，但不支持分阶段迁移。
