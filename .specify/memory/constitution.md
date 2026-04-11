# AI Agent Station Study Constitution

## Core Principles

### I. DDD Boundary First

所有设计和实现必须先满足模块职责清晰，再追求交付速度。`types` 仅承载基础类型和通用能力，`api` 只暴露契约，`domain` 持有核心业务规则和策略编排，`infrastructure` 负责 DAO/仓储/外部网关实现，`trigger` 负责 HTTP/任务/监听器入口，`app` 负责装配与配置。任何计划如果让业务逻辑穿透到 `trigger`、让持久化细节泄漏到 `domain`，或让配置层承载业务判断，必须先调整方案再继续。

### II. Configurable Agent Capability

本项目的核心资产是 Agent、Tool、Prompt、RAG、MCP 的可组合能力。新增功能优先扩展现有策略工厂、工具注册、模型配置、流程配置和元数据装配机制，而不是写一次性分支逻辑。若需求涉及 Agent 类型、工具调用、流程节点、系统提示词、模型参数或工具清单，方案必须说明如何复用现有抽象，并明确新增能力由哪个配置或扩展点接管。

### III. Reuse Existing Ecosystem First

实现前优先评估并复用项目中已经存在的成熟能力，其次复用主流生态和高质量开源实践，最后才自行造轮子。对 Java 主链路优先沿用 Spring Boot、Spring AI、MyBatis-Plus、现有 DAO/配置装配模式；对前端优先复用 `ui/` 已有组件和请求工具；对 Python 工具优先复用 `reactor-tool/`、`reactor-client/` 里的现有 FastAPI/MCP 结构。独立实现必须说明为什么现有能力无法承接。

### IV. Verification Over Assumption

所有变更必须有可执行验证路径，不能依赖“理论上可行”。涉及领域逻辑、执行策略、工具调用、Mapper、数据库、配置装配、前端关键交互、Python 工具协议时，计划中必须写明至少一条对应验证方式。若改动影响 Java 主链路，优先补或运行 `ai-agent-station-study-app`/`domain` 下相关测试；若影响 SQL、Mapper 或 DAO，必须验证持久化链路；若影响 UI 或 Python 工具，也要补构建、lint、启动或最小冒烟验证。

### V. Observable, Safe, and Incremental Delivery

功能必须支持渐进交付，优先让单个用户故事形成可独立验证的闭环。涉及流式输出、任务编排、异步回调、外部 MCP、搜索、RAG、文件处理时，必须考虑异常兜底、日志与事件记录、超时与空结果处理、配置缺失场景以及向后兼容。任何需要提升复杂度的方案，都要在计划中记录复杂度理由和更简单方案为何不可行。

## Project Constraints

- 后端主栈固定为 Java 17、Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis-Plus 3.5.14；新增基础设施优先兼容当前栈，不轻易引入重型替代框架。
- 数据存储默认围绕 MySQL 与 PostgreSQL/pgvector 设计；涉及数据结构变化时，需同时考虑实体、DAO、Mapper XML、初始化 SQL、配置和回归验证。
- `ui/`、`reactor-tool/`、`reactor-client/` 是并行子系统。非必要不做跨栈大改；确需联动时，计划必须列清楚模块影响面与验收顺序。
- 复杂代码块、边界条件和关键设计决策需配中文注释；命名保持英文语义化，避免缩写驱动设计。
- 仓库是棕地项目，现有 `CLAUDE.md`、模块级 `CLAUDE.md` 和 `.claude/index.json` 视为一等上下文来源。开始较大改动前，应先复用这些项目记忆，而不是凭空重建认知。

## Delivery Workflow

- 新需求默认先产出 `spec.md`，用用户场景、验收条件、边界情况定义需求，再进入 `plan.md` 和 `tasks.md`。
- `plan.md` 必须至少回答五件事：影响模块、复用的现有能力、跨层边界是否被破坏、需要哪些数据/配置/契约变更、如何验证。
- `tasks.md` 必须按用户故事拆分，任务描述中写出准确文件路径，优先形成可独立演示与回归的增量。
- 如存在不确定点，优先使用 `clarify` 或在规格中显式记录假设，不允许把关键歧义拖到实现阶段才临时决策。
- 若计划违反本宪章中的任一原则，必须在 `Complexity Tracking` 中说明必要性、替代方案和风险控制。

## Governance

本宪章高于临时实现习惯和个人偏好。所有规格、计划、任务拆解和代码评审都必须检查是否满足本宪章。宪章修订需要同步更新根级 `AGENTS.md` 与相关模板，确保 Codex、Spec Kit 和团队文档的一致性。

**Version**: 1.0.0 | **Ratified**: 2026-04-11 | **Last Amended**: 2026-04-11
