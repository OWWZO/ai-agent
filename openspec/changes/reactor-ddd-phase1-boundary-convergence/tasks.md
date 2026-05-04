## 1. 迁移 legacy HTTP 入口到 trigger

- [x] 1.1 新增 `ReactorHttpControllerTest`，锁定 `ReactorController` 与 `DataAgentController` 的全量 legacy 路由集合和代表性委派行为
- [x] 1.2 在 `ai-agent-station-study-trigger` 创建 `ReactorController` 与 `DataAgentController`，逐个迁入现有 `/1/**`、`/data/**` endpoint 并保持 URL、参数、返回契约不变
- [x] 1.3 通过控制器回归后删除 `ai-agent-station-study-domain` 中旧的 `ReactorController` 与 `DataAgentController`

## 2. 迁移低风险 Spring 装配到 app

- [x] 2.1 新增装配拓扑测试，锁定 `ReplayProjectorAutoConfiguration`、`Es7HighLevelClientConfig`、`DataAgentInitRunner` 不再从 `domain` 暴露
- [x] 2.2 将上述三类装配类型迁到 `ai-agent-station-study-app` 对应配置包，保持现有 Bean 定义、初始化时机和装配结果不变
- [x] 2.3 为 `ReactorConfig` 添加 Phase 1 保护说明和回归约束，确保其继续作为延后治理的过渡态共享配置契约

## 3. 在 domain 建立 execution-ledger 仓储端口

- [x] 3.1 新增 `IExecutionLedgerWriteRepository` 与 `IExecutionLedgerReadRepository`，只暴露 `AgentExecutionRecorderImpl` 与 `ExecutionLedgerQueryServiceImpl` 当前需要的领域级读写能力
- [x] 3.2 新增 `ExecutionLedgerBoundaryTest`，锁定两个 domain ledger service 不再声明任何 ledger `*Dao` 字段
- [x] 3.3 调整 `AgentExecutionRecorderImpl`、`ExecutionLedgerQueryServiceImpl` 和 `ExecutionLedgerFixtureFactory`，让生产代码与测试夹具都经 repository seam 装配

## 4. 在 infrastructure 落地 production repository adapter

- [x] 4.1 在 `ai-agent-station-study-infrastructure` 实现 `ExecutionLedgerWriteRepository` 与 `ExecutionLedgerReadRepository`，把原先 service 中的 DAO 读写逻辑下沉到 adapter
- [x] 4.2 保持 Phase 1 过渡策略不变，继续复用现有 ledger DAO、ledger entity 和 Mapper XML，不迁移持久化类型物理位置
- [x] 4.3 让 `AgentExecutionLedgerRepositoryTest`、`ExecutionLedgerQueryServiceTest`、`ConversationHistoryControllerTest` 等回归改为真实覆盖 production repository adapter

## 5. 锁定 Phase 1 边界并完成回归

- [x] 5.1 更新 `CLAUDE.md` 与 `ai-agent-station-study-domain/CLAUDE.md`，明确 trigger、domain、infrastructure、app 在 Reactor Phase 1 之后的职责边界
- [x] 5.2 运行聚焦回归，覆盖控制器迁移、装配拓扑、ledger boundary、repository adapter、conversation history 与 tool-output 相关测试
- [x] 5.3 运行模块编译与人工残留检查，确认 `domain` 中不再保留本期已迁出的 controller / 低风险 config，且 `ReactorConfig`、session-memory、tool-output、workspace-image 与 ledger 持久化类型迁移仍留待后续 change
