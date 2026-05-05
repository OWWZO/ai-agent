## 1. 建立匿名访客身份与会话归属应用层

- [x] 1.1 新增匿名访客身份模型、`AnonymousVisitorApplicationService`、`ConversationSessionOwnershipApplicationService` 和 `VisitorRequestContext`，统一处理 Cookie 解析、访客建档、会话首次绑定与跨访客拒绝
- [x] 1.2 新增 `VisitorIdentityFilter` 并注册到 trigger 层过滤链，接管 `ai_agent_visitor_token` Cookie 解析、换新和请求上下文绑定
- [x] 1.3 补充 `ConversationSessionOwnershipApplicationServiceTest` 与 `VisitorIdentityFilterTest`，锁定“首次绑定 session、跨访客拒绝访问、失效 token 换新、Set-Cookie 属性正确”

## 2. 扩展 visitor-aware 持久化与 ledger 查询契约

- [x] 2.1 为匿名访客表、`ai_agent_dialogue_session`、`ai_agent_dialogue_run` 增加 `visitor_id` 持久化模型、DAO、Mapper XML 和 `schema.sql` 变更
- [x] 2.2 扩展 `DialogueSession` / `DialogueRun` 及其 upsert/view/start record 模型，让 `visitorId` 进入 execution ledger 写侧
- [x] 2.3 调整 `ExecutionLedgerQueryService`、`IExecutionLedgerReadRepository`、`ExecutionLedgerReadRepository` 与相关 DAO 查询，使会话列表和详情前置查询按 `visitorId + sessionId` 过滤
- [x] 2.4 补充 `ExecutionLedgerQueryServiceTest`，锁定“知道别人的 sessionId 也不能读到历史或最近会话”

## 3. 把 visitor 归属接入真实对话入口、历史接口和上传入口

- [x] 3.1 扩展 `AgentRequest`、内部转发请求和 `AgentQueryServiceImpl`，在 `/web/api/v1/gpt/queryAgentStreamIncr -> /AutoAgent` 链路上显式传播 `visitorId`
- [x] 3.2 调整 `AiAgentController` 与 `ReactorController`，在主对话入口提交任务前完成 `visitorId -> sessionId` 绑定/校验，并以服务端请求上下文 visitor 为准
- [x] 3.3 调整 `AgentConversationHistoryController` 与 `AgentFileController`，让历史列表、详情回放和附件上传在进入核心逻辑前执行 visitor 归属校验
- [x] 3.4 补充 `AiAgentControllerVisitorBindingTest` 与 `AgentQueryServiceVisitorPropagationTest`，锁定 visitor 绑定、内部转发透传和入口拒绝逻辑

## 4. 引入 Spring 托管执行器并收敛主链路异步治理

- [x] 4.1 新增 `AgentExecutorProperties`、`AgentExecutorConfiguration` 和 dev/prod 配置项，定义 `dispatch / llm / tool / heartbeat` 执行器、Cookie 与 Origin 白名单参数
- [x] 4.2 调整 `BaseFilterConfig` 与 `SseLifecycleSupport`，把凭证型 CORS、visitor Cookie 和心跳调度统一接入 Spring 托管配置
- [x] 4.3 替换 `AiAgentController`、`ReactorController` 中的私有心跳线程池和 ad-hoc 派发逻辑，统一把执行器拒绝转换为可观测的“系统繁忙”失败
- [x] 4.4 替换 `LLM`、`VectorService`、配置加载策略、`BaseAgent`、`DataAgentQueryServiceImpl`、`Step2PlanExecuteNode` 等主链路 `ThreadUtil` 或默认 `supplyAsync` 调用点，显式指定受控执行器
- [x] 4.5 补充 `AgentExecutorConfigurationTest` 与 `AgentContextConvergenceBoundaryTest`，锁定命名执行器装配、拒绝语义、`ThreadUtil.execute(...)` 清理和默认 common pool 清理

## 5. 完成回归与上线前验收闭环

- [x] 5.1 运行 visitor 治理定向回归：`ConversationSessionOwnershipApplicationServiceTest`、`VisitorIdentityFilterTest`、`AiAgentControllerVisitorBindingTest`、`AgentQueryServiceVisitorPropagationTest`
- [x] 5.2 运行 execution ledger 与异步治理回归：`ExecutionLedgerQueryServiceTest`、`AgentExecutorConfigurationTest`、`AgentContextConvergenceBoundaryTest`
- [x] 5.3 运行 `mvn test -pl ai-agent-station-study-domain -am -DskipTests=false`，确认 domain 主链路回归通过
- [ ] 5.4 发布前人工清空无 `visitor_id` 的旧历史数据，并按生产站点配置核对 Cookie `Secure` / `SameSite` 与 Origin 白名单
