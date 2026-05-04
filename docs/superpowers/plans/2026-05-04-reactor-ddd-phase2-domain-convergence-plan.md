# Reactor DDD Phase 2 Domain Convergence Plan

## 目标

将 `ai-agent-station-study-domain` 从当前的 Phase 1 过渡态继续收敛为更标准的 DDD / 六边形架构领域层。

本阶段的总目标是：

- `domain` 只保留领域模型、领域服务、仓储/端口接口
- `infrastructure` 承接 DAO、外部 HTTP / RPC、JDBC / SQL 执行技术细节
- `app` 承接 Spring Bean 装配与运行时注册
- 必要时引入 `case` 层承接跨能力应用编排

## 当前问题基线

### 1. 持久化接口仍在 domain

当前 `domain` 内仍保留多组 MyBatis DAO / Mapper，例如：

- `org.wwz.ai.domain.agent.reactor.mapper.IArtifactLedgerDao`
- `org.wwz.ai.domain.agent.reactor.mapper.ChatModelInfoMapper`
- `org.wwz.ai.domain.agent.reactor.mapper.IToolInvocationLedgerDao`
- `org.wwz.ai.domain.agent.reactor.mapper.IToolOutput*Dao`

这类类型属于典型基础设施职责，不应继续停留在领域层。

### 2. Spring 运行时耦合仍在 domain

当前 `domain` 内仍存在以下运行时耦合：

- `SpringContextHolder`
- `ApplicationContext.getBean(...)`
- `@Configuration` / `@Bean` 风格的装配语义
- `@Component` + `@ConfigurationProperties` 型配置对象

这会让领域服务依赖 Spring 容器细节，而不是只依赖显式接口。

### 3. 外部调用实现仍在 domain

当前 `domain` 内仍存在直接的技术调用实现：

- OkHttp / WebClient 外部请求
- Qdrant 访问
- 文件服务调用
- 多模态 / DeepSearch / CodeInterpreter / DataAnalysis 外部工具调用
- MCP runtime 客户端构建

这些都应通过 Port 接口下沉到 `infrastructure`。

### 4. JDBC / SQL 技术内核仍在 domain

当前 `domain` 内仍保留：

- `reactor/data/jdbc/**`
- `reactor/data/sql/**`
- `reactor/data/provider/jdbc/**`
- `JdbcUtils` / `HttpUtils` / `ESUtil`

其中大量内容本质上是技术执行器，不是领域概念。

### 5. 部分 service 更像应用编排或技术执行器

例如：

- `MultiAgentServiceImpl`
- `QdrantService`
- `SessionContextMemoryServiceImpl`
- `WorkspaceImageGenerationServiceImpl`
- `tooloutput` 相关读写服务

这些服务并不全是“纯领域服务”，需要在后续阶段进一步拆分职责。

## 分阶段计划

## Phase 2A: 持久化边界迁移

### 目标

先把 `domain` 中的 DAO / Mapper 物理迁出，为后续收敛建立清晰的 Repository seam。

### 范围

- `org.wwz.ai.domain.agent.reactor.mapper.*`
- 直接依赖上述 mapper 的 service / fixture / test

### 实施策略

- 在 `domain/adapter/repository/` 中补齐缺失的 repository port
- 在 `infrastructure/adapter/repository/` 中实现这些 port
- 将 DAO 接口迁移到 `infrastructure/dao/`
- 如短期内 PO / entity 强耦合，允许过渡复用现有 entity，但 DAO 物理位置必须先迁出

### 验收标准

- `domain` 内 `@Mapper`、`BaseMapper` 命中数降为 0
- 领域服务不再直接声明或注入任何 `*Dao` / `*Mapper`
- 相关回归测试真实经过 `infrastructure` repository adapter

## Phase 2B: Spring 运行时解耦

### 目标

消除 `domain` 对 Spring 容器的 Service Locator 依赖。

### 范围

- `SpringContextHolder`
- `ApplicationContext.getBean(...)`
- `AgentHandlerConfig`
- `AbstractArmorySupport`
- `AbstractExecuteSupport`
- `FlowAgentExecuteStrategy`
- `LLM` 及若干 agent / tool 中的静态取 Bean 行为

### 实施策略

