## 1. 盘点并迁移 Reactor DAO / Mapper 物理归属

- [x] 1.1 盘点 `domain.agent.reactor.mapper` 下 ledger、tool-output、chat-model DAO / Mapper 与对应 XML namespace，形成 Phase 2A 迁移清单
- [x] 1.2 将上述 DAO / Mapper 物理迁入 `ai-agent-station-study-infrastructure` 的 `dao/` 包空间，并同步修正 Java import 与扫描引用
- [x] 1.3 调整 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/` 中对应 XML 的 namespace、resultMap type 和相关引用，确保不再绑定 `domain.reactor.mapper`

## 2. 为 chat-model 元数据服务补 repository seam

- [x] 2.1 在 `domain/adapter/repository/` 定义 chat-model 元数据所需的最小 repository port，覆盖模型去重、schema 去重、清理、保存和查询能力
- [x] 2.2 在 `infrastructure/adapter/repository/` 实现对应 adapter，并在内部复用迁移后的 DAO / Mapper 与现有 SQL 契约
- [x] 2.3 重构 `ChatModelInfoService`、`ChatModelSchemaService`，移除 `ServiceImpl<BaseMapper>` 继承，改为通过 repository port 提供现有领域行为

## 3. 收敛过渡态服务对 mapper 的依赖

- [x] 3.1 调整 `SessionContextMemoryServiceImpl`、`WorkspaceImageGenerationServiceImpl` 的 DAO 引用，使其仅依赖迁移后的 infrastructure DAO 类型，并记录后续 seam 落点
- [x] 3.2 调整 `ToolOutputReaderImpl`、`ToolOutputWriterImpl` 及相关 adapter，使 tool-output 持久化链路统一引用迁移后的 infrastructure DAO 包名
- [x] 3.3 为仍保留直接 DAO 依赖的过渡态服务补充范围说明和边界注释，确保 Phase 2A 不再新增 `domain.reactor.mapper` 依赖

## 4. 补齐边界与装配回归

- [x] 4.1 新增或调整边界检查，锁定 `domain` 内 Reactor `@Mapper`、`BaseMapper`、`domain.reactor.mapper` 命中数为 0
- [x] 4.2 新增或调整 MyBatis / Spring 装配回归，验证迁移后的 Reactor DAO 与 XML namespace 可以正常绑定
- [x] 4.3 新增或调整 chat-model、ledger、tool-output、session-memory、workspace-image 相关聚焦测试，确保回归真实经过迁移后的 DAO / repository 路径

## 5. 更新文档并完成验收

- [x] 5.1 更新 `CLAUDE.md`、`ai-agent-station-study-domain/CLAUDE.md`、`ai-agent-station-study-infrastructure/CLAUDE.md`，明确 Phase 2A 后的持久化边界
- [x] 5.2 运行聚焦编译和测试回归，覆盖 Mapper namespace、chat-model metadata、execution ledger、tool-output 与会话记忆核心链路
- [x] 5.3 运行人工残留检查，确认 Phase 2A 不包含 Spring runtime 解耦、remote port 迁移和 data engine 抽离等后续阶段内容
