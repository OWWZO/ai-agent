# ai-agent-station-study Development Guidelines

Auto-generated from project architecture and Spec Kit setup. Last updated: 2026-05-05

## Active Technologies
- Java 17（后端）；TypeScript 5 + React 19（`ui/`） + Spring Boot 3.4.3, Spring AI 1.1.4, MyBatis/MyBatis-Plus 风格 DAO + Mapper XML, OkHttp SSE, React 19, Vite 6, Ant Design 5, Radix UI (001-fix-role-library)
- MySQL（`ai_agent` / `ai_agent_flow_config` / `ai_agent_conversation` / `ai_client*`） (001-fix-role-library)
- Java 17（Spring Boot 多模块主链路）；Python 3.11+（`reactor-tool`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis-Plus 3.5.14、FastJSON 1.2.83、OkHttp 4.9.3、FastAPI、Pydantic v2、PyYAML (002-agent-skill-mechanism)
- 文件系统中的运行时 skill 目录；现有文件服务用于脚本输出文件上传；无新增数据库表 (002-agent-skill-mechanism)
- Java 17（Spring Boot 多模块主链路）；TypeScript 5 + React 19（`ui/`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、MySQL 8、React 19、Vite 6、Ant Design 5、现有 `reactor-tool` 文件上传能力 (004-conversation-history-refactor)
- MySQL（`ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`）+ 现有文件服务返回的稳定资源 URL / key (004-conversation-history-refactor)
- Java 17（后端主链路）；TypeScript 5 + React 19（`ui/`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、MySQL 8、React 19、Vite 6、Ant Design 5 (005-fix-history-replay)
- MySQL（`ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`）+ 现有文件服务稳定资源引用 (005-fix-history-replay)
- Java 17（Spring Boot 多模块主链路） + TypeScript 5 / React 19（`ui/`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis DAO + Mapper XML、MySQL 8、React 19、Vite 6、Ant Design 5、ahooks、现有 `ActionView/FilePreview/Dialogue` 组件链 (005-fix-history-replay)
- MySQL（`ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`）+ 文件服务稳定 `artifactRefs` 引用 (005-fix-history-replay)
- Java 17（Spring Boot 多模块主链路） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis-Plus 3.5.14、OkHttp、MySQL 8、现有 ReAct / PlanSolve Agent 框架 (006-session-context-memory)
- MySQL（既有 `ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`，新增会话记忆摘要快照表） (006-session-context-memory)
- Java 17（仅后端主链路，本期不改 `ui/`、`reactor-tool/`、`reactor-client/`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、OkHttp SSE、现有 ReAct / PlanSolve Agent 框架 (006-session-context-memory)
- MySQL 既有 `ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`、`ai_agent_session_memory`；本期无新增表/列 (006-session-context-memory)
- Java 17（仅后端主链路，本期不改 `ui/`、`reactor-tool/`、`reactor-client/`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、现有 Reactor `LLM`/`ChatClient` 装配能力、OkHttp SSE、既有 rich transcript 组装链路 (007-freecode-session-compaction)
- MySQL 既有 `ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`、`ai_agent_session_memory`；其中 `ai_agent_session_memory` 演进为同一 `session_id` 的多版本快照 (007-freecode-session-compaction)
- TypeScript 5.7 + React 19（仅 `ui/`，本期不改 Java / Python 子系统） + Vite 6、Ant Design 5、react-markdown 10.1.0、remark-gfm 4.0.1、streamdown 2.5.0、现有 `ai-elements` 消息渲染组件链 (008-fix-summary-markdown)
- N/A（纯前端展示修复，不新增数据库、接口持久化或文件存储） (008-fix-summary-markdown)
- Java 17（`ai-agent-station-study-domain` / `ai-agent-station-study-app`） + Python 3.11（`reactor-tool`）；本期不改 `ui/` + Spring Boot 3.4.3、Spring AI 1.1.4、OkHttp 4.9.3、FastJSON 1.2.83、FastAPI、Pydantic v2、sse-starlette、qdrant-client、fastembed，以及 MRAG 实现实际依赖的最小补集 (008-fix-summary-markdown)
- 复用现有 MySQL 会话/事件持久化、现有文件服务或本地 `FILE_SERVER_URL` 产物存储、MRAG 所需知识库/向量索引由 `reactor-tool` 侧承接 (008-fix-summary-markdown)
- Java 17（`ai-agent-station-study-domain` / `ai-agent-station-study-app`） + Python 3.11（`reactor-tool`）；本期不改 `ui/` + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / MyBatis-Plus 既有配置装配、OkHttp 4.9.3、FastAPI、Pydantic v2、qdrant-client、MRAG 现有文本 embedding 适配层 (010-unify-cloud-vector-env)
- 复用既有 MySQL 问数模型元数据；远端 Qdrant collection `reactor_model_schema`；远端 Elasticsearch index `reactor_model_column_value`；无新增表/列 (010-unify-cloud-vector-env)
- Java 17（后端主链路） + TypeScript 5 / React 19（`ui/` 仅做最小适配） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、MySQL 8、React 19、Vite 6、Ant Design 5、现有 `combineData / handleTaskData / FilePreview` 渲染链 (011-transcript-fact-persistence)
- MySQL（`ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`、`ai_agent_session_memory`）+ 现有稳定文件引用能力 (011-transcript-fact-persistence)
- Java 17（后端主链路） + TypeScript 5 / React 19（`ui/` 历史消费链最小但明确的适配） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、MyBatis-Plus 3.5.14、MySQL 8、OkHttp SSE、React 19、Vite 6、Ant Design 5、现有 `ActionView / FilePreview / Dialogue` 组件链 (012-transcript-block-refactor)
- MySQL（新增 `ai_agent_turn`、`ai_agent_transcript_block`、`ai_agent_display_event`，重写 `ai_agent_session_memory`），外加现有稳定文件/产物引用能力 (012-transcript-block-refactor)
- Java 17（仅后端主链路；本期不改 `ui/`、`reactor-tool/`、`reactor-client/`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis-Plus 3.5.14、MyBatis Mapper XML、MySQL 8、OkHttp SSE、现有 Reactor `AgentContext / BaseAgent / LLM / SSEPrinter / ToolArtifactRegistry` 运行时抽象 (013-dialogue-persistence)
- MySQL（新增 `ai_agent_dialogue_run`、`ai_agent_llm_invocation`、`ai_agent_tool_invocation`、`ai_agent_artifact` 四张表） (013-dialogue-persistence)
- Java 17（仅后端主链路；本期不改 `ui/`、`reactor-tool/`、`reactor-client/`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis-Plus 3.5.14、MyBatis Mapper XML、MySQL 8、FastJSON 1.2.83、现有 Reactor `AgentContext / BaseAgent / AgentExecutionRecorderImpl / ToolArtifactRegistry / ToolInvocationProjectorRegistry` 抽象 (014-tool-output-refactor)
- MySQL（删除 `ai_agent_tool_invocation.output_json`，新增 8 张 `ai_agent_tool_output_*` 工具输出表；继续复用 `ai_agent_artifact`） (014-tool-output-refactor)
- Java 17（后端主链路） + TypeScript 5 / React 19（`ui/`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、MyBatis-Plus 3.5.14、MySQL 8、OkHttp SSE、React 19、Vite 6、Ant Design 5、现有 `ToolInvocationProjectorRegistry` 与 `combineData / handleTaskData` 前端恢复链 (017-conversation-history-projector-replay)
- MySQL（新增 `ai_agent_dialogue_session`，复用 `ai_agent_dialogue_run`、`ai_agent_llm_invocation`、`ai_agent_tool_invocation`、`ai_agent_artifact`、`ai_agent_tool_output_*`） (017-conversation-history-projector-replay)
- Java 17（仅后端 Maven 多模块主链路；本期不改 `ui/`、`reactor-tool/`、`reactor-client/`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / MyBatis-Plus 3.5.14、MySQL 8、OkHttp、现有 `ai-agent-station-study-case` 应用编排层、execution ledger / history replay / session memory 领域能力 (019-agent-ddd-convergence)
- 复用现有 MySQL 会话、执行账本、tool-output 与 session memory 持久化；本期不新增表、不改 schema (019-agent-ddd-convergence)
- Java 17（仅后端 Maven 多模块主链路；本期不改 `ui/`、`reactor-tool/`、`reactor-client/`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / MyBatis-Plus 3.5.14、MySQL 8、OkHttp、现有 `ai-agent-station-study-case` 应用编排层、runtime/ledger/memory/rag/role 子域骨架、既有 dataquery / image generation / history replay / session memory 能力 (020-prune-agent-bridges)
- 复用现有 MySQL 会话、账本、tool-output 与 session memory 持久化；本期不新增表、不改 schema (020-prune-agent-bridges)

