## Context

Phase 1 已经把 legacy controller、低风险 Spring 装配和 execution ledger 的第一条 repository seam 从 `domain` 中切出来，但 Reactor 持久化边界仍停留在明显的过渡态：

- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/` 仍保留整组 ledger、tool-output、chat-model DAO / Mapper。
- `ai-agent-station-study-app/src/main/resources/mybatis/mapper/` 中多份 XML 的 namespace 仍直接绑定 `org.wwz.ai.domain.agent.reactor.mapper.*`。
- `ChatModelInfoService`、`ChatModelSchemaService` 仍直接继承 `ServiceImpl<...Mapper, ...>`，把 MyBatis-Plus 的 `BaseMapper` 和持久化模型细节暴露在 domain service 中。
- `SessionContextMemoryServiceImpl`、`WorkspaceImageGenerationServiceImpl`、`ToolOutputReaderImpl / ToolOutputWriterImpl` 仍直接依赖 reactor mapper，其中前两者还位于 `domain`。

这意味着当前代码虽然“部分 service 已经依赖 repository seam”，但 `domain` 仍然持有 DAO 定义和 BaseMapper 继承关系，尚未满足“domain 只保留领域模型、领域服务、repository/port 接口”的目标。Phase 2A 的核心任务不是一次性解决所有耦合，而是先把 DAO / Mapper 的物理归属和直接依赖方式纠正到位。

当前约束也比较明确：

- 本期不处理 `ReactorConfig`、`ApplicationContext.getBean(...)`、MCP runtime、OkHttp / WebClient 等 Spring/runtime/remote 端口化问题。
- 本期不迁移 `reactor/data/jdbc/**`、`reactor/data/sql/**`、`JdbcUtils` 等 data engine 技术内核。
- `SessionContextMemoryServiceImpl`、`WorkspaceImageGenerationServiceImpl`、`ToolOutputReaderImpl / ToolOutputWriterImpl` 允许暂时继续做技术执行器，但它们对 DAO 的引用必须以 infrastructure 中的类型为准，不能再保留 `domain.reactor.mapper`。
- 现有数据库表结构与 SQL 语义必须保持兼容，所有变化都应停留在 Java 类型归属、repository seam 和 Mapper XML namespace 层面。

## Goals / Non-Goals

**Goals:**

- 让 `domain` 内 Reactor 持久化接口的物理归属清零：`@Mapper`、`BaseMapper` 和 `org.wwz.ai.domain.agent.reactor.mapper.*` 在 domain 中都不再存在。
- 将现存 ledger、tool-output、chat-model DAO / Mapper 统一迁入 `infrastructure/dao/` 包空间，并同步修正 Mapper XML namespace 和装配。
- 为仍直接依赖 DAO 的 domain service 补齐最小 repository seam，至少覆盖 chat-model 元数据服务，并为 session-memory / workspace-image 的后续收敛预留一致的接口落点。
- 让 `ChatModelInfoService`、`ChatModelSchemaService` 从 `ServiceImpl<BaseMapper>` 风格收敛为依赖 repository port 的领域服务实现。
- 用边界回归、Mapper 装配回归和模块文档，明确 Phase 2A 完成后哪些服务仍是过渡态、哪些边界已经收敛完成。

**Non-Goals:**

- 不处理 Spring 运行时 Service Locator 解耦，不迁移 `ReactorConfig`，不重构 Agent / Tool 主执行循环。
- 不做外部 HTTP / SSE / MCP / Qdrant 等端口化迁移。
- 不迁移 data engine 技术内核，不处理 JDBC provider、dialect、catalog loader 的物理归属。
- 不在本期内彻底消除 `SessionContextMemoryServiceImpl`、`WorkspaceImageGenerationServiceImpl`、`ToolOutputReaderImpl / ToolOutputWriterImpl` 的技术执行器属性；只纠正其 DAO 依赖边界。
- 不改变数据库 schema、SQL 语义、Controller URL、SSE 事件协议或前端消费数据结构。

## Decisions

### 1. 采用“DAO 先物理迁出，service 再按能力补 seam”的分层收敛顺序

**Decision**

Phase 2A 先完成 Reactor DAO / Mapper 的物理迁移和 namespace 收敛，再对当前直接依赖这些 DAO 的 domain service 按能力分批补 repository port。优先级从 chat-model 元数据服务开始，再覆盖仍位于 domain 的 session-memory 和 workspace-image 相关 seam 定位。

**Rationale**

- 物理迁出 DAO 是最清晰、最可验证的边界动作，能立即让 `domain` 摆脱最明显的基础设施类型归属错误。
- 先统一 DAO 归属，再分能力补 seam，能避免“有些 service 走 repository、有些 service 还在引用旧 mapper 包”的混乱状态。

**Alternatives considered**

- 先逐个 service 抽 repository，再最后迁 DAO：迁移周期内会长期同时维护两套包名和 namespace，认知成本更高。
- 一次性把所有 service 都重做成纯领域服务：范围过大，会把 Phase 2A 扩成 Phase 2C/2E 的组合改造。

### 2. DAO 迁移目标统一放入 infrastructure/dao，Repository 实现统一放入 infrastructure/adapter/repository

**Decision**

所有 `org.wwz.ai.domain.agent.reactor.mapper.*` 类型统一迁到 `org.wwz.ai.infrastructure.dao` 或其 reactor 子包；领域仓储契约仍定义在 `domain/adapter/repository/`；production 实现统一放在 `infrastructure/adapter/repository/`。

**Rationale**

- 这符合仓库现有 DDD 约定：DAO 在 infrastructure，repository port 在 domain，实现也在 infrastructure。
- 统一命名和位置后，后续 code search、Mapper XML namespace 管理和模块文档都更稳定。

**Alternatives considered**

- 将 Reactor DAO 放到 infrastructure/adapter/repository 内部私有包：会混淆 DAO 与 repository adapter 两类职责。
- 保持 DAO 在 domain，仅用注释声明“过渡态”：这已经被证明会持续诱导错误依赖。

### 3. MyBatis XML namespace 采用“与新 DAO FQCN 一一对应”的迁移策略

**Decision**

`ai-agent-station-study-app/src/main/resources/mybatis/mapper/` 下所有引用 Reactor DAO 的 XML 均同步改到新的 infrastructure DAO 全限定名，且不保留兼容别名 namespace。

**Rationale**

- namespace 与 DAO FQCN 一一对应是最少惊奇原则，便于后续维护和工具检查。
- 保留旧 namespace 别名会让“domain 不再持有 DAO”变成名义收敛、实际混用。

**Alternatives considered**

- 通过额外桥接接口暂时兼容旧 namespace：会增加无业务价值的中间层，并拖慢后续清理。

### 4. Chat-model 元数据服务不再继承 MyBatis-Plus ServiceImpl，改为面向 repository port

**Decision**

`ChatModelInfoService` 和 `ChatModelSchemaService` 从 `ServiceImpl<Mapper, Entity>` 继承模型退出，改为普通 `@Service` 领域服务，只依赖 `domain` 中定义的 chat-model repository port。若确有通用 CRUD 需求，由 infrastructure repository adapter 在内部决定是否复用 MyBatis-Plus。

**Rationale**

- `ServiceImpl` 直接把 `BaseMapper`、`lambdaQuery()`、`saveBatch()` 等持久化机制暴露给 domain service，违背本期目标。
- chat-model 元数据是当前仍明确直接耦合 `BaseMapper` 的核心区域，拿它作为 Phase 2A 的 seam 样板最合适。

**Alternatives considered**

- 仅把 Mapper 包迁走，保留 `ServiceImpl` 继承：虽然包归属纠正了，但 domain 依然直接依赖持久化框架。
- 把 chat-model 逻辑整个移到 infrastructure：会把领域语义和技术实现混回一起，不符合 DDD。

### 5. 对 session-memory / workspace-image / tool-output 采取“约束新增依赖 + 预留 seam”的过渡策略

**Decision**

`SessionContextMemoryServiceImpl`、`WorkspaceImageGenerationServiceImpl`、`ToolOutputReaderImpl`、`ToolOutputWriterImpl` 在 Phase 2A 不要求彻底抽成 repository port，但必须完成两件事：

- 其 DAO 依赖全部指向迁移后的 infrastructure DAO 类型；
- 设计与任务中明确后续要抽取的 repository seam 落点，避免继续增加对 DAO 的直接散射依赖。

**Rationale**

- 这些服务横跨 execution ledger、tool-output 和 workspace 能力，若本期全部彻底抽象，范围会溢出到 Phase 2E。
- 先锁住“不能继续依赖 domain mapper”，再为下一阶段保留统一 seam，能兼顾节奏和边界质量。

**Alternatives considered**

- 保持现状不动：会让新旧 mapper 包并存风险持续存在。
- 本期全部抽 repository：过重，会影响 apply 阶段执行稳定性。

### 6. 测试以“边界检查 + 真实 adapter 路径 + namespace 装配”三层验证

**Decision**

Phase 2A 验证分成三层：

- 边界检查：`domain` 内 `@Mapper`、`BaseMapper`、`domain.reactor.mapper` 引用数为 0；
- 真实路径检查：chat-model、ledger、tool-output、session-memory 相关测试覆盖 production repository adapter / 新 DAO 包；
- 装配检查：MyBatis `mapperLocations` 与 XML namespace 在 Spring 上下文中能正常绑定。

**Rationale**

- 只做编译通过无法证明边界真的收敛。
- 只做行为测试又可能漏掉“domain 仍悄悄持有 mapper 类型”的结构性问题。

**Alternatives considered**

- 仅做 `rg` 检查：无法证明运行链路正常。
- 仅做集成测试：难以及时发现边界回退。

## Risks / Trade-offs

- [批量迁移 DAO 包名后 XML namespace 漏改] → 用 `rg` 扫描旧 namespace 残留，并增加至少一个 Spring/MyBatis 装配测试验证关键 Mapper 可加载。
- [chat-model service 退出 `ServiceImpl` 后行为回归] → 先补针对 `listDistinctModels`、`cleanModelMetadata`、`saveModelSchema`、`previewData` 等核心路径的聚焦测试，再做 repository seam 替换。
- [session-memory / workspace-image 仍直接用 DAO 导致 reviewer 误判为“没收敛”] → 在文档和任务中明确它们是 Phase 2A 过渡项，验收点是“DAO 已迁出 domain 且不再新增 domain mapper 依赖”。
- [tool-output / ledger adapter 与新 DAO 包名不一致造成测试夹具分裂] → 统一让 fixture、repository adapter 和 service 都只引用 infrastructure DAO。
- [实现过程顺手把 Spring runtime 或 remote port 问题带进来] → 用显式非目标清单和任务边界约束，发现范围扩散即暂停。

## Migration Plan

1. 先盘点 `domain.reactor.mapper` 下的 DAO / Mapper 与对应 XML namespace，按 ledger、tool-output、chat-model 三类建立迁移清单。
2. 将这些 DAO / Mapper 物理迁入 `infrastructure/dao/`，同步修正 import、Mapper XML namespace 和必要的扫描配置。
3. 为 chat-model 能力定义 repository port，并让 `ChatModelInfoService`、`ChatModelSchemaService` 退出 `ServiceImpl` 继承，改经 repository seam 装配。
4. 调整 execution-ledger、tool-output、session-memory、workspace-image 相关实现与测试，统一引用新的 infrastructure DAO 包名，并明确过渡态 seam 位置。
5. 更新模块文档与边界检查，执行聚焦测试、模块编译和旧包残留扫描，确认 `domain` 内不再保留 Reactor DAO / Mapper。

**Rollback**

- 任一阶段若出现 MyBatis namespace 绑定失败、chat-model 元数据初始化回归、session-memory 历史拼装异常或 workspace-image 历史查询异常，即回退到上一步已验证的包结构和 repository 接入状态。
- 本期不涉及数据库 schema 迁移，因此回退主要依赖代码回滚和 XML namespace 恢复，不涉及数据修复。

## Open Questions

- `ChatModelInfoService` / `ChatModelSchemaService` 最终是否需要独立拆成 `IChatModelMetadataRepository` 与 `IChatModelSchemaRepository` 两个 port，还是保留一个聚合型 metadata repository 更符合当前业务边界？
- `SessionContextMemoryServiceImpl` 在 Phase 2B/2E 是继续依赖 `ExecutionLedgerQueryService` 聚合查询，还是需要单独抽取面向历史记忆的 read repository？
- `ToolOutputReaderImpl / ToolOutputWriterImpl` 的 DAO 收敛是否应该在下一阶段直接合并到统一 `ToolOutputPersistenceRepository`，还是继续保留按 tool-output 类型拆分的内部 adapter？

