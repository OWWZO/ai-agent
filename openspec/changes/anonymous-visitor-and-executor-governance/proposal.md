## Why

当前真实对话链路仍然把前端传入的 `sessionId` 视为主要归属凭据，缺少后端匿名访客身份隔离，导致“知道别人的 `sessionId` 就可能读取或追加对方会话”的安全缺口。同时，主链路异步执行仍混用 `ThreadUtil`、`CompletableFuture` 默认公共线程池和 Controller 私有心跳线程池，拒绝语义不可观测、线程治理分散，已经开始影响可维护性与上线风险。

现在需要把这两个问题一起收口，因为匿名访客隔离本身会横跨浏览器入口、内部 `/AutoAgent` 转发、历史回放、文件上传和 execution ledger；如果并发执行模型继续保持分散状态，后续 visitor 归属校验、SSE 生命周期与错误处理会进一步耦合在多个入口里，成本只会越来越高。

## What Changes

- 为真实浏览器入口 `/web/api/v1/gpt/queryAgentStreamIncr` 引入基于 HttpOnly Cookie 的匿名访客身份解析与创建机制，并把 `visitorId` 沿内部 `/AutoAgent` 转发链路显式透传。
- 新增 `visitorId -> sessionId` 会话归属绑定与校验服务，首次访问时绑定会话，后续会话详情、会话列表、文件上传和 run/session 账本统一按当前匿名访客归属过滤。
- 扩展 execution ledger、dialogue session/run 持久化模型和查询契约，使 `visitorId` 成为历史查询与审计的显式维度，而不是仅凭 `sessionId` 读取。
- 将 `ThreadUtil.execute(...)`、默认 `CompletableFuture.supplyAsync(...)` 和 Controller 手工 `newScheduledThreadPool(...)` 收敛为 Spring 托管的 `dispatch / llm / tool / heartbeat` 命名执行器。
- 把执行器拒绝统一转换为可观测的“系统繁忙”失败，并收敛 CORS 与 visitor Cookie 的同站点生产配置。

## Capabilities

### New Capabilities
- `anonymous-visitor-session-governance`: 真实对话链路必须为匿名访客分配稳定身份，并以 `visitorId -> sessionId` 归属约束会话访问、历史读取和附件上传。
- `agent-executor-governance`: Agent 主链路异步任务必须运行在 Spring 托管命名执行器上，并在执行器饱和时返回明确失败而不是静默丢弃。

### Modified Capabilities
- 无

## Impact

- 影响 `ai-agent-station-study-trigger`：`AiAgentController`、`ReactorController`、`AgentConversationHistoryController`、`AgentFileController`、过滤器注册与 SSE 生命周期治理。
- 影响 `ai-agent-station-study-case`：新增匿名访客身份服务和会话归属应用服务，承接 visitor 解析、绑定与校验。
- 影响 `ai-agent-station-study-domain`：`AgentRequest`、内部转发请求、execution ledger run/session 模型、query service 契约，以及主链路异步执行入口。
- 影响 `ai-agent-station-study-infrastructure` 与 `ai-agent-station-study-app`：匿名访客/ledger 持久化、Mapper XML、`schema.sql`、执行器配置与 `application-dev.yml` / `application-prod.yml`。
- 不改前端 `sessionId` 生成协议，不引入正式账号体系，不覆盖多实例部署或后端串行化会话执行。
