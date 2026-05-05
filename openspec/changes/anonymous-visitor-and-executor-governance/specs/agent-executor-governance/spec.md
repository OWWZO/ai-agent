## ADDED Requirements

### Requirement: Agent mainline dispatch SHALL use Spring-managed named executors
真实对话 Controller、域内异步调用和工具/向量等主链路异步任务 MUST 运行在 Spring 托管的命名执行器上；系统 MUST NOT 再依赖默认公共线程池或散落在各处的 ad-hoc 线程池提交主链路工作。

#### Scenario: Controller dispatch uses the managed dispatch executor
- **WHEN** `/AutoAgent` 或等价的 Reactor 对话入口开始提交一次新的对话任务
- **THEN** 系统必须通过 Spring 托管的 `dispatch` 执行器提交该任务
- **THEN** 系统不得在 Controller 内部临时创建专用执行线程池

#### Scenario: Runtime async tasks declare an explicit managed executor
- **WHEN** LLM 调用、向量召回或配置加载逻辑发起异步任务
- **THEN** 系统必须显式指定 `llm` 或 `tool` 等受控执行器
- **THEN** 系统不得回退到未指定执行器的 `CompletableFuture` 默认公共线程池

### Requirement: SSE heartbeats SHALL be scheduled by the managed heartbeat scheduler
SSE 心跳 MUST 通过 Spring 托管的 `heartbeat` 调度器统一启动、停止和观测，避免控制器私自创建心跳线程池导致生命周期分散。

#### Scenario: Heartbeat scheduling uses the shared scheduler
- **WHEN** 对话 SSE 连接建立后需要定期发送心跳
- **THEN** 系统必须通过 Spring 托管的 `heartbeat` 调度器安排固定频率任务
- **THEN** `AiAgentController` 与 `ReactorController` 必须复用同一类调度治理方式

#### Scenario: Heartbeat stops when the client disconnects
- **WHEN** SSE 客户端断开连接或心跳发送触发已知断连异常
- **THEN** 系统必须停止对应心跳任务
- **THEN** 系统不得继续让后台线程无限发送无效心跳

### Requirement: Executor saturation SHALL fail fast with an observable busy result
当 `dispatch / llm / tool / heartbeat` 等受控执行器无法接收新任务时，系统 MUST 返回明确的受控失败，并保留日志或错误结果用于观测；系统 MUST NOT 静默吞掉被拒绝的任务。

#### Scenario: Dispatch rejection returns a controlled busy failure
- **WHEN** `dispatch` 执行器因队列或线程池饱和而拒绝新的对话任务
- **THEN** 系统必须把本次请求收口为明确的“系统繁忙，请稍后重试”类失败
- **THEN** 系统必须记录可观测的拒绝日志，而不是假装任务已提交

#### Scenario: Runtime async rejection does not silently drop downstream work
- **WHEN** `llm` 或 `tool` 执行器拒绝某个域内异步任务
- **THEN** 系统必须向上抛出受控异常或失败结果
- **THEN** 系统不得保持调用链静默继续并造成状态不一致

### Requirement: Mainline async governance SHALL prohibit `ThreadUtil.execute(...)` and unmanaged common-pool usage
为了保证主链路异步行为可审计、可隔离、可回归，系统 MUST 移除真实对话主路径中的 `ThreadUtil.execute(...)` 调用和未显式指定执行器的 `CompletableFuture.supplyAsync(...)` 用法，并通过回归测试持续锁定该边界。

#### Scenario: Mainline async entrypoints no longer use `ThreadUtil.execute(...)`
- **WHEN** 对主链路异步入口执行结构性治理回归测试
- **THEN** 测试结果中不得再出现 `ThreadUtil.execute(...)` 的直接调用点
- **THEN** 新增异步入口必须改为显式使用受控执行器

#### Scenario: Mainline async entrypoints no longer use default common pool
- **WHEN** 对主链路异步入口执行结构性治理回归测试
- **THEN** 测试结果中不得再出现未显式指定执行器的 `CompletableFuture.supplyAsync(...)` 调用点
- **THEN** 所有保留的异步任务都必须能映射到受控执行器类别
