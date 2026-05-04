## Context

当前 `domain/agent/reactor` 目录同时承载了三类不该停留在 `domain` 的职责：HTTP 入口、低风险 Spring 装配，以及直接依赖 ledger DAO 的服务实现。它们分散在 `controller/`、`config/`、`service/impl/` 等位置，导致 `domain` 对 `trigger`、`app`、`infrastructure` 的边界不断反向渗透。

本次 Phase 1 的目标不是“大重构 Reactor”，而是先把已经确认的错层职责迁回既有模块边界，同时为 execution ledger 建立第一条稳定的 repository seam。这样可以在不触碰运行时主循环、不提前启动持久化类型整体迁移的前提下，先把最容易放大维护成本的耦合点切开。

当前还存在几个明确约束：

- `ReactorConfig` 仍有 30+ 直接依赖点，并存在运行期 `ApplicationContext.getBean(ReactorConfig.class)` 拉取，本期不能物理迁移。
- `BaseAgent`、`SessionContextMemoryServiceImpl`、`ToolOutputWriterImpl / ToolOutputReaderImpl`、`WorkspaceImageGenerationServiceImpl` 仍和现有 runtime / ledger 持久化强耦合，本期只能保持兼容。
- `domain.reactor.mapper` 与 `domain.reactor.entity` 已被多条链路复用，Phase 1 只能把它们视为过渡态 persistence contract，不能在本期顺手迁库或切换 Mapper XML namespace。

## Goals / Non-Goals

**Goals:**

- 让 legacy `ReactorController` 与 `DataAgentController` 从 `domain` 回到 `trigger`，且所有现有路由保持等价可用。
- 让 `ReplayProjectorAutoConfiguration`、`Es7HighLevelClientConfig`、`DataAgentInitRunner` 从 `domain` 回到 `app`，而不改变装配结果。
- 在 `domain` 建立 `IExecutionLedgerWriteRepository` 与 `IExecutionLedgerReadRepository` 两个仓储端口，让 `AgentExecutionRecorderImpl` 与 `ExecutionLedgerQueryServiceImpl` 摆脱对 DAO 的直接依赖。
- 在 `infrastructure` 落地 production repository adapter，并继续复用现有 ledger DAO / entity / mapper XML，保证 Phase 1 风险可控。
- 用测试和文档把本期边界锁死，避免实现过程中继续向 Phase 2 扩散。

**Non-Goals:**

- 不迁移 `ReactorConfig`，也不在本期重做配置读取方式。
- 不改写 `BaseAgent`、`LLM`、工具执行主循环或 SSE 运行时链路。
- 不改动 `SessionContextMemoryServiceImpl`、`ToolOutputWriterImpl / ToolOutputReaderImpl`、`WorkspaceImageGenerationServiceImpl` 的直接 DAO 依赖。
- 不移动 `IDialogue*Dao`、`IArtifactLedgerDao`、`DialogueRun`、`ArtifactRecord` 等持久化类型的物理位置。
- 不新增数据库表、列，也不切换 `mybatis/mapper/*ledger*.xml` 的 namespace / resultMap type。

## Decisions

### 1. 采用“先止血、再抽缝”的 Phase 1 收口策略

**Decision**

Phase 1 只处理已经确认且风险可控的三类边界问题：

- `trigger` 接管 HTTP 入口
- `app` 接管低风险 Spring 装配
- `infrastructure` 通过 repository adapter 吞掉 execution ledger 的 DAO 细节

其余与 runtime 主循环、tool-output、session-memory、workspace-image 相关的深耦合点统一延后到 Phase 2。

**Rationale**

- 这样可以先消除最明显的模块职责错位，同时避免把一个“边界收敛”需求膨胀成“全链路重写”。
- 先建立 seam，比直接搬空所有持久化实现更容易验证，也更容易回退。

**Alternatives considered**

- 一次性迁完 controller、config、repository、tool-output、session-memory：范围过大，几乎不可验证。
- 只迁 controller 和 config、不动 repository seam：`domain` 仍会被 DAO 反向绑死，Phase 1 收益不足。

### 2. trigger/controller 迁移坚持“契约等价迁移”，不顺手改 API 语义

**Decision**

`ReactorController` 与 `DataAgentController` 迁到 `trigger` 时，只改变包位置和归属模块，不改变现有 URL、HTTP method、参数对象、返回对象、SSE 语义和轻量委派方式。

**Rationale**

- 这两个 controller 当前本质上是 legacy 入口，Phase 1 的目标是纠正层级，而不是趁机改协议。
- 先通过路由集合与代表性委派测试锁住现状，能显著降低 API 回归风险。

**Alternatives considered**

- 迁移时同步整理 REST 风格或合并接口：会把边界治理和接口重设计混在一起，超出本期范围。

### 3. app/config 迁移只调整 Bean 所属层，不调整 Bean 拓扑

**Decision**

`ReplayProjectorAutoConfiguration`、`Es7HighLevelClientConfig`、`DataAgentInitRunner` 原样迁到 `app` 对应配置包，保持现有 `@Bean`、`@Configuration`、`@Component` 语义和参数绑定逻辑不变。

**Rationale**

- 这三类类型本身就是装配职责，迁出 `domain` 后能够立即提升层次清晰度。
- 如果迁移时再顺手优化 Bean 拓扑，会引入不必要的装配回归风险。

**Alternatives considered**

