# Implementation Plan: 移植 MRAG 多模态知识检索能力

**Branch**: `[008-fix-summary-markdown]` | **Date**: 2026-04-24 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/009-mrag-capability-port/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

在不改前端、不新增数据库结构的前提下，为当前 Spring Boot Agent 运行时补齐 MRAG 多模态知识检索能力：Java 侧新增 `multimodalagent_tool` 并接入现有 `AgentToolCollectionFactory` / `ReactorConfig` / SSE 持久化链路，Python 侧将 `/v1/tool/mragQuery` 并入当前 `reactor-tool`。方案复用现有 `knowledge` / `markdown` / 文件产物展示能力，对 `REACT` 与 `PlanSolve` 默认开放，对 `dataAgent` 保持不暴露，并在失败时显式报错而不是静默回退。

## Technical Context

**Language/Version**: Java 17（`ai-agent-station-study-domain` / `ai-agent-station-study-app`） + Python 3.11（`reactor-tool`）；本期不改 `ui/`  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、OkHttp 4.9.3、FastJSON 1.2.83、FastAPI、Pydantic v2、sse-starlette、qdrant-client、fastembed，以及 MRAG 实现实际依赖的最小补集  
**Storage**: 复用现有 MySQL 会话/事件持久化、现有文件服务或本地 `FILE_SERVER_URL` 产物存储、MRAG 所需知识库/向量索引由 `reactor-tool` 侧承接  
**Testing**: Java 定向 Maven 测试 + `reactor-tool` 启动/接口冒烟 + 现有对话链路手工验收  
**Target Platform**: Spring Boot Agent 运行时 + FastAPI `reactor-tool` 工具服务  
**Project Type**: 跨栈后端能力迁移（Java 运行时 + Python 工具服务）  
**Performance Goals**: 成功样本首个 MRAG 可见片段 5 秒内返回；工具执行不能无限等待；失败场景必须显式结束  
**Constraints**: 必须遵守 DDD 分层；复用 `AgentToolCollectionFactory`、`ReactorConfig`、`SSEPrinter`、现有会话持久化链路；不改前端/数据库；不引入独立长期 MRAG 服务；失败时不自动降级普通搜索  
**Scale/Scope**: 影响 `ai-agent-station-study-domain`、`ai-agent-station-study-app`、`reactor-tool`；新增 1 个 Java Tool、2 个 Java DTO、1 个 Python API 路由、1 个 Python 请求模型及 MRAG 运行目录；无新增表/列

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
specs/009-mrag-capability-port/
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

**Structure Decision**: 不新增顶层模块。Java 运行时改动放在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/**`；运行配置与测试样例放在 `ai-agent-station-study-app/src/main/resources`、`ai-agent-station-study-app/src/test/java`；MRAG Python 能力并入 `reactor-tool/reactor_tool/api`、`reactor-tool/reactor_tool/model` 与新建的 `reactor-tool/reactor_tool/tool/mrag/`。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 新增 `MultiModalAgent`、DTO、工具装配、结果可见性抑制、事件流归类与异常收口 |
| `ai-agent-station-study-app` | modify | 新增/调整 YAML 配置与对应测试样例 |
| `ai-agent-station-study-infrastructure` | none | 本期无新增 DAO、仓储或外部网关实现 |
| `ai-agent-station-study-trigger` | none | 不新增 Controller/入口，沿用现有对话入口 |
| `ui` | none | 前端零改动，复用现有会话流与产物展示 |
| `reactor-tool` | modify | 增加 `/v1/tool/mragQuery`、请求模型、MRAG 运行目录及依赖 |

## Layer Boundary Notes

- `domain` should contain business rules, execution strategies, prompt orchestration, and tool selection logic.
- `infrastructure` should contain DAO/gateway implementations and technical adapters.
- `trigger` should only expose endpoints/listeners/jobs and delegate to services.
- `app` should assemble beans, configs, Mapper XML, and runtime wiring.
- 本期不会照搬参考项目中的 `GenieController` / `GenieConfig`；当前仓库的等价扩展点是 `AgentToolCollectionFactory` 与 `ReactorConfig`。
- MRAG SSE 解析、工具结果抑制和 Markdown 产物生成属于 Agent 运行时行为，保留在 `domain`。
- `reactor-tool` 只负责暴露 `mragQuery` 与封装底层 MRAG 检索，不承担会话持久化、前端展示协议和 Java 侧文件上传编排。
- `app` 仅承载配置项和测试，不在 YAML 或启动装配层写业务分支判断。

## Data / Config / Contract Changes

- **Database**: N/A，本期不新增表、列、索引或初始化 SQL
- **Config**: 新增 `autobots.autoagent.tool.multimodalagent_tool.desc/params`、`autobots.autoagent.multimodalagent_url`，调整 `autobots.autoagent.tool_list.default`，补充 `message_interval.knowledge` 约定；`reactor-tool` 侧补充 MRAG 知识库与外部依赖环境变量
- **Contract**: 新增 `POST /v1/tool/mragQuery` SSE 契约；新增 Java DTO `MultiModalAgentRequest` / `MultiModalAgentResponse`；新增 `multimodalagent_tool` 参数 Schema
- **Compatibility**: 保持 `dataAgent` 现有工具集合不变；继续保留 `knowledge_url` 给 `sopRecall` 使用；当未启用 `multimodalagent` 或未触发 MRAG 时，普通搜索/报告/文件/历史回放行为不变；`LLM` 的 `base64Image` 支持与 `BaseAgent` 并发释放修复已存在，不作为本期新增

## Verification Plan

- **Java**: `mvn test -pl ai-agent-station-study-app -Dtest=AgentToolCollectionFactoryTest,ConversationHistoryArtifactTest,ConversationHistoryPersistenceTest,SessionTranscriptBlockAssemblerTest -DskipTests=false`，并为 `MultiModalAgent` / 工具注册新增定向测试
- **UI**: N/A（仅复用现有页面做手工回归）
- **Python**: `cd reactor-tool && uv run python server.py`，随后用 `curl -N http://127.0.0.1:1601/v1/tool/mragQuery ...` 做 SSE 冒烟；如补单测则执行对应 `pytest`
- **Manual**: 通过现有对话入口分别验证 `REACT` / `PlanSolve` 默认可用、生成 Markdown 产物、上游超时显式失败、移除 `multimodalagent` 后新会话不再暴露该工具、`dataAgent` 仍不暴露该能力

## Complexity Tracking

> 当前方案无宪章违例，无需额外登记。
