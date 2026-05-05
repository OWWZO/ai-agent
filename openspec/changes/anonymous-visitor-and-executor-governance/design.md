## Context

当前真实浏览器入口是 `/web/api/v1/gpt/queryAgentStreamIncr`，前端继续自生成 `sessionId`，后端再把请求转发到内部 `/AutoAgent`。这条链路目前没有稳定的匿名访客身份层：`AiAgentController`、历史接口、文件上传和 execution ledger 读侧基本都依赖调用方给出的 `sessionId`，导致一旦别人知道某个 `sessionId`，就可能追加消息、读取历史或上传附件到不属于自己的会话。

并发治理也存在同样的边界问题。主链路仍混用 `ThreadUtil.execute(...)`、`CompletableFuture.supplyAsync(...)` 默认公共线程池，以及 `AiAgentController` / `ReactorController` 私有 `newScheduledThreadPool(...)` 心跳池。结果是线程来源分散、拒绝语义不一致、心跳生命周期不可观测，而且后续要把 visitor 校验、SSE 生命周期和异常处理收口时，必须同时面对多个异步模型。

这次改动是跨 `case / domain / infrastructure / trigger / app` 的横切变更，但约束明确：

- 前端 `sessionId` 生成逻辑保持不变，本期不改 `ui/`。
- 生产部署前提是前后端同服务器、同站点优先、HTTPS，不默认支持完全跨站 Cookie 场景。
- 单机部署，不引入 Redis、MQ、分布式锁或跨节点粘性会话。
- 后端仍不做会话串行化，只沿用前端“执行中禁输入”约束。
- 上线前由人工清空无 `visitor_id` 的旧历史数据，本期不做迁移或兼容回填。

## Goals / Non-Goals

**Goals:**

- 为匿名浏览器用户建立稳定的 HttpOnly visitor 身份，并在真实对话主链路上显式传播 `visitorId`。
- 建立 `visitorId -> sessionId` 首次绑定和后续校验机制，防止跨访客读写会话、历史和附件。
- 让 `DialogueSession`、`DialogueRun` 和 execution ledger 查询链路都变成 visitor-aware，而不是只信任 `sessionId`。
- 用 Spring 托管的 `dispatch / llm / tool / heartbeat` 命名执行器替换主链路里的公共线程池、`ThreadUtil` 和手工心跳线程池。
- 统一执行器拒绝语义和同站点 Cookie/CORS 配置，并用测试锁定 visitor 治理和异步治理边界。

**Non-Goals:**

- 不把匿名访客升级为正式账号，也不支持跨设备会话合并。
- 不改前端协议，不新增前端 visitor 存储，不调整单会话禁输入交互。
- 不解决多实例部署、分布式路由或后端 session 串行执行。
- 不为旧 ledger / history 数据做迁移、回填或双读兼容。
- 不把角色库等公共只读接口改造成 visitor 过滤接口。

## Decisions

### Decision 1: 在 HTTP Filter 层解析和签发匿名访客 Cookie，而不是把 visitor 身份交给前端

采用方案：

- 新增 `VisitorIdentityFilter`，在真实浏览器入口和会话相关接口前统一解析 `ai_agent_visitor_token` Cookie。
- 由 `AnonymousVisitorApplicationService` 负责 token 解析、失效换新、首次建档和最后访问时间刷新。
- 解析出的 `visitorId` 放入 `VisitorRequestContext`，trigger 层显式读取，前端 JavaScript 不直接访问该 Cookie。

原因：

- visitor 身份属于服务端安全边界，不能继续依赖前端 header 或 localStorage 自报。
- Filter 是唯一能覆盖对话入口、历史接口和文件上传的横切位置，最适合做同一身份解析逻辑。

备选方案：

- 方案 A：前端生成 deviceId/visitorId 并放到 header。实现简单，但无法防篡改，也和“HttpOnly Cookie”目标冲突。
- 方案 B：在每个 Controller 单独解析 Cookie。会重复实现，而且容易漏掉历史或上传等旁路接口。

### Decision 2: 会话归属采用“首次绑定 + 后续强校验”，服务端解析出的 `visitorId` 优先于请求体内容

采用方案：

- 新增 `ConversationSessionOwnershipApplicationService`。
- 当 `sessionId` 首次出现时，绑定到当前 `visitorId`；后续再次访问时必须校验归属一致。
- `/web/api/v1/gpt/queryAgentStreamIncr` 生成 `AgentRequest` 时显式写入当前 `visitorId`，内部 `/AutoAgent` 转发继续透传。
- `/AutoAgent` 若收到的 `visitorId` 为空或与当前请求上下文冲突，以服务端当前请求上下文为准。

原因：

- 前端继续生成 `sessionId` 的前提下，后端必须补上“谁拥有这个 session”的真实约束，否则 `sessionId` 只是一个可猜可传的字符串。
- 把 visitor 绑定集中到应用服务，可以让对话入口、历史读取和文件上传共享一套归属规则。

备选方案：

- 方案 A：继续只信任 `sessionId`。安全边界缺失，正是本期要修复的问题。
- 方案 B：把 visitor 归属只做在 trigger 层，不进 domain/ledger。这样历史回放和查询仓储仍无法按 visitor 过滤。

### Decision 3: `visitorId` 要贯穿 ledger 写侧和读侧全链路，而不是只做入口校验

采用方案：

