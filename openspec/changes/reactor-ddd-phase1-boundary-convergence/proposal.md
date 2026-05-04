## Why

`domain/agent/reactor` 目前同时承载了 HTTP 入口、低风险 Spring 装配以及直接面向 ledger DAO 的服务实现，已经明显越过项目既定的 DDD 分层边界。继续在这条链路上叠加功能，会让 `domain` 对 Web、Spring 和 MyBatis 细节的耦合越来越深，也会让后续的 `tool-output / session-memory / workspace-image` 收口变得更难。

现在需要先做一个范围受控的 Phase 1，把最明确、最低风险的错层职责迁回既有模块边界，并为 execution ledger 建立第一条稳定的 repository seam。这样后续阶段才能在不重写 Reactor 运行时内核的前提下，继续推进更深的边界治理。

## What Changes

- 将 legacy `ReactorController` 与 `DataAgentController` 从 `domain` 迁到 `trigger`，保持现有 URL、参数和返回契约不变。
- 将 `ReplayProjectorAutoConfiguration`、`Es7HighLevelClientConfig`、`DataAgentInitRunner` 从 `domain` 迁到 `app`，只调整归属层级，不改变现有 Bean 装配结果。
- 在 `domain` 新增 `IExecutionLedgerWriteRepository` 与 `IExecutionLedgerReadRepository` 仓储契约，并让 `AgentExecutionRecorderImpl`、`ExecutionLedgerQueryServiceImpl` 改为依赖端口而不是直接依赖 DAO。
- 在 `infrastructure` 新增 execution-ledger repository adapter，Phase 1 继续复用现有 `domain.reactor.mapper` 与 `domain.reactor.entity` 作为过渡型 persistence contract。
- 补齐控制器、装配拓扑、ledger 边界与回放链路回归测试，并同步更新模块职责文档。
- 明确本期不处理 `ReactorConfig`、`SessionContextMemoryServiceImpl`、`ToolOutputWriterImpl / ToolOutputReaderImpl`、`WorkspaceImageGenerationServiceImpl` 与 ledger 持久化类型物理迁移。

## Capabilities

### New Capabilities
- `reactor-ddd-boundary-convergence`: 定义 Reactor Phase 1 如何在不改变现有运行时行为和外部路由契约的前提下，收拢 HTTP 入口、低风险 Spring 装配以及 execution ledger 的仓储边界。

### Modified Capabilities
- None.

## Impact

- 主要影响 `ai-agent-station-study-domain`、`ai-agent-station-study-infrastructure`、`ai-agent-station-study-trigger`、`ai-agent-station-study-app` 四个模块的 Reactor 相关目录。
- 不引入新的数据库表、列或新的外部依赖；Phase 1 的 repository adapter 继续复用现有 ledger DAO、实体与 Mapper XML。
- 不改变 `/1/**`、`/data/**` 的现有 HTTP 契约，也不改变 execution ledger、tool-output、session-memory 的既有对外消费协议。
- 需要新增和调整聚焦回归测试，锁定控制器迁移、Spring 装配位置、repository seam 和 Phase 1 延后范围不回退。
