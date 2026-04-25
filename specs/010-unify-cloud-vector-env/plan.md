# Implementation Plan: 统一 DataAgent 与 MRAG 的云端向量环境

**Branch**: `[010-unify-cloud-vector-env]` | **Date**: 2026-04-25 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/010-unify-cloud-vector-env/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

在不改前端、不新增数据库结构的前提下，统一 DataAgent 与 MRAG 的云端向量环境：Java `DataAgent` 与 Python `reactor-tool` 复用同一套 `QDRANT_*`、`TEXT_EMBEDDING_*`、`TR_ES_CONFIGS_*` 配置；Java 侧补齐基于 URL/TLS 的云端 Qdrant 接入、共享文本向量代理消费、ES `scheme` 支持与显式 `force-refresh` 重建机制；Python 侧补齐可供 Java 调用的文本 embedding 代理端点，并让 `table_rag` 在缺省时回退到共享 Qdrant 配置。方案保留旧本地模式、旧 embedding override 和 `table_rag` 专属覆盖，失败时以“能力降级但主流程可用”为默认运行策略，以“强制刷新失败即终止”为迁移策略。

## Technical Context

**Language/Version**: Java 17（`ai-agent-station-study-domain` / `ai-agent-station-study-app`） + Python 3.11（`reactor-tool`）；本期不改 `ui/`  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / MyBatis-Plus 既有配置装配、OkHttp 4.9.3、FastAPI、Pydantic v2、qdrant-client、MRAG 现有文本 embedding 适配层  
**Storage**: 复用既有 MySQL 问数模型元数据；远端 Qdrant collection `reactor_model_schema`；远端 Elasticsearch index `reactor_model_column_value`；无新增表/列  
**Testing**: Java 定向单测 / 集成测试 + `reactor-tool` 启动与接口冒烟 + DataAgent / `table_rag` / MRAG 手工联调验收  
**Target Platform**: Spring Boot 问数主服务 + FastAPI `reactor-tool` 工具服务  
**Project Type**: 跨栈后端配置与检索能力统一  
**Performance Goals**: 常规启动不得因增强能力失败阻断主服务；文本 embedding 代理满足现有 DataAgent 批量向量调用；显式刷新在一次发布窗口内可重复执行并输出明确结果  
**Constraints**: 必须遵守 DDD 分层；Java 与 Python 共用配置契约但职责分离；保留旧本地 host/port 模式、旧 `TR_EMBEDDING_URL` override 和 `table_rag` 专属配置；首次迁移依赖显式 `force-refresh`，不能隐式重建  
**Scale/Scope**: 影响 `ai-agent-station-study-domain`、`ai-agent-station-study-app`、`reactor-tool`；新增 1 个内部文本 embedding HTTP 契约、若干配置字段与降级/刷新策略；无 UI、数据库结构或新服务部署变更

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更是否遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界？
- [x] 是否优先复用了现有 Agent、Tool、Prompt、RAG、DAO、配置装配能力？
- [x] 是否为每个关键改动点定义了可执行验证方式？
- [x] 是否将外部调用、流式链路、任务编排的异常与可观测性纳入方案？
- [x] 若提高了复杂度，是否在 `Complexity Tracking` 中给出合理说明？

## Project Structure

### Documentation (this feature)

```text
specs/010-unify-cloud-vector-env/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
ai-agent-station-study-api/
ai-agent-station-study-app/
├── src/main/java/org/wwz/ai/config/
├── src/main/resources/
│   ├── application-*.yml
│   ├── db/
│   └── mybatis/mapper/
└── src/test/java/org/wwz/ai/test/
ai-agent-station-study-domain/
├── src/main/java/org/wwz/ai/domain/
└── src/test/java/
ai-agent-station-study-infrastructure/
├── src/main/java/org/wwz/ai/infrastructure/
└── src/test/java/
ai-agent-station-study-trigger/
├── src/main/java/org/wwz/ai/trigger/
└── src/test/java/
ai-agent-station-study-types/
mcp-server-csdn/mcp-server-csdn/
ui/
├── src/
└── package.json
reactor-client/
reactor-tool/
```

