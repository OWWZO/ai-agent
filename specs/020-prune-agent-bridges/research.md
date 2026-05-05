# Research: Agent Legacy Bridge 实质删除与子域再收敛

## Decision 1: 把 `case.query` / `case.dataquery` 从“入口收口”升级为真正稳定 seam

- **Decision**: 不再满足于控制器只依赖 case；本轮要求 `case.query` 与 `case.dataquery` 底层也不再直接委派 `IGptProcessService`、`DataAgentService`、`Nl2SqlService` 这类 bridge，而是改为依赖新的稳定领域语义接口。
- **Rationale**: 当前 case 层虽然挡住了 trigger 对旧 bridge 的直接依赖，但主链路语义并没有真正切走，仍然只是多包了一层应用服务。
- **Alternatives considered**:
  - 保留现状，仅证明 trigger 不再直连旧 bridge：无法完成“实质删除”
  - 直接把所有 query/dataagent 逻辑塞回 case：会让领域语义上浮，破坏 DDD 边界

## Decision 2: 将 legacy bridge 与 legacy stable contract 分开治理

- **Decision**: 本轮把遗留内容明确分为三类：
  - 必须删除的 bridge
  - 允许延期但不得扩张的稳定历史契约
  - 已完成收敛的稳定子域归属
- **Rationale**: `reactor` / `service` 当前残留内容并不等价。有些是纯桥接壳，应该删除；有些是仍被多个模块稳定依赖的请求模型或配置契约，短期内需要被重新归类，而不是粗暴判定为“一律不能存在”。
- **Alternatives considered**:
  - 一刀切要求旧目录全部清空：风险高，容易把稳定契约与桥接壳混在一起
  - 保持上一轮“只要有注释就能保留”：无法推动真正收敛

## Decision 3: GPT query / multi-agent 先删过渡接口，再稳定请求与响应契约

- **Decision**: 优先删除 `IGptProcessService`、`IMultiAgentService`、`GptProcessServiceImpl`、`MultiAgentServiceImpl` 这一组 bridge，并同步确定 `GptQueryReq`、`AgentResponse`、`GptProcessResult`、`EventResult` 等契约的稳定归属。
- **Rationale**: 这一组 bridge 的生产依赖面已经较小，主要集中在 case seam 与少量测试。适合作为本轮“桥接删除”的第一批收口对象。
- **Alternatives considered**:
  - 只改接口名不改调用图：不能消除 bridge
  - 先迁模型、后迁接口：会让入口层继续依赖旧桥，主链路仍不干净

## Decision 4: dataagent 要按能力拆分，而不是继续围绕 `DataAgentService` 大类续命

- **Decision**: dataagent 相关能力至少拆分为查询编排、NL2SQL 执行、schema recall、模型元数据、向量/ES 同步、配置初始化这几类稳定职责，不再以 `DataAgentService` / `Nl2SqlService` 两个大类桥接所有语义。
- **Rationale**: 当前 `DataAgentService` 同时承担 query request 组装、RAG 回忆、stream 输出收口与测试查询；`Nl2SqlService` 同时承担远端调用、SQL 解析、结果整形与 stream listener。继续保留这两个类会让 dataagent 领域很难获得稳定边界。
- **Alternatives considered**:
  - 直接把当前两个类整体搬包：目录变了，语义没变
  - 只在 case 层继续包一层 facade：仍无法删除旧桥

## Decision 5: `reactor/model/**` 与 `reactor/config/data/**` 要按“稳定契约”重新分类

- **Decision**: 请求/响应、event、多智能体结果、image generation 模型和 dataagent 配置必须被明确归入稳定语义边界；若短期不能物理迁移，必须被标记为“历史包名下的稳定契约”，并建立禁止扩张守卫。
- **Rationale**: 目前大量 case/trigger/app/infrastructure 代码仍依赖这些包。它们已经不是单纯的 bridge，而是事实上的共享契约。如果不先明确其身份，后续无法判断哪些文件该删、哪些该迁、哪些该暂存。
- **Alternatives considered**:
  - 不处理这些模型，只删 service bridge：会留下“入口干净但契约全挂旧包”的半收敛状态
  - 全量一次性迁移所有模型：范围和风险都过大

## Decision 6: `service/execute/**` 与 `service/armory/**` 按运行时语义继续收敛

- **Decision**: 对执行步骤节点、armory 工厂与加载策略按真实依赖面继续收敛：
  - 真正仍属于 runtime/armory 语义的，迁入稳定 runtime 或相关子域
  - 已无真实依赖方的，直接删除
- **Rationale**: 当前这些目录虽然不全是 bridge，但仍是旧 `service` 总树的重要残留。如果完全不碰，旧目录仍会长期承担“默认放杂物”的角色。
- **Alternatives considered**:
  - 全部保留到下一轮：会让 `020` 无法兑现“子域模型再收敛”
  - 不加区分全部删除：容易误删仍被主链路依赖的节点

## Decision 7: 边界守卫要从“旧接口零引用”升级为“旧目录分类治理”

- **Decision**: 守卫不仅要检查旧 bridge 零依赖，还要检查：
  - 必须删除目录是否已清空
  - 允许延期目录是否出现新增文件或新增主逻辑
  - case/trigger/app/infrastructure 是否继续引用不该存在的旧包
- **Rationale**: 仅靠零引用旧根接口已不足以证明这轮完成。当前大量问题已转移为旧模型、旧配置、旧服务组仍承担稳定语义。
- **Alternatives considered**:
  - 继续沿用 `019` 的守卫集合：覆盖不足
  - 新增独立 lint 工具：引入成本高，本期没必要
