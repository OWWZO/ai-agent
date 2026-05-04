## Why

Phase 1 只为 execution ledger 建立了第一条 repository seam，但 `domain` 内仍保留整组 MyBatis DAO / Mapper，`app` 的 Mapper XML 也仍然直接绑定 `org.wwz.ai.domain.agent.reactor.mapper.*`。如果继续让这些持久化类型停留在领域层，后续 Spring 运行时解耦、tool-output 收敛和 data engine 抽离都会被“domain 持有 DAO”这一结构性问题反复拖慢。

现在需要优先启动 Phase 2A，把 Reactor 现有 DAO / Mapper 从 `domain` 物理迁出，并为仍直接依赖这些 DAO 的 domain service 补齐最小 repository seam。这样可以先把“domain 不承载持久化接口”这条 DDD 基线真正落地，同时把后续阶段的治理范围压缩到更清晰的端口和服务职责层面。

## What Changes

- 将 `org.wwz.ai.domain.agent.reactor.mapper.*` 下现存的 ledger、tool-output、chat-model 相关 DAO / Mapper 物理迁移到 `ai-agent-station-study-infrastructure` 的 `dao/` 包空间。
- 同步调整 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/*.xml` 的 namespace、`resultMap` 绑定与扫描装配，确保 MyBatis 运行时继续指向迁移后的 DAO 类型。
- 在 `domain/adapter/repository/` 为本期仍直接依赖 DAO 的能力补齐最小 repository port，至少覆盖 chat-model 元数据服务，以及后续可平滑承接 session-memory、workspace-image、tool-output 的 seam 位置。
- 在 `infrastructure/adapter/repository/` 落地对应 production repository adapter，把 `ChatModelInfoService`、`ChatModelSchemaService` 等当前直接依赖 `BaseMapper` / DAO 的实现从“直接持久化风格”收敛为“依赖 repository port 的领域服务风格”。
- 对 `SessionContextMemoryServiceImpl`、`WorkspaceImageGenerationServiceImpl` 以及 `ToolOutputReaderImpl / ToolOutputWriterImpl` 明确保持 Phase 2A 过渡态：允许短期继续复用已迁出的 infrastructure DAO，但不得再新增对 `domain.reactor.mapper` 的依赖。
- 补齐边界回归、Mapper 装配回归与聚焦编译检查，并更新模块文档，锁定 `domain` 内 `@Mapper` / `BaseMapper` 命中数为 0。

## Capabilities

### New Capabilities
- `reactor-ddd-persistence-boundary`: 定义 Reactor Phase 2A 如何将 DAO / Mapper 物理收拢到 infrastructure，并通过 repository seam 让 domain service 摆脱直接持久化接口依赖。

### Modified Capabilities
- None.

## Impact

- 主要影响 `ai-agent-station-study-domain`、`ai-agent-station-study-infrastructure`、`ai-agent-station-study-app` 三个模块，以及 `openspec/changes/reactor-ddd-phase1-boundary-convergence/` 之后保留下来的 Reactor 持久化链路。
- 不新增数据库表、列或新的外部服务依赖，但会批量修改 Reactor ledger / tool-output / chat-model 相关 Mapper XML namespace 与 DAO 包名。
- 不改变 conversation history、tool-output、session-memory、workspace-image、问数模型管理等现有对外 API / SSE 契约；本期只收敛持久化边界和 service 依赖方式。
- 需要新增和调整 repository 边界测试、Mapper 装配测试以及聚焦服务回归，确保 production adapter 真正参与运行链路。
