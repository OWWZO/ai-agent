# Research: Agent 领域边界最终收敛

## Decision 1: 保留 `case` 作为唯一应用编排入口，不把剩余旧接口继续留在 `domain`

- **Decision**: 将 `dispatch / execute / armory / task` 的主链路所有权固定在 `ai-agent-station-study-case`，旧 `domain/agent/service` 根接口与实现只允许作为短期迁移对象，最终不得保留为并行入口。
- **Rationale**: 当前仓库已经完成第一阶段切分，`AgentSessionStream` 和 `SseEmitterAgentSessionStream` 已建立应用层到触发层的会话流抽象。继续保留 `IAgentDispatchService`、`IExecuteStrategy`、`IArmoryService`、`ITaskService` 作为 `domain` 入口，只会让主链路在应用层与领域层之间长期双轨并行。
- **Alternatives considered**:
  - 保留旧接口并在内部委派到 `case`：短期看改动更少，但会让错误依赖长期合法化
  - 将 `case` 回撤到 `domain`：与已落地的分层边界和 Phase 1 收敛方向冲突

## Decision 2: 将 `reactor` 视为历史技术总包，而不是长期领域边界

- **Decision**: 把 `domain/agent/reactor` 拆成 runtime、ledger、memory、rag、role 五个明确子域，并把 `reactor` 视为待清退的历史承载目录。
- **Rationale**: 当前 `reactor` 同时混装了运行时内核、账本查询、SSE 工具、JDBC 查询执行器、配置对象和外部调用实现，已经不是一个可解释的单一子域。若继续允许它作为总包存在，任何新功能都能继续“合理地”落回其中。
- **Alternatives considered**:
  - 只改类名不改包边界：无法阻止 catch-all 总包继续扩张
  - 一次性全面重命名所有模型：风险过大，不符合棕地渐进收敛原则

## Decision 3: SSE 协议对象只保留在 `trigger` 适配层，领域层只保留协议无关输出契约

- **Decision**: 统一以 `AgentSessionStream`、`AgentSessionPrinter` 或领域打印契约承接过程输出；`SseEmitter`、心跳、关闭、异常收口与协议适配全部收口到 `trigger`。
- **Rationale**: 代码扫描显示 `domain` 仍在 `IAgentDispatchService`、多种 `IExecuteStrategy`、`DataAgentService`、`Nl2SqlService`、`GptProcessServiceImpl`、`SSEPrinter` 等路径直接依赖 `SseEmitter`。这会让领域服务同时承担传输协议生命周期，破坏 DDD 边界。
- **Alternatives considered**:
  - 保留 `SseEmitter` 到 `case` 层：仍然把 HTTP/SSE 具体协议带进核心执行链
  - 前端改协议，放弃 SSE：超出本次范围，也不能解决领域层职责污染

## Decision 4: HTTP / JDBC / MCP / Tool Runtime 技术执行器统一经由 port/repository seam 下沉

- **Decision**: 把 `OkHttpClient` 构建、URL 拼接、SSE 远程调用、`JdbcDataProvider`、连接池、catalog / dialect、文件与工具运行时客户端全部视为基础设施实现；`domain` 只保留端口、请求/结果模型与业务语义约束。
- **Rationale**: 扫描已确认 `domain` 仍存在 `new OkHttpClient`、`JdbcDataProvider` 注入、`OkHttpUtil`、`HttpUtils` 以及以数据服务名伪装的技术执行器。若不先抽出 port/repository seam，子域拆分后仍会把技术依赖带着一起迁移。
- **Alternatives considered**:
  - 仅把工具类移动到别的包，不引入显式端口：会隐藏依赖方向，后续容易再次直接 new 客户端
  - 维持技术执行器在 `domain`，只补文档说明：无法通过边界守卫稳定约束

## Decision 5: Spring 运行时查找必须继续清零，不允许为迁移便利重新引回 service locator

- **Decision**: 继续以 `SpringRuntimeBoundaryTest` 为硬边界，禁止 `domain` 中出现 `SpringContextHolder`、`applicationContext.getBean(...)` 和运行时装配型类。
- **Rationale**: 运行时 service locator 会让依赖关系变为隐式字符串耦合，特别是在 Agent 策略、handler、tool runtime 与回放投影场景中，会直接削弱 `case/domain/infrastructure/app` 的层次切分。
- **Alternatives considered**:
  - 临时恢复 `getBean` 加快迁移：短期便利，长期会重新制造隐藏依赖
  - 允许部分配置对象留在 `domain` 并调用运行时：除 `ReactorConfig` 这类已明确延后的共享配置契约外，其余都不应新增例外

## Decision 6: 对外 HTTP API 保持稳定，本期 contracts 聚焦“内部边界契约”而非新增用户接口

- **Decision**: 本次 `contracts/` 只记录层间边界契约和边界守卫契约，不定义新的终端用户 HTTP 接口。
- **Rationale**: 该特性的目标是收敛现有分层，不是新增产品能力。真正变化的是主链路内部的所有权与接口位置，例如应用层 dispatch contract、领域输出 contract、技术 port/repository seam 以及自动化边界守卫范围。
- **Alternatives considered**:
  - 不写 contracts：不利于后续 `tasks.md` 明确哪些 seam 需要实现或替换
  - 误写成 Controller API 变更：与规格“无新增终端用户工作流”的范围冲突

## Decision 7: 以现有边界测试为基础扩充守卫，而不是另起一套架构审计机制

- **Decision**: 复用并扩展现有 `AgentContextConvergenceBoundaryTest`、`SpringRuntimeBoundaryTest`、`ReactorPersistenceBoundaryTest` 以及相关 HTTP/controller 回归测试，作为本次验收主证据。
- **Rationale**: 仓库已经存在聚焦扫描式边界测试和主链路回归测试，继续沿用可以把“新边界”直接编进现有测试习惯，同时避免引入第二套审计工具链。
- **Alternatives considered**:
  - 新增独立的架构 lint 工具：引入成本高，本期不必要
  - 完全依赖人工 code review：难以防止后续回流