- 给 `ai_agent_dialogue_session`、`ai_agent_dialogue_run` 增加 `visitor_id` 字段和索引。
- 扩展 `DialogueSessionUpsertRecord`、`DialogueRunStartRecord`、`DialogueSessionView`、`DialogueSession`、`DialogueRun` 等模型。
- 让 `ExecutionLedgerQueryService -> IExecutionLedgerReadRepository -> ExecutionLedgerReadRepository -> Dao / Mapper XML` 全链路变成 visitor-aware。

原因：

- 只在 Controller 做一次 `sessionId` 校验并不够，列表查询、详情回放前置查询和审计检索都需要明确的 visitor 维度。
- 归属进入账本后，才能保证新旧入口、回放与运维排障看到的是同一事实源。

备选方案：

- 方案 A：查询时临时回查最近一次请求的 visitor。事实不稳定，也会让读侧逻辑变复杂。
- 方案 B：仅给 session 表加 visitor，不给 run 表加。会丢失单次执行的归属审计维度。

### Decision 4: 以四类命名执行器替换 ad-hoc 异步模型，并显式建模拒绝语义

采用方案：

- 在 `AgentExecutorConfiguration` 中创建 `dispatch / llm / tool / heartbeat` 四类执行器或调度器。
- Controller 主派发走 `dispatchExecutor`，LLM 调用和向量/配置加载等异步任务显式指定 `llmExecutor` 或 `toolExecutor`。
- 心跳统一改为 Spring 托管 `TaskScheduler`。
- 当执行器饱和时，统一转换为明确的“系统繁忙”错误，并记录日志。

原因：

- 当前 `ThreadUtil`、默认 common pool 和手工 scheduler 混杂，出了问题难以判断是谁在抢线程。
- 只有显式命名执行器，才能为不同负载类型留出隔离、观测和调参空间。

备选方案：

- 方案 A：继续沿用默认公共线程池。实现最省事，但无法控制隔离和拒绝语义。
- 方案 B：只引入一个全局执行器。比现在略好，但 LLM、工具、派发和心跳之间仍会互相干扰。

### Decision 5: CORS 与 Cookie 策略按“同站点白名单 + 显式配置”收口，不做跨站兼容优先

采用方案：

- Cookie 名称固定为 `ai_agent_visitor_token`，`HttpOnly`、`Path=/`，`Secure` 与 `SameSite` 由配置显式控制。
- 只允许白名单 Origin 走 `allowCredentials=true` 的 CORS。
- dev / prod profile 都补齐 visitor cookie 与执行器配置。

原因：

- 当前部署假设已经明确是同服务器、同站点优先，继续为了潜在跨站场景放宽 CORS 只会扩大风险面。
- 显式配置比把 `SameSite`、`Secure` 写死在代码里更容易适配测试和生产环境。

备选方案：

- 方案 A：继续使用宽泛 Origin 或 `*`。与凭证型 CORS 组合不安全，也不满足 visitor cookie 约束。
- 方案 B：强行做跨站兼容默认值。会偏离当前部署前提，还会增加排障复杂度。

## Risks / Trade-offs

- [Cookie 丢失或被浏览器清理后，用户会被识别成新的匿名访客] → 这是匿名模式的接受范围；通过会话归属校验保证旧会话不会被新访客误拿到。
- [visitor 过滤切到读端口全链路后，任何漏改的 DAO / Mapper 都会造成行为不一致] → 通过 `ExecutionLedgerQueryServiceTest` 和 repository 契约测试锁定 visitor-aware 查询。
- [更严格的 CORS 与 same-site Cookie 可能暴露现有环境配置问题] → 把 Origin 白名单、`Secure`、`SameSite` 做成 profile 配置，并在 dev/prod 各自显式声明。
- [执行器池化后，如果容量配置不当，可能更早出现拒绝] → 用明确的饱和失败替代静默丢任务，并通过配置化容量与线程名前缀支持快速调优。
- [后端仍未串行化同一会话执行，极端并发下仍可能出现业务层竞争] → 继续明确这属于前端禁输入约束，留待后续单独治理，不在本期扩大范围。

## Migration Plan

1. 先新增匿名访客身份、会话归属应用服务与请求上下文，补 visitor 持久化和测试桩。
2. 再扩展 dialogue session/run schema、PO/DAO/Mapper 和 execution ledger 读写契约，让 `visitorId` 成为账本事实字段。
3. 把 visitor 解析和归属校验接入 `/web/api/v1/gpt/queryAgentStreamIncr`、`/AutoAgent`、历史接口和文件上传接口。
4. 引入 Spring 托管命名执行器与 heartbeat scheduler，替换 `ThreadUtil`、默认 common pool 和私有心跳池调用点。
5. 更新 dev/prod 配置，运行 visitor 治理与异步治理回归测试。
6. 发布前人工清空无 `visitor_id` 的旧历史数据，再上线新版本。

回滚策略：

- 代码回滚后可恢复旧入口和旧执行器模型。
- 已新增的 `visitor_id` 字段、匿名访客表和执行器配置允许保留，不要求做反向清理。
- 如果 visitor 治理上线后发现环境配置问题，可先通过回滚应用版本恢复旧行为，再调整白名单和 Cookie 配置。

## Open Questions

- 无阻塞性开放问题；部署前只需要把生产环境的白名单 Origin、`SameSite` 和 `Secure` 值与实际站点拓扑对齐。