**Structure Decision**: 不新增顶层模块。Java DataAgent 相关配置与运行策略保留在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/dataAgent/**`；应用配置样例与装配留在 `ai-agent-station-study-app/src/main/resources`；共享 embedding 代理与 `table_rag` 配置回退逻辑落在 `reactor-tool/reactor_tool/api/**` 与 `reactor_tool/tool/**`。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 承载 DataAgent 的共享向量配置解析、云端 Qdrant 连接、ES 增强召回修正、能力降级与强制刷新编排 |
| `ai-agent-station-study-app` | modify | 暴露统一环境变量映射、默认配置与测试样例 |
| `ai-agent-station-study-infrastructure` | none | 本期不新增 DAO、仓储或独立外部网关层 |
| `ai-agent-station-study-trigger` | none | 不新增 Controller；复用既有 Agent/问数入口 |
| `ui` | none | 前端零改动，保持现有问数与 MRAG 对话入口 |
| `reactor-tool` | modify | 新增文本 embedding 代理端点，并让 `table_rag` / MRAG 共享云端向量配置 |

## Layer Boundary Notes

- `domain` should contain business rules, execution strategies, prompt orchestration, and tool selection logic.
- `infrastructure` should contain DAO/gateway implementations and technical adapters.
- `trigger` should only expose endpoints/listeners/jobs and delegate to services.
- `app` should assemble beans, configs, Mapper XML, and runtime wiring.
- Java 侧的“共享云端配置 -> DataAgent 能力是否可用 -> 是否执行刷新/降级”属于运行策略，保留在 `domain`，不把业务判断塞进 `application-dev.yml` 或启动类。
- `reactor-tool` 只负责共享 embedding 代理与 Python 检索配置复用，不接管 Java 侧模型元数据初始化、Qdrant collection 重建或 ES 索引重建。
- `table_rag` 专属 override 仍归 Python 工具层自身解析，但其回退目标改为共享 `QDRANT_*` 契约，避免双写配置。
- 远端索引与集合的清理策略由 Java `force-refresh` 执行链路负责，避免把远端一致性职责分散到请求时路径。

## Data / Config / Contract Changes

- **Database**: N/A，本期不新增表、列、索引；仅重用既有 `chat_model_info` / `chat_model_schema` 作为刷新数据源
- **Config**: 新增或统一使用 `QDRANT_URL`、`QDRANT_PORT`、`QDRANT_API_KEY`、`QDRANT_PREFER_GRPC`、`TEXT_EMBEDDING_TYPE`、`TEXT_EMBEDDING_BASE_URL`、`TEXT_EMBEDDING_API_KEY`、`TEXT_EMBEDDING_MODEL_NAME`、`TEXT_EMBEDDING_DIMENSION`、`TR_ES_CONFIGS_HOST`、`TR_ES_CONFIGS_SCHEME`、`TR_ES_CONFIGS_USER`、`TR_ES_CONFIGS_PASSWORD`；DataAgent 新增 `force-refresh`；`table_rag` 保留 `TR_QDRANT_*` / `TR_EMBEDDING_URL` 作为 override
- **Contract**: 新增内部 `POST /v1/tool/embedding/text` 契约，入参为 `{"inputs":[...],"normalize":true|false}`，返回 `List<List<Float>>`；补充共享配置优先级与刷新语义文档契约
- **Compatibility**: 保持旧本地 host/port 模式、旧显式 embedding override、`table_rag` 专属 Qdrant 配置和现有集合/索引命名稳定；增强能力失败时降级但不整体禁用 DataAgent 主流程

## Verification Plan

- **Java**: 补/跑 `ai-agent-station-study-domain` 中 DataAgent 相关配置解析、Qdrant URL/TLS、ES `scheme`、刷新短路/强制刷新、能力降级、`SchemaRecallService` 字段修正等定向测试；必要时补 `ai-agent-station-study-app` 配置装配测试
- **UI**: N/A（仅通过现有问数/对话入口做手工回归）
- **Python**: `cd reactor-tool && uv run python server.py`；随后对 `/v1/tool/embedding/text` 与 `/v1/tool/table_rag` 做 HTTP 冒烟；补充共享配置回退和旧 override 兼容测试
- **Manual**: 验证“仅旧本地配置”“仅共享云端配置”“共享配置 + `table_rag` override”“显式 `force-refresh`”“增强能力降级后 DataAgent 退回基础模式”五类路径

## Complexity Tracking

> 当前方案无宪章违例，无需额外登记。
