# Research: 对话执行持久化账本

## Decision 1: 账本挂在真实执行链路，而不是挂在 SSE 展示投影链路

- **Decision**: 以 `/AutoAgent -> IAgentDispatchService -> React/PlanSolve ExecuteStrategy -> RootNode/Step1 -> AgentContext/BaseAgent/LLM` 作为账本写入主路径。
- **Rationale**: 当前 `MultiAgentServiceImpl` 与 `BaseAgentResponseHandler` 看到的是外层转发或展示结果，缺少 LLM 次数、tool dispatch 顺序和工具产物来源等执行事实。只有挂在真实执行路径上，才能准确得到 run、LLM、tool、artifact 关系。
- **Alternatives considered**:
  - 在 `SSEPrinter` 里拦截所有消息后反推账本：只能看到展示事件，看不到真实 LLM 调用和并发工具生命周期
  - 在 `BaseAgentResponseHandler` 里补写账本：这条链路只消费 `AgentResponse`，已经丢失了工具入参、toolCallId 映射和产物去重上下文

## Decision 2: 用 `AgentRunState + AgentExecutionRecorder` 贯穿账本上下文

- **Decision**: 在 `AgentContext` 中新增轻量运行态 `AgentRunState`，并新增领域接口 `AgentExecutionRecorder` 统一承接 start/finish/query 行为。
- **Rationale**: 账本需要跨 RootNode、LLM、BaseAgent、Summary 节点共享 `runId`、当前 agent 名称、LLM 序号和 `toolCallId -> toolInvocationId` 映射。把这些放到 `AgentContext` 能保持数据就近、避免在方法签名里层层透传，也不会把 SQL 细节暴露进执行策略。
- **Alternatives considered**:
  - 使用全局 `ThreadLocal` 保存账本上下文：PlanSolve 多线程工具执行会让上下文边界更脆弱，且不利于测试
  - 直接在每个节点手拼 DAO 调用：职责分散，后续难以统一 fail-open 和指标逻辑

## Decision 3: LLM 与 tool 都采用“前插后更”，tool 在主线程先预登记

- **Decision**: `LLM.ask()` / `askTool()` 前写 RUNNING 记录，完成后更新；`BaseAgent.executeTools()` 在主线程按 `toolCalls` 原始顺序先登记 tool invocation，再由工作线程并发执行并回写结果。
- **Rationale**: 这样即便执行中途异常，也能保留半程事实。tool 预登记还能稳定保存 `dispatch_index`，避免并发线程争抢全局序号。
- **Alternatives considered**:
  - 执行完再统一补写：一旦中途失败就丢链路，且文件产物难以稳定归属
  - 让每个工具线程自己插入记录：会把模型原始顺序和真实执行顺序混在一起，造成序号不稳定

## Decision 4: 文件产物继续以 `ToolArtifactRegistry` 为唯一事实源

- **Decision**: 输入文件从 `AgentRequest.sessionFiles` 直接登记到 run；输出文件在工具完成后，按 `ToolArtifactRegistry` 中同一 `toolCallId` 的绑定写入 `ai_agent_artifact`。
- **Rationale**: 当前仓库已经用 `ToolArtifactRegistry` 解决了工具产物来源绑定、内部文件过滤和可见文件去重问题，直接复用比重新解析工具结果字符串更稳。
- **Alternatives considered**:
  - 从 `tool_result` 文本里回扫文件链接：会丢失内部文件/可见文件语义，且文本格式不稳定
  - 让工具各自直写账本表：会把持久化职责散落到每个工具实现里，难以统一治理

## Decision 5: 本期只做内部查询服务，不暴露正式查询 API

- **Decision**: 先通过领域查询服务和 DAO 支持 run/tool/session 三类内部查询，不在 `trigger` 新增正式接口。
- **Rationale**: clarified 结果已经明确执行账本只面向内部排障与治理。本期先把事实写准、查准，避免提前扩展权限模型和产品化展示。
- **Alternatives considered**:
  - 直接新增后台查询 API：会把范围扩到鉴权、展示与前端消费，不符合本期收敛目标
  - 完全不做查询能力：难以验证账本价值，也不满足 spec 中的内部诊断诉求

## Decision 6: 账本失败采用 fail-open，并补日志与指标

- **Decision**: 账本写入异常只影响账本本身，不中断用户主流程；同时必须打错误日志，并暴露失败计数/成功率指标。
- **Rationale**: spec 已明确“持久化失败不能阻断主流程”。如果只有日志没有指标，长期静默失败很难被发现；如果失败即阻断，又违背用户要求。
- **Alternatives considered**:
  - 持久化失败直接抛异常终止请求：风险过高，影响用户任务完成
  - 只记日志不记指标：能排查单次故障，但无法识别持续性退化趋势