- Java 17 + Spring Boot 3.4.3 + Spring AI 1.1.4 + MyBatis-Plus 3.5.14
- MySQL 8 + PostgreSQL 15/pgvector + Maven multi-module
- React 19 + TypeScript 5 + Vite 6 + Tailwind CSS 4 + Ant Design 5 (`ui/`)
- Python 3.10+/3.11+ + FastAPI + MCP tooling (`reactor-client/`, `reactor-tool/`)

## Project Structure

```text
ai-agent-station-study/
├── ai-agent-station-study-types/           # 基础类型、常量、任务调度接口
├── ai-agent-station-study-api/             # DTO 与服务接口契约
├── ai-agent-station-study-domain/          # 核心领域模型、Agent/Tool/RAG 业务逻辑
├── ai-agent-station-study-infrastructure/  # DAO、仓储实现、外部网关
├── ai-agent-station-study-trigger/         # Controller、Listener、Job
├── ai-agent-station-study-app/             # Spring Boot 启动、配置、Mapper XML、测试
├── mcp-server-csdn/                        # 独立 MCP Server
├── ui/                                     # React 前端
├── reactor-client/                         # Python MCP Client
├── reactor-tool/                           # Python 工具集与 MCP 工具实现
├── docs/                                   # 运维、方案、数据与提示词文档
├── CLAUDE.md                               # 项目总览与模块索引
└── .specify/ + .agents/skills/             # Spec Kit + Codex 集成
```