- 优先改为构造注入或字段注入
- 对动态能力选择场景，引入显式注册表 / 工厂接口，而不是运行时按名称取 Bean
- 将 `@Configuration` / `@Bean` 组装类型迁到 `app`
- `ReactorConfig` 允许继续保留在 `domain` 作为过渡配置契约，但要逐步去掉静态全局访问

### 验收标准

- `domain` 内不再出现 `ApplicationContext.getBean(...)`
- `domain` 内不再依赖 `SpringContextHolder`
- Bean 组装职责收敛到 `app`

## Phase 2C: 外部调用端口化

### 目标

将所有 HTTP / SSE / WebClient / OkHttp 技术调用从 `domain` 迁出。

### 范围

- `QdrantService`
- `MultiAgentServiceImpl`
- `agent/tool/common/*`
- `agent/tool/mcp/runtime/*`
- 其他直接构建 OkHttp / WebClient 的实现

### 实施策略

- 在 `domain/adapter/port/` 定义端口，例如：
  - `IQdrantPort`
  - `IFileToolPort`
  - `ICodeInterpreterPort`
  - `IDataAnalysisPort`
  - `IDeepSearchPort`
  - `IMultiModalPort`
  - `IMcpRuntimePort`
- 在 `infrastructure/adapter/port/` 实现这些端口
- `domain` 保留业务协议、输入输出语义和结果解释，不再处理 HTTP 细节

### 验收标准

- `domain` 内不再直接创建 `OkHttpClient` / `WebClient`
- 外部请求头、超时、URL 拼接、状态码处理均位于 `infrastructure`

## Phase 2D: Data Engine 技术内核迁移

### 目标

将 JDBC / SQL / catalog / dialect 等技术执行器迁出 `domain`。

### 范围

- `reactor/data/jdbc/**`
- `reactor/data/sql/**`
- `reactor/data/provider/jdbc/**`
- `JdbcUtils` / `HttpUtils` / `ESUtil`

### 实施策略

- 保留真正的领域查询模型和值对象
- 将连接池、catalog loader、dialect factory、provider 执行器迁到 `infrastructure`
- 通过 `domain` 端口暴露“查询能力”而不是暴露底层 JDBC 细节

### 验收标准

- `domain` 内不再承载连接池、数据源、catalog / dialect 技术实现
- `domain` 只保留查询语义模型、约束和端口接口

## Phase 2E: 运行时与应用编排收敛

### 目标

对剩余过渡项做最后一轮职责明确化，避免 `domain` 继续承担应用编排和技术执行。

### 范围

- `tooloutput` 读写
- `session-memory`
- `workspace-image`
- 部分 handler / tool orchestration / replay projector 相关能力

### 实施策略

- 区分“领域能力”与“应用编排”
- 如涉及多组件协同、事务边界或运行时协议拼装，优先考虑 `case` 层
- 持久化与外部调用继续通过 repository / port seam 收敛

### 验收标准

- `domain` 中的 service 更接近业务语义，而不是技术执行器
- 延后项得到明确归属，不再长期挂在过渡态

## 推荐拆分为独立 change

建议拆成以下 4 到 5 个 OpenSpec change，而不是一次性大改：

1. `reactor-ddd-phase2-persistence-extraction`
2. `reactor-ddd-phase2-spring-runtime-decoupling`
3. `reactor-ddd-phase2-remote-port-adapters`
4. `reactor-ddd-phase2-data-engine-extraction`
5. `reactor-ddd-phase2-runtime-cleanup`

## 执行原则

- 每个阶段只解决一类边界问题，禁止顺手扩 scope
- 每个阶段必须补边界回归测试
- 每个阶段必须更新 `CLAUDE.md` 与模块文档
- 每个阶段完成后都执行聚焦测试和模块编译
- 未通过边界回归，不进入下一阶段

## 推荐起步

优先启动 `Phase 2A: 持久化边界迁移`。

原因：

- 风险最可控
- 收益最高
- 最符合 DDD 第一性原则：`domain` 定义接口，`infrastructure` 持有 DAO 和持久化实现
- 能为后续 Spring 解耦、远程调用端口化和 Data Engine 迁移打下稳定基础

## 建议的第一批回归检查

- `domain` 内 `@Mapper` / `BaseMapper` 检查
- 领域服务字段中是否仍声明 DAO
- repository adapter 是否真实参与执行链路
- conversation history / ledger / tool-output 相关回归是否保持通过
- `mvn clean compile`

