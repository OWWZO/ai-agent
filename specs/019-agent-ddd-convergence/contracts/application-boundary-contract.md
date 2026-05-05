# Contract: Application Boundary

## Goal

定义本次收敛后 `trigger -> case -> domain` 的主链路边界，以及哪些职责必须从旧 `domain/agent/service` 根接口迁出。

## 1. Trigger to Case

### Required Shape

- `trigger` 入口只能依赖 `ai-agent-station-study-case` 暴露的应用服务接口
- 流式会话输出必须通过协议无关抽象传入 `case`
- `trigger` 可以持有 `SseEmitter`、心跳、超时和错误收口逻辑

### Forbidden Shape

- `trigger` 直接依赖旧 `org.wwz.ai.domain.agent.service.IAgentDispatchService`
- `trigger` 把 `SseEmitter` 继续穿透给 `domain`
- `trigger` 在 controller / job 内自行拼业务编排

## 2. Case to Domain

### Required Shape

- `case` 负责 dispatch、execute、armory、task 的应用编排
- `case` 选择执行策略、组织调用顺序、桥接会话输出抽象
- `domain` 只暴露领域模型、领域服务、子域能力和 port/repository contract

### Forbidden Shape

- `case` 直接创建 `OkHttpClient`、`JdbcDataProvider`、文件上传客户端或其他技术执行器
- `case` 通过旧 `domain/agent/service` 兼容树绕回主路径

## 3. Domain Ownership

### Required Shape

- `runtime / ledger / memory / rag / role` 是 `domain` 内唯一允许的主子域归属
- `domain` 可声明：
  - 领域模型
  - 领域服务
  - repository seam
  - port seam
  - 协议无关输出契约

### Forbidden Shape

- `domain` 直接持有 `SseEmitter`
- `domain` 直接 `new OkHttpClient`
- `domain` 直接注入 `JdbcDataProvider`
- `domain` 直接调用 `applicationContext.getBean(...)` 或 `SpringContextHolder`
- `domain` 继续以 `service` 或 `reactor` 总树承载应用编排与技术执行器

## 4. Acceptance Signals

满足以下信号时，应用边界视为成立：

1. 入口控制器与任务入口只依赖 `case` 暴露接口
2. 旧 `domain.agent.service` 根接口不再出现在主链路依赖中
3. `domain` 中不存在协议对象和技术执行器直接依赖
4. `case` 成为唯一应用编排入口

## 5. Current Seams After 019

- Trigger 入口当前稳定依赖：
  - `IAgentDispatchService`
  - `IArmoryService`
  - `IFixRoleQueryService`
  - `IRagApplicationService`
  - `IGptQueryApplicationService`
  - `IDataAgentApplicationService`
- `SseEmitterAgentSessionStream` 是 Trigger 向 Case 传递协议无关流的唯一主适配器
- legacy GPT 查询与 dataagent 主链路已先经由 `case.query` / `case.dataquery` 收口，再桥接到仍待删除的领域过渡服务
- bridge 删除条件：
  - 当 legacy GPT 查询入口不再依赖 `IGptProcessService` / `IMultiAgentService` 时删除对应桥接
  - 当 dataagent 主链路拆分为稳定领域语义接口后删除 `DataAgentService` / `Nl2SqlService` 过渡桥