- 同时把 `ReactorConfig` 一起迁出：当前依赖面过大，收益与风险不成比例。

### 4. 用 domain port + infrastructure adapter 建立 execution ledger seam

**Decision**

在 `domain` 定义 `IExecutionLedgerWriteRepository` 与 `IExecutionLedgerReadRepository` 两个面向服务用例的仓储契约；`AgentExecutionRecorderImpl` 与 `ExecutionLedgerQueryServiceImpl` 只依赖这两个端口；`infrastructure` 提供具体 adapter，并在 adapter 内部继续复用当前 DAO 和实体。

**Rationale**

- 这一步能先把 `domain service -> DAO` 的硬耦合切断，让领域服务回到“面向端口编排业务”的基本状态。
- 通过 adapter 复用现有持久化 contract，可以避免本期同时触发 Mapper XML、实体包路径、测试夹具和其他服务的连锁修改。

**Alternatives considered**

- 直接把 DAO 和实体整体迁到 `infrastructure`：会牵连过多依赖点，属于 Phase 2 的持久化类型收口。
- 只在 service 里包一层 helper，而不定义仓储端口：不能真正恢复 DDD 边界。

### 5. 测试夹具同步经 repository seam 装配，保证 seam 不是空壳

**Decision**

`ExecutionLedgerFixtureFactory` 与相关测试必须跟随 service 构造器一起改造，让测试走 `IExecutionLedgerWriteRepository / IExecutionLedgerReadRepository`，并在下一步切到 production repository adapter。

**Rationale**

- 如果只改 production 代码，不改测试夹具，现有 ledger 回归要么直接编译失败，要么绕开新 seam，导致测试对边界治理没有约束力。
- 先用 seam 过渡，再切 production adapter，能把改造拆成更清晰的验证步骤。

**Alternatives considered**

- 暂时保留测试直接 new DAO 版 service：会让测试和生产代码边界分裂。

### 6. 用“显式延后列表”锁住 Phase 1 范围

**Decision**

在 proposal、design、tasks 和回归检查中明确列出本期延后项，包括 `ReactorConfig`、`SessionContextMemoryServiceImpl`、`ToolOutputWriterImpl / ToolOutputReaderImpl`、`WorkspaceImageGenerationServiceImpl`、ledger 持久化类型物理迁移以及 Mapper XML namespace 切换。

**Rationale**

- 这类收口类改造最容易在实现时出现“顺手再改一点”，最终把范围拖爆。
- 把延后项写成可检查的边界，有助于 reviewer 和实施人共同控范围。

**Alternatives considered**

- 只在口头上说“本期不做”：约束力太弱，无法沉淀成验收条件。

## Risks / Trade-offs

- [controller 迁移时漏掉 legacy 路由] → 用路由集合测试锁定 `/1/**` 与 `/data/**` 全量现有 endpoint，再删除旧类。
- [装配类迁移后 Bean 图发生漂移] → 通过 bean topology 测试和 profile 加载回归确认位置变化不影响装配结果。
- [repository seam 只改生产代码，测试没有真实覆盖 adapter] → 先改测试夹具走 port，再切到 production adapter，确保回归真的经过新边界。
- [实现阶段顺手移动 DAO / entity / mapper XML] → 在任务和人工检查里明确这属于越界动作，发现即回退到 Phase 1 范围内。
- [`ReactorConfig` 等高耦合类型被误纳入同批改造] → 在设计和任务中把它们标记为过渡态共享配置契约，本期只加保护，不做迁移。

## Migration Plan

1. 先新增控制器回归测试，锁定 `ReactorController` 与 `DataAgentController` 的全量路由和代表性委派行为。
2. 在 `trigger` 创建新 controller 并完成等价迁移，通过回归后删除 `domain` 中旧 controller。
3. 新增装配拓扑测试，再把 `ReplayProjectorAutoConfiguration`、`Es7HighLevelClientConfig`、`DataAgentInitRunner` 迁到 `app`，保持装配结果不变。
4. 新增边界测试，锁定 `AgentExecutionRecorderImpl` 与 `ExecutionLedgerQueryServiceImpl` 不再包含 `*Dao` 字段。
5. 在 `domain` 定义 execution-ledger read/write port，并先让 service 与测试夹具经 seam 装配。
6. 在 `infrastructure` 落地 production repository adapter，并让现有 ledger 回归改为覆盖 adapter。
7. 更新 `CLAUDE.md` 与 `ai-agent-station-study-domain/CLAUDE.md`，最后运行聚焦测试、模块编译回归和目录残留检查，锁住 Phase 1 边界。

**Rollback**

- 任一阶段只要出现 API 行为回归、Bean 装配异常、ledger 查询/写入语义漂移或范围越界，即回退到上一步已验证状态。
- Phase 1 不引入新的存储结构和外部依赖，因此回退以代码回滚和恢复旧包归属为主，不涉及数据迁移。

## Open Questions

- `ExecutionLedgerReadRepository` 的查询返回对象是否已经足够覆盖 Phase 2 的 session-memory 摘要消费，还是后续还需要补更细的查询端口？
- `ReactorConfig` 第二阶段究竟是演进为 `SettingsProvider` 风格 port，还是继续保留共享配置契约但下沉到 `app` 装配层？
- Phase 2 里 tool-output seam 和 ledger 持久化类型物理迁移应该拆成一个 change 还是两个 change，才能避免再次混成大范围改造？