## Commands

- 后端启动: `mvn -pl ai-agent-station-study-app spring-boot:run`
- 应用测试: `mvn test -pl ai-agent-station-study-app -DskipTests=false`
- 领域层回归: `mvn test -pl ai-agent-station-study-domain -am -DskipTests=false`
- 前端开发: `cd ui && npm install && npm run dev`
- 前端构建: `cd ui && npm run build`
- 前端检查: `cd ui && npm run lint`
- Python 工具调试: `cd reactor-tool && uv run python server.py`
- Python 客户端调试: `cd reactor-client && uv run python server.py`

## Code Style

- 严格遵守 DDD 分层边界：`types` 放通用类型，`api` 放契约，`domain` 放业务规则，`infrastructure` 放实现细节，`trigger` 放入口适配，`app` 放装配与配置。
- 新功能优先复用已有 Agent、Tool、Prompt、RAG、DAO、配置装配能力，避免在 `trigger` 或 `app` 层堆业务逻辑。
- `domain` 层不直接依赖 Controller、Mapper XML、HTTP 细节；`infrastructure` 层负责落地 DAO、网关、MCP 外部接入。
- 涉及 Agent/Tool 扩展时，优先做可配置能力，尽量通过模型配置、工具注册、策略工厂、元数据装配实现，不要把新能力硬编码进单一路径。
- 涉及数据库变更时，同时检查 PO、DAO、Mapper XML、`schema.sql`、测试数据与相关管理接口是否需要同步调整。
- 涉及流式输出、消息事件、任务编排时，补齐持久化、事件记录、异常处理与可观测性，不做“只通主链路”的半成品实现。
- 命名保持英文语义化；复杂逻辑、边界条件和关键设计决策使用中文注释说明。

## Recent Changes
- 020-prune-agent-bridges: Added Java 17（仅后端 Maven 多模块主链路；本期不改 `ui/`、`reactor-tool/`、`reactor-client/`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / MyBatis-Plus 3.5.14、MySQL 8、OkHttp、现有 `ai-agent-station-study-case` 应用编排层、runtime/ledger/memory/rag/role 子域骨架、既有 dataquery / image generation / history replay / session memory 能力
- 019-agent-ddd-convergence: Added Java 17（仅后端 Maven 多模块主链路；本期不改 `ui/`、`reactor-tool/`、`reactor-client/`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / MyBatis-Plus 3.5.14、MySQL 8、OkHttp、现有 `ai-agent-station-study-case` 应用编排层、execution ledger / history replay / session memory 领域能力
- 017-conversation-history-projector-replay: Added Java 17（后端主链路） + TypeScript 5 / React 19（`ui/`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、MyBatis-Plus 3.5.14、MySQL 8、OkHttp SSE、React 19、Vite 6、Ant Design 5、现有 `ToolInvocationProjectorRegistry` 与 `combineData / handleTaskData` 前端恢复链


<!-- MANUAL ADDITIONS START -->
## Working Agreements

- 进入具体需求前，先判断影响模块，再决定是后端、前端、Python 工具单栈修改，还是跨栈联动；默认优先做最小闭环，不轻易跨模块扩散。
- 需求涉及现有 Java 主链路时，优先阅读根级 `CLAUDE.md` 与对应模块下的 `CLAUDE.md`，再进入实现。
- 新增接口、管理项、Agent 类型、MCP 工具、RAG 能力时，先补规格和验收标准，再落计划和任务拆分。
- `012-transcript-block-refactor` 已切换为硬重写：运行链路只允许使用 `ai_agent_turn / ai_agent_transcript_block / ai_agent_display_event / ai_agent_session_memory`，不得再把旧 `ai_agent_message*` 账本接回主路径。
- 未经明确需要，不主动改动 `reactor-tool/` 下的可执行文件、cookie 文件和运行产物。
- 仓库可能存在用户未提交改动；修改前先确认文件是否已有在途变更，避免覆盖。
<!-- MANUAL ADDITIONS END -->
