# ai-agent-station-study Development Guidelines

Auto-generated from project architecture and Spec Kit setup. Last updated: 2026-04-12

## Active Technologies
- Java 17（后端）；TypeScript 5 + React 19（`ui/`） + Spring Boot 3.4.3, Spring AI 1.1.4, MyBatis/MyBatis-Plus 风格 DAO + Mapper XML, OkHttp SSE, React 19, Vite 6, Ant Design 5, Radix UI (001-fix-role-library)
- MySQL（`ai_agent` / `ai_agent_flow_config` / `ai_agent_conversation` / `ai_client*`） (001-fix-role-library)
- Java 17（Spring Boot 多模块主链路）；Python 3.11+（`reactor-tool`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis-Plus 3.5.14、FastJSON 1.2.83、OkHttp 4.9.3、FastAPI、Pydantic v2、PyYAML (002-agent-skill-mechanism)
- 文件系统中的运行时 skill 目录；现有文件服务用于脚本输出文件上传；无新增数据库表 (002-agent-skill-mechanism)

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
- 002-agent-skill-mechanism: Added Java 17（Spring Boot 多模块主链路）；Python 3.11+（`reactor-tool`） + Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis-Plus 3.5.14、FastJSON 1.2.83、OkHttp 4.9.3、FastAPI、Pydantic v2、PyYAML
- 001-fix-role-library: Added Java 17（后端）；TypeScript 5 + React 19（`ui/`） + Spring Boot 3.4.3, Spring AI 1.1.4, MyBatis/MyBatis-Plus 风格 DAO + Mapper XML, OkHttp SSE, React 19, Vite 6, Ant Design 5, Radix UI

- `master`: 引入 `spec-kit`、`.specify/`、`.agents/skills/`，为 Codex 增加 Spec-Driven Development 能力

<!-- MANUAL ADDITIONS START -->
## Working Agreements

- 进入具体需求前，先判断影响模块，再决定是后端、前端、Python 工具单栈修改，还是跨栈联动；默认优先做最小闭环，不轻易跨模块扩散。
- 需求涉及现有 Java 主链路时，优先阅读根级 `CLAUDE.md` 与对应模块下的 `CLAUDE.md`，再进入实现。
- 新增接口、管理项、Agent 类型、MCP 工具、RAG 能力时，先补规格和验收标准，再落计划和任务拆分。
- 未经明确需要，不主动改动 `reactor-tool/` 下的可执行文件、cookie 文件和运行产物。
- 仓库可能存在用户未提交改动；修改前先确认文件是否已有在途变更，避免覆盖。
<!-- MANUAL ADDITIONS END -->
