# Anonymous Visitor and Executor Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保持前端继续生成 `sessionId`、单机部署、前端单会话禁输入约束不变的前提下，为真实对话主链路补齐匿名访客隔离，并把 `ThreadUtil + 默认公共线程池 + Controller 私有心跳线程池` 收敛为 Spring 托管执行器。

**Architecture:** 生产部署前提固定为“前后端同服务器、同站点优先、HTTPS”。浏览器入口仍然是 `/web/api/v1/gpt/queryAgentStreamIncr`，服务端在该入口解析 `visitor_token` Cookie、绑定 `visitorId`，再把 `visitorId` 显式写入 `AgentRequest` 并随内部 `/AutoAgent` 转发继续透传，最终由 `AiAgentController`、会话历史接口、文件上传入口和 execution ledger 统一按 `visitorId -> sessionId` 做归属校验。并发治理采用“命名执行器分池”方案：新增 `dispatch / llm / tool / heartbeat` 四类执行器，替换 `ThreadUtil.execute(...)`、`CompletableFuture.supplyAsync(...)` 默认线程池以及 `AiAgentController/ReactorController` 中手工 `newScheduledThreadPool(...)` 的做法；线程池拒绝统一转换为可观测的“系统繁忙”失败，而不是静默丢任务。

**Tech Stack:** Java 17, Spring Boot 3.4.3, Spring AI 1.1.4, MyBatis / Mapper XML, MySQL 8, OkHttp SSE, React 19（前端协议保持不变）, JUnit 4 / Mockito

---

## Design Decisions Locked Before Implementation

- 生产部署默认是“同服务器、同站点、HTTPS”，本计划不默认支持前端站点与 API 站点完全跨站部署。
- 浏览器真实主入口是 `/web/api/v1/gpt/queryAgentStreamIncr`，不是直接请求 `/AutoAgent`；visitor 治理必须覆盖该入口及其内部转发链路。
- Cookie 名称统一使用 `ai_agent_visitor_token`，采用 HttpOnly，不允许前端 JavaScript 读取。
- 同站点生产默认使用 `Secure=true`、`Path=/`；`SameSite` 采用适合同站点子域部署的保守配置，由配置项显式控制，不写死“跨站兼容”语义。
- 只对白名单前端 Origin 开放凭证型 CORS；禁止继续使用“`allowCredentials=true` + `*`/宽泛模式”的组合。
- 访问控制统一收口为：先解析当前 `visitorId`，再校验 `sessionId` 归属；不能靠前端传的 `sessionId` 自证。
- 历史详情接口必须在进入 `ConversationHistoryReplayService` 前完成 visitor 归属校验，避免知道别人 `sessionId` 就能回放全量历史。
- execution ledger 的 visitor 过滤必须贯穿 `ExecutionLedgerQueryService -> IExecutionLedgerReadRepository -> ExecutionLedgerReadRepository -> *Dao/XML` 全链路，不能只改 service 实现类。
- 执行器拒绝必须转成明确异常或错误结果，不能沿用当前 `ThreadUtil` 的静默丢任务语义。

## File Map

- Create: `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/visitor/model/AnonymousVisitorIdentity.java`
  责任：封装当前请求匿名访客身份、原始 Cookie token 和“是否新建”的结果。
- Create: `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/visitor/AnonymousVisitorApplicationService.java`
  责任：解析匿名访客 Cookie、创建访客档案、刷新最后访问时间。
- Create: `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/visitor/ConversationSessionOwnershipApplicationService.java`
  责任：在 `sessionId` 首次出现时完成“会话归属绑定”，后续统一校验当前匿名访客是否拥有该会话，并提供 visitor 维度的会话列表/详情查询入口。
- Create: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/visitor/VisitorIdentityFilter.java`
  责任：从 Cookie 解析匿名访客令牌，委托应用层服务解析或创建访客，并把 `visitorId` 放入请求上下文。
- Create: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/visitor/VisitorRequestContext.java`
  责任：以 `ThreadLocal` 方式保存当前 HTTP 请求的 `visitorId`，供 trigger 层显式读取。
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/reactor/IVisitorIdentityDao.java`
  责任：匿名访客主表 DAO。
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/VisitorIdentityPO.java`
  责任：匿名访客表 PO。
- Create: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/visitor_identity_mapper.xml`
  责任：匿名访客表 SQL 映射。
- Create: `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/AgentExecutorProperties.java`
  责任：定义 `dispatch / llm / tool / heartbeat` 四类执行器与 visitor cookie/CORS 相关配置。
- Create: `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/AgentExecutorConfiguration.java`
  责任：创建命名 `Executor` / `TaskScheduler` Bean，统一拒绝策略和线程名前缀。
- Modify: `ai-agent-station-study-app/src/main/resources/db/schema.sql`
  责任：新增匿名访客表，并给 `ai_agent_dialogue_session`、`ai_agent_dialogue_run` 增加 `visitor_id` 字段与索引。
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_session_ledger_mapper.xml`
  责任：让会话主表 upsert / view / 查询具备 `visitor_id` 读写能力，并新增 visitor 维度查询 SQL。
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml`
  责任：让 run 账本同步持久化 `visitor_id`。
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/config/BaseFilterConfig.java`
  责任：注册 `VisitorIdentityFilter`，并把 CORS 收敛为可配置白名单。
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/AiAgentController.java`
  责任：在 `/AutoAgent` 主对话入口注入当前 `visitorId`，完成 `sessionId` 绑定/校验，并切换到 `dispatchExecutor + heartbeatScheduler`。
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/reactor/ReactorController.java`
  责任：和 `AiAgentController` 保持相同执行器治理策略，消除私有心跳线程池。
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationHistoryController.java`
  责任：会话列表与详情改为 visitor 维度查询，并在详情回放前显式校验归属。
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentFileController.java`
  责任：上传附件前校验 `sessionId` 归属。
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/reactor/support/SseLifecycleSupport.java`
  责任：心跳调度改为依赖 Spring 管理的 `TaskScheduler`。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/req/AgentRequest.java`
  责任：增加 `visitorId` 字段，供账本记录和后续审计使用。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/adapter/port/RemoteStreamRequest.java`
  责任：允许内部 `/web/api/v1/gpt/queryAgentStreamIncr -> /AutoAgent` 转发携带 visitor 透传头。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/AgentQueryServiceImpl.java`
  责任：在真实浏览器入口生成 `AgentRequest` 时写入 `visitorId`，并把它继续透传到内部 `/AutoAgent` 请求。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/DialogueRunStartRecord.java`
  责任：记录单次 run 归属的匿名访客。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/DialogueSessionUpsertRecord.java`
  责任：记录会话主表 `visitorId`。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/DialogueSessionView.java`
  责任：会话查询视图增加 `visitorId`。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/entity/DialogueSession.java`
  责任：会话实体增加 `visitorId`。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/entity/DialogueRun.java`
  责任：run 实体增加 `visitorId`。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/ExecutionLedgerRunSupport.java`
  责任：从 `AgentRequest` 读取 `visitorId` 并写入 run 启动记录。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/ExecutionLedgerQueryService.java`
  责任：改为 visitor-aware 查询契约。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/IExecutionLedgerReadRepository.java`
  责任：补齐 visitor-aware 读端口，避免 service 与仓储端口签名不一致。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/impl/ExecutionLedgerQueryServiceImpl.java`
  责任：通过 visitor-aware 读端口完成会话列表/详情/回放前置查询。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/impl/AgentExecutionRecorderImpl.java`
  责任：run/session 账本 upsert 时补齐 `visitorId`。
- Modify: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/repository/ExecutionLedgerReadRepository.java`
  责任：实现 visitor-aware 查询。
- Modify: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/reactor/IDialogueSessionLedgerDao.java`
  责任：定义 visitor-aware session 查询方法。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/llm/LLM.java`
  责任：所有 `CompletableFuture.supplyAsync(...)` 显式指定 `llmExecutor`。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/VectorService.java`
  责任：向量召回异步任务显式指定执行器。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/business/data/impl/AiClientLoadDataStrategy.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/business/data/impl/AiClientApiLoadDataStrategy.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/business/data/impl/AiClientModelLoadDataStrategy.java`
  责任：配置加载相关 `supplyAsync` 统一指定受控执行器。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/rag/DataAgentQueryServiceImpl.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/agent/BaseAgent.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/step/Step2PlanExecuteNode.java`
  责任：替换 `ThreadUtil.execute(...)`。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/util/ThreadUtil.java`
  责任：删除调用点后退化为废弃占位或删除。
- Modify: `ai-agent-station-study-app/src/main/resources/application-dev.yml`
- Modify: `ai-agent-station-study-app/src/main/resources/application-prod.yml`
  责任：补齐 visitor cookie、same-site、secure、origin 白名单和四类执行器配置。
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationSessionOwnershipApplicationServiceTest.java`
  责任：覆盖“首次绑定 session、跨访客拒绝访问、同访客重复访问通过”。
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/VisitorIdentityFilterTest.java`
  责任：覆盖“无 Cookie 自动创建、有 Cookie 复用、失效 Cookie 自动换新、Set-Cookie 属性正确”。
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AiAgentControllerVisitorBindingTest.java`
  责任：覆盖 `/AutoAgent` 入口 visitor 绑定/校验与受控执行器提交。
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java`
  责任：补 visitor 维度的会话列表/详情查询断言，锁定“知道别人 sessionId 也不能读”。
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java`
  责任：补 `ThreadUtil.execute(` 与默认 `supplyAsync(` 治理回归。
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutorConfigurationTest.java`
  责任：覆盖命名执行器 Bean、拒绝策略和 heartbeat scheduler 装配。
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentQueryServiceVisitorPropagationTest.java`
  责任：覆盖 `/web/api/v1/gpt/queryAgentStreamIncr -> /AutoAgent` 内部转发时 visitor 透传。

## Constraints

- 前端继续生成 `sessionId`，本计划不改 `ui/` 会话 ID 生成逻辑。
- 当前部署前提是前后端同服务器；计划按同站点优先设计，不默认支持完全跨站 Cookie。
- 单机部署，不引入 Redis 分布式锁、消息队列或跨节点粘性会话方案。
- 后端会话串行化暂不实现；只依赖前端“任务执行中禁输入”约束。
- 只对会话相关接口加 visitor 归属校验；角色库等纯只读公共接口不引入 visitor 过滤。
- 不把匿名访客升级为正式账号，也不做跨设备合并。

## Verification Commands

- `mvn test -pl ai-agent-station-study-app -Dtest=ConversationSessionOwnershipApplicationServiceTest,VisitorIdentityFilterTest,AgentExecutorConfigurationTest,AiAgentControllerVisitorBindingTest,AgentQueryServiceVisitorPropagationTest -DskipTests=false`
- `mvn test -pl ai-agent-station-study-app -Dtest=ExecutionLedgerQueryServiceTest,AgentContextConvergenceBoundaryTest -DskipTests=false`
- `mvn test -pl ai-agent-station-study-domain -am -DskipTests=false`

## Task 1: Lock Real Visitor Ownership and Executor Governance Regressions First

**Files:**
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationSessionOwnershipApplicationServiceTest.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/VisitorIdentityFilterTest.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AiAgentControllerVisitorBindingTest.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentQueryServiceVisitorPropagationTest.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutorConfigurationTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java`

- [ ] **Step 1: Write the failing ownership and query propagation regressions**

```java
@Test
public void shouldBindSessionToCurrentVisitorOnFirstUse() {
    InMemoryOwnershipRepository repository = new InMemoryOwnershipRepository();
    ConversationSessionOwnershipApplicationService service =
            new ConversationSessionOwnershipApplicationService(repository, null);

    DialogueSession session = service.ensureSessionAccessible("visitor-001", "session-001", "你好");

    Assert.assertEquals("session-001", session.getSessionId());
    Assert.assertEquals("visitor-001", session.getVisitorId());
    Assert.assertEquals("visitor-001", repository.findVisitorId("session-001"));
}

@Test(expected = IllegalStateException.class)
public void shouldRejectSessionOwnedByAnotherVisitor() {
    InMemoryOwnershipRepository repository = new InMemoryOwnershipRepository();
    repository.bind("session-001", "visitor-001");
    ConversationSessionOwnershipApplicationService service =
            new ConversationSessionOwnershipApplicationService(repository, null);

    service.ensureSessionAccessible("visitor-002", "session-001", "非法访问");
}
```

```java
@Test
public void shouldPropagateVisitorIdentityWhenBrowserEntryRelaysToAutoAgent() {
    RecordingRemoteStreamPort remoteStreamPort = new RecordingRemoteStreamPort();
    AgentQueryServiceImpl service = newAgentQueryService(remoteStreamPort);
    GptQueryReq req = GptQueryReq.builder()
            .requestId("req-001")
            .sessionId("session-001")
            .query("帮我总结一下项目")
            .build();

    VisitorRequestContext.bind("visitor-001");
    try {
        service.queryAgentStreamIncr(req, new SilentAgentMessageStream());
    } finally {
        VisitorRequestContext.clear();
    }

    Assert.assertTrue(remoteStreamPort.getLastRequestBody().contains("\"visitorId\":\"visitor-001\""));
}
```

- [ ] **Step 2: Write the failing filter, controller and query-service regressions**

```java
@Test
public void shouldCreateVisitorCookieWhenRequestHasNoToken() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/conversation/sessions");
    MockHttpServletResponse response = new MockHttpServletResponse();
    VisitorIdentityFilter filter = new VisitorIdentityFilter(new StubAnonymousVisitorApplicationService(), cookieProperties());

    filter.doFilter(request, response, new MockFilterChain());

    String setCookie = response.getHeader("Set-Cookie");
    Assert.assertNotNull(setCookie);
    Assert.assertTrue(setCookie.contains("ai_agent_visitor_token="));
    Assert.assertTrue(setCookie.contains("HttpOnly"));
}
```

```java
@Test
public void shouldBindSessionBeforeDispatchingAutoAgent() throws Exception {
    AgentRequest request = AgentRequest.builder()
            .requestId("req-001")
            .sessionId("session-001")
            .query("帮我总结一下这个项目")
            .build();
    when(ownershipService.ensureSessionAccessible("visitor-001", "session-001", "帮我总结一下这个项目"))
            .thenReturn(DialogueSession.builder().sessionId("session-001").visitorId("visitor-001").build());

    VisitorRequestContext.bind("visitor-001");
    try {
        controller.AutoAgent(request);
    } finally {
        VisitorRequestContext.clear();
    }

    verify(ownershipService).ensureSessionAccessible("visitor-001", "session-001", "帮我总结一下这个项目");
    verify(agentDispatchExecutor).execute(any(Runnable.class));
    Assert.assertEquals("visitor-001", request.getVisitorId());
}
```

```java
@Test
public void shouldRejectQueryingAnotherVisitorsSession() {
    ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
    seedRun(ctx, "req-visitor-001", "session-visitor-001", "visitor-001");

    Assert.assertNull(ctx.queryService.querySession("visitor-002", "session-visitor-001"));
    Assert.assertTrue(ctx.queryService.queryRecentSessions("visitor-002", 20).isEmpty());
}
```

- [ ] **Step 3: Run focused regressions to verify they fail**

Run:
- `mvn test -pl ai-agent-station-study-app -Dtest=ConversationSessionOwnershipApplicationServiceTest -DskipTests=false`
- `mvn test -pl ai-agent-station-study-app -Dtest=VisitorIdentityFilterTest,AiAgentControllerVisitorBindingTest,AgentQueryServiceVisitorPropagationTest -DskipTests=false`
- `mvn test -pl ai-agent-station-study-app -Dtest=ExecutionLedgerQueryServiceTest -DskipTests=false`

Expected: FAIL because visitor-aware services, cookie handling, query propagation and visitor-scoped ledger queries do not yet exist

- [ ] **Step 4: Add the failing executor topology regression**

```java
@Test
public void shouldExposeNamedExecutors() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.register(AgentExecutorConfiguration.class, AgentExecutorProperties.class);
    context.refresh();

    Assert.assertTrue(context.containsBean("agentDispatchExecutor"));
    Assert.assertTrue(context.containsBean("agentLlmExecutor"));
    Assert.assertTrue(context.containsBean("agentToolExecutor"));
    Assert.assertTrue(context.containsBean("agentHeartbeatScheduler"));
}
```

- [ ] **Step 5: Run the executor regression and commit failing tests**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=AgentExecutorConfigurationTest -DskipTests=false`

Expected: FAIL because the named executor and heartbeat scheduler beans do not yet exist

```bash
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationSessionOwnershipApplicationServiceTest.java ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/VisitorIdentityFilterTest.java ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AiAgentControllerVisitorBindingTest.java ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentQueryServiceVisitorPropagationTest.java ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutorConfigurationTest.java ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java
git commit -m "test: lock visitor isolation and executor governance regressions"
```

## Task 2: Add Visitor Persistence and Visitor-Aware Ledger Data Model

**Files:**
- Modify: `ai-agent-station-study-app/src/main/resources/db/schema.sql`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/reactor/IVisitorIdentityDao.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/VisitorIdentityPO.java`
- Create: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/visitor_identity_mapper.xml`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/entity/DialogueSession.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/entity/DialogueRun.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/DialogueSessionView.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/DialogueSessionUpsertRecord.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/DialogueRunStartRecord.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/IExecutionLedgerReadRepository.java`
- Modify: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/reactor/IDialogueSessionLedgerDao.java`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_session_ledger_mapper.xml`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml`

- [ ] **Step 1: Add schema and read-port deltas**

```sql
CREATE TABLE IF NOT EXISTS ai_agent_visitor (
    id BIGINT NOT NULL AUTO_INCREMENT,
    visitor_id VARCHAR(64) NOT NULL COMMENT '匿名访客ID',
    token_digest VARCHAR(128) NOT NULL COMMENT 'Cookie token 摘要',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1-有效 0-禁用',
    first_seen_at DATETIME NOT NULL,
    last_seen_at DATETIME NOT NULL,
    last_ip VARCHAR(64) NULL,
    last_user_agent VARCHAR(512) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_visitor_id (visitor_id),
    UNIQUE KEY uk_visitor_token_digest (token_digest),
    KEY idx_visitor_last_seen (deleted, last_seen_at DESC)
);

ALTER TABLE ai_agent_dialogue_session
    ADD COLUMN visitor_id VARCHAR(64) NULL COMMENT '匿名访客ID' AFTER session_id,
    ADD KEY idx_dialogue_session_visitor_active (visitor_id, deleted, last_active_at DESC);

ALTER TABLE ai_agent_dialogue_run
    ADD COLUMN visitor_id VARCHAR(64) NULL COMMENT '匿名访客ID' AFTER session_id,
    ADD KEY idx_dialogue_run_visitor_create (visitor_id, deleted, create_time DESC);
```

```java
public interface IExecutionLedgerReadRepository {

    DialogueSessionView querySession(String visitorId, String sessionId);

    List<DialogueSessionView> queryRecentSessions(String visitorId, int limit);
}
```

```java
public interface IDialogueSessionLedgerDao {

    DialogueSessionView querySessionViewByVisitor(@Param("visitorId") String visitorId,
                                                  @Param("sessionId") String sessionId);

    List<DialogueSessionView> queryRecentSessionsByVisitor(@Param("visitorId") String visitorId,
                                                           @Param("limit") int limit);
}
```

- [ ] **Step 2: Run focused tests**

Run:
- `mvn test -pl ai-agent-station-study-app -Dtest=ConversationSessionOwnershipApplicationServiceTest -DskipTests=false`
- `mvn test -pl ai-agent-station-study-app -Dtest=ExecutionLedgerQueryServiceTest -DskipTests=false`

Expected: FAIL because the schema/model/DAO layer still lacks `visitor_id`

- [ ] **Step 3: Implement the visitor persistence model and visitor-aware ledger mappings**

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitorIdentityPO {
    private Long id;
    private String visitorId;
    private String tokenDigest;
    private Integer status;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private String lastIp;
    private String lastUserAgent;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
```

```xml
<insert id="upsertSession" parameterType="org.wwz.ai.domain.agent.ledger.model.DialogueSessionUpsertRecord">
    INSERT INTO ai_agent_dialogue_session
    (session_id, visitor_id, title, status, latest_request_id, latest_query_text, latest_summary_text,
     run_count, finished_run_count, failed_run_count, started_at, last_active_at, deleted)
    VALUES
    (#{sessionId}, #{visitorId}, #{title}, #{status}, #{latestRequestId}, #{latestQueryText}, #{latestSummaryText},
     #{runCount}, #{finishedRunCount}, #{failedRunCount}, #{startedAt}, #{lastActiveAt}, 0)
    ON DUPLICATE KEY UPDATE
        visitor_id = CASE
            WHEN visitor_id IS NULL OR visitor_id = '' THEN VALUES(visitor_id)
            ELSE visitor_id
        END,
        title = VALUES(title),
        status = VALUES(status),
        latest_request_id = VALUES(latest_request_id),
        latest_query_text = VALUES(latest_query_text),
        latest_summary_text = VALUES(latest_summary_text),
        run_count = VALUES(run_count),
        finished_run_count = VALUES(finished_run_count),
        failed_run_count = VALUES(failed_run_count),
        started_at = VALUES(started_at),
        last_active_at = VALUES(last_active_at),
        deleted = 0
</insert>
```

- [ ] **Step 4: Run tests to verify the persistence layer now supports `visitor_id`**

Run:
- `mvn test -pl ai-agent-station-study-app -Dtest=ExecutionLedgerQueryServiceTest -DskipTests=false`
- `mvn test -pl ai-agent-station-study-app -Dtest=ConversationSessionOwnershipApplicationServiceTest -DskipTests=false`

Expected: compile-time `visitorId` model/mapper issues disappear; query tests may still FAIL until service wiring is completed

- [ ] **Step 5: Commit the data model layer**

```bash
git add ai-agent-station-study-app/src/main/resources/db/schema.sql ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_session_ledger_mapper.xml ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml ai-agent-station-study-app/src/main/resources/mybatis/mapper/visitor_identity_mapper.xml ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/reactor/IVisitorIdentityDao.java ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/reactor/IDialogueSessionLedgerDao.java ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/VisitorIdentityPO.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/entity/DialogueSession.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/entity/DialogueRun.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/DialogueSessionView.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/DialogueSessionUpsertRecord.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/model/DialogueRunStartRecord.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/IExecutionLedgerReadRepository.java
git commit -m "feat: add visitor persistence and ledger ownership fields"
```

## Task 3: Add Anonymous Visitor Resolution and First-Use Session Ownership Services

**Files:**
- Create: `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/visitor/model/AnonymousVisitorIdentity.java`
- Create: `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/visitor/AnonymousVisitorApplicationService.java`
- Create: `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/visitor/ConversationSessionOwnershipApplicationService.java`
- Create: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/visitor/VisitorIdentityFilter.java`
- Create: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/visitor/VisitorRequestContext.java`
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/config/BaseFilterConfig.java`

- [ ] **Step 1: Add the failing service and filter skeleton**

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnonymousVisitorIdentity {
    private String visitorId;
    private String rawToken;
    private boolean newlyCreated;
}
```

```java
public final class VisitorRequestContext {
    private static final ThreadLocal<String> VISITOR_HOLDER = new ThreadLocal<>();

    public static void bind(String visitorId) {
        VISITOR_HOLDER.set(visitorId);
    }

    public static String requireVisitorId() {
        String visitorId = VISITOR_HOLDER.get();
        if (visitorId == null || visitorId.isBlank()) {
            throw new IllegalStateException("当前请求缺少 visitorId");
        }
        return visitorId;
    }

    public static void clear() {
        VISITOR_HOLDER.remove();
    }
}
```

- [ ] **Step 2: Run the filter and ownership tests**

Run:
- `mvn test -pl ai-agent-station-study-app -Dtest=VisitorIdentityFilterTest -DskipTests=false`
- `mvn test -pl ai-agent-station-study-app -Dtest=ConversationSessionOwnershipApplicationServiceTest -DskipTests=false`

Expected: FAIL because the service/filter skeletons are not wired to repository logic

- [ ] **Step 3: Implement opaque-cookie visitor resolution and first-use session binding**

```java
public AnonymousVisitorIdentity resolveOrCreate(String rawToken, String userAgent, String ip) {
    if (StringUtils.isNotBlank(rawToken)) {
        VisitorIdentityPO existing = visitorIdentityDao.queryByTokenDigest(sha256(rawToken));
        if (existing != null && Integer.valueOf(1).equals(existing.getStatus())) {
            visitorIdentityDao.updateLastSeen(existing.getVisitorId(), LocalDateTime.now(), ip, userAgent);
            return AnonymousVisitorIdentity.builder()
                    .visitorId(existing.getVisitorId())
                    .rawToken(rawToken)
                    .newlyCreated(false)
                    .build();
        }
    }

    String visitorId = "visitor_" + UUID.randomUUID().toString().replace("-", "");
    String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    visitorIdentityDao.insert(VisitorIdentityPO.builder()
            .visitorId(visitorId)
            .tokenDigest(sha256(token))
            .status(1)
            .firstSeenAt(LocalDateTime.now())
            .lastSeenAt(LocalDateTime.now())
            .lastIp(ip)
            .lastUserAgent(userAgent)
            .deleted(0)
            .build());
    return AnonymousVisitorIdentity.builder()
            .visitorId(visitorId)
            .rawToken(token)
            .newlyCreated(true)
            .build();
}
```

```java
public DialogueSession ensureSessionAccessible(String visitorId, String sessionId, String queryText) {
    if (StringUtils.isBlank(visitorId) || StringUtils.isBlank(sessionId)) {
        throw new IllegalArgumentException("visitorId 和 sessionId 不能为空");
    }
    DialogueSession existing = dialogueSessionLedgerDao.queryBySessionId(sessionId);
    if (existing == null) {
        executionLedgerWriteRepository.upsertSession(DialogueSessionUpsertRecord.builder()
                .sessionId(sessionId)
                .visitorId(visitorId)
                .title(StringUtils.abbreviate(StringUtils.defaultString(queryText), 60))
                .status(0)
                .runCount(0)
                .finishedRunCount(0)
                .failedRunCount(0)
                .startedAt(LocalDateTime.now())
                .lastActiveAt(LocalDateTime.now())
                .build());
        return dialogueSessionLedgerDao.queryBySessionId(sessionId);
    }
    if (!StringUtils.equals(existing.getVisitorId(), visitorId)) {
        throw new IllegalStateException("当前访客无权访问该会话");
    }
    return existing;
}
```

```java
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
    AnonymousVisitorIdentity identity = anonymousVisitorApplicationService.resolveOrCreate(
            readCookieValue(request, "ai_agent_visitor_token"),
            request.getHeader("User-Agent"),
            request.getRemoteAddr()
    );
    VisitorRequestContext.bind(identity.getVisitorId());
    if (identity.isNewlyCreated()) {
        ResponseCookie cookie = ResponseCookie.from("ai_agent_visitor_token", identity.getRawToken())
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path("/")
                .maxAge(Duration.ofDays(365))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
    try {
        filterChain.doFilter(request, response);
    } finally {
        VisitorRequestContext.clear();
    }
}
```

- [ ] **Step 4: Run the visitor-related regression suite**

Run:
- `mvn test -pl ai-agent-station-study-app -Dtest=VisitorIdentityFilterTest,ConversationSessionOwnershipApplicationServiceTest -DskipTests=false`

Expected: PASS

- [ ] **Step 5: Commit the visitor resolution layer**

```bash
git add ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/visitor ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/visitor ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/config/BaseFilterConfig.java
git commit -m "feat: add anonymous visitor filter and session ownership service"
```

## Task 4: Wire Visitor Ownership Into the Real Browser Entry, Internal Relay, History Detail, File Upload, and Ledger Query Path

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/req/AgentRequest.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/adapter/port/RemoteStreamRequest.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/AgentQueryServiceImpl.java`
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/AiAgentController.java`
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/reactor/ReactorController.java`
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationHistoryController.java`
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentFileController.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/ExecutionLedgerRunSupport.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/ExecutionLedgerQueryService.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/impl/ExecutionLedgerQueryServiceImpl.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/impl/AgentExecutionRecorderImpl.java`
- Modify: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/repository/ExecutionLedgerReadRepository.java`

- [ ] **Step 1: Write the failing controller and relay propagation regressions**

```java
@Test
public void shouldBindSessionBeforeDispatchingAutoAgent() throws Exception {
    AgentRequest request = AgentRequest.builder()
            .requestId("req-001")
            .sessionId("session-001")
            .query("帮我总结一下这个项目")
            .build();
    when(ownershipService.ensureSessionAccessible("visitor-001", "session-001", "帮我总结一下这个项目"))
            .thenReturn(DialogueSession.builder().sessionId("session-001").visitorId("visitor-001").build());

    VisitorRequestContext.bind("visitor-001");
    try {
        controller.AutoAgent(request);
    } finally {
        VisitorRequestContext.clear();
    }

    verify(ownershipService).ensureSessionAccessible("visitor-001", "session-001", "帮我总结一下这个项目");
    Assert.assertEquals("visitor-001", request.getVisitorId());
}
```

```java
@Test
public void shouldScopeConversationDetailByVisitorBeforeReplay() {
    ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
    seedRun(ctx, "req-history-001", "session-history-001", "visitor-001");

    Assert.assertNull(ctx.queryService.querySession("visitor-002", "session-history-001"));
}
```

- [ ] **Step 2: Run focused regressions**

Run:
- `mvn test -pl ai-agent-station-study-app -Dtest=AiAgentControllerVisitorBindingTest -DskipTests=false`
- `mvn test -pl ai-agent-station-study-app -Dtest=AgentQueryServiceVisitorPropagationTest -DskipTests=false`
- `mvn test -pl ai-agent-station-study-app -Dtest=ExecutionLedgerQueryServiceTest -DskipTests=false`

Expected: FAIL because controllers do not yet read `visitorId`, browser entry does not yet propagate visitor identity, and detail/history queries are not visitor-scoped

- [ ] **Step 3: Implement visitor-aware browser entry, internal relay and session queries**

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {
    private String requestId;
    private String sessionId;
    private String visitorId;
    private String erp;
    private String query;
}
```

```java
private AgentRequest buildAgentRequest(GptQueryReq req) {
    AgentRequest request = new AgentRequest();
    request.setRequestId(req.getTraceId());
    request.setSessionId(req.getSessionId());
    request.setVisitorId(VisitorRequestContext.requireVisitorId());
    request.setErp(req.getUser());
    request.setQuery(req.getQuery());
    return request;
}
```

```java
private RemoteStreamRequest buildRemoteRequest(AgentRequest request) {
    return RemoteStreamRequest.builder()
            .method("POST")
            .url("http://127.0.0.1:8100/AutoAgent")
            .headers(Map.of(
                    "Content-Type", "application/json",
                    "X-Visitor-Id", request.getVisitorId()
            ))
            .body(JSONObject.toJSONString(request))
            .connectTimeoutSeconds(60L)
            .readTimeoutSeconds((long) reactorConfig.getSseClientReadTimeout())
            .writeTimeoutSeconds(1800L)
            .callTimeoutSeconds((long) reactorConfig.getSseClientConnectTimeout())
            .build();
}
```

```java
@PostMapping("/AutoAgent")
public SseEmitter AutoAgent(@RequestBody AgentRequest request) throws UnsupportedEncodingException {
    String visitorId = StringUtils.defaultIfBlank(request.getVisitorId(), VisitorRequestContext.requireVisitorId());
    request.setVisitorId(visitorId);
    conversationSessionOwnershipApplicationService.ensureSessionAccessible(
            visitorId,
            request.getSessionId(),
            request.getQuery()
    );
    // 其余调度逻辑保持不变
}
```

```java
@GetMapping("/{sessionId}")
public Response<ConversationHistoryDetailRespVO> detail(@PathVariable("sessionId") String sessionId) {
    String visitorId = VisitorRequestContext.requireVisitorId();
    conversationSessionOwnershipApplicationService.ensureSessionAccessible(visitorId, sessionId, null);
    ConversationHistoryDetail detail = conversationHistoryReplayService.queryConversationHistory(sessionId);
    return Response.<ConversationHistoryDetailRespVO>builder()
            .code(ResponseCode.SUCCESS.getCode())
            .info(ResponseCode.SUCCESS.getInfo())
            .data(toDetailRespVO(detail))
            .build();
}
```

```java
public interface ExecutionLedgerQueryService {
    DialogueSessionView querySession(String visitorId, String sessionId);
    List<DialogueSessionView> queryRecentSessions(String visitorId, int limit);
}
```

- [ ] **Step 4: Run the conversation/file/history regression suite**

Run:
- `mvn test -pl ai-agent-station-study-app -Dtest=AiAgentControllerVisitorBindingTest,AgentQueryServiceVisitorPropagationTest -DskipTests=false`
- `mvn test -pl ai-agent-station-study-app -Dtest=ExecutionLedgerQueryServiceTest -DskipTests=false`

Expected: PASS

- [ ] **Step 5: Commit the visitor-aware entry points**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/req/AgentRequest.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/adapter/port/RemoteStreamRequest.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/AgentQueryServiceImpl.java ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/AiAgentController.java ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/reactor/ReactorController.java ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationHistoryController.java ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentFileController.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/ExecutionLedgerRunSupport.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/ExecutionLedgerQueryService.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/impl/ExecutionLedgerQueryServiceImpl.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/impl/AgentExecutionRecorderImpl.java ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/repository/ExecutionLedgerReadRepository.java
git commit -m "feat: enforce visitor ownership on browser entry and history endpoints"
```

## Task 5: Introduce Spring-Managed Named Executors, Same-Site Cookie Config, and Failure Semantics

**Files:**
- Create: `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/AgentExecutorProperties.java`
- Create: `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/AgentExecutorConfiguration.java`
- Modify: `ai-agent-station-study-app/src/main/resources/application-dev.yml`
- Modify: `ai-agent-station-study-app/src/main/resources/application-prod.yml`
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/config/BaseFilterConfig.java`
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/reactor/support/SseLifecycleSupport.java`
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/AiAgentController.java`
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/reactor/ReactorController.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutorConfigurationTest.java`

- [ ] **Step 1: Add the failing named-executor configuration skeleton**

```java
@Data
@ConfigurationProperties(prefix = "autobots.execution")
public class AgentExecutorProperties {

    private Pool dispatch = new Pool(16, 32, 200, "AbortPolicy", "agent-dispatch-");
    private Pool llm = new Pool(16, 32, 100, "AbortPolicy", "agent-llm-");
    private Pool tool = new Pool(8, 16, 50, "AbortPolicy", "agent-tool-");
    private Heartbeat heartbeat = new Heartbeat(2, "agent-heartbeat-", 10);
    private VisitorCookie visitorCookie = new VisitorCookie(true, "Lax", true, List.of("https://app.example.com"));
}
```

- [ ] **Step 2: Run the executor configuration regression test**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=AgentExecutorConfigurationTest -DskipTests=false`

Expected: FAIL because named executors, heartbeat scheduler and cookie/CORS config beans do not yet exist

- [ ] **Step 3: Implement the named executor beans and explicit rejection semantics**

```java
@Bean("agentDispatchExecutor")
public ThreadPoolTaskExecutor agentDispatchExecutor(AgentExecutorProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.getDispatch().getCorePoolSize());
    executor.setMaxPoolSize(properties.getDispatch().getMaxPoolSize());
    executor.setQueueCapacity(properties.getDispatch().getQueueCapacity());
    executor.setThreadNamePrefix(properties.getDispatch().getThreadNamePrefix());
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.initialize();
    return executor;
}
```

```java
try {
    agentDispatchExecutor.execute(dispatchTask);
} catch (TaskRejectedException ex) {
    log.warn("{} dispatch rejected because executor is saturated", request.getRequestId(), ex);
    emitter.completeWithError(new IllegalStateException("系统繁忙，请稍后重试", ex));
}
```

```java
public static ScheduledFuture<?> startHeartbeat(TaskScheduler scheduler,
                                                SseEmitter emitter,
                                                String requestId,
                                                long heartbeatIntervalMillis,
                                                Logger log) {
    return scheduler.scheduleAtFixedRate(() -> {
        try {
            log.info("{} send heartbeat", requestId);
            emitter.send("heartbeat");
        } catch (Exception e) {
            if (SseClientDisconnectDetector.isClientDisconnected(e)) {
                log.info("{} heartbeat stopped because SSE client disconnected", requestId);
                emitter.complete();
                return;
            }
            log.warn("{} heartbeat failed, closing connection", requestId, e);
            emitter.completeWithError(e);
        }
    }, Duration.ofMillis(heartbeatIntervalMillis));
}
```

- [ ] **Step 4: Run the focused executor regression commands**

Run:
- `mvn test -pl ai-agent-station-study-app -Dtest=AgentExecutorConfigurationTest -DskipTests=false`
- `mvn test -pl ai-agent-station-study-app -Dtest=AiAgentControllerVisitorBindingTest -DskipTests=false`

Expected: PASS

- [ ] **Step 5: Commit the named executor and cookie configuration**

```bash
git add ai-agent-station-study-app/src/main/java/org/wwz/ai/config/AgentExecutorProperties.java ai-agent-station-study-app/src/main/java/org/wwz/ai/config/AgentExecutorConfiguration.java ai-agent-station-study-app/src/main/resources/application-dev.yml ai-agent-station-study-app/src/main/resources/application-prod.yml ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/config/BaseFilterConfig.java ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/reactor/support/SseLifecycleSupport.java ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/AiAgentController.java ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/reactor/ReactorController.java ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutorConfigurationTest.java
git commit -m "feat: add managed executors and same-site visitor config"
```

## Task 6: Replace `ThreadUtil` and Default Common Pool Usage Across the Agent Runtime

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/llm/LLM.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/VectorService.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/business/data/impl/AiClientLoadDataStrategy.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/business/data/impl/AiClientApiLoadDataStrategy.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/business/data/impl/AiClientModelLoadDataStrategy.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/rag/DataAgentQueryServiceImpl.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/agent/BaseAgent.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/step/Step2PlanExecuteNode.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/util/ThreadUtil.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java`

- [ ] **Step 1: Add the failing async-governance regression**

```java
@Test
public void shouldRemoveThreadUtilFromMainlineAsyncEntrypoints() throws Exception {
    List<String> offenders = findFilesContaining("ThreadUtil.execute(");
    Assert.assertTrue("主链路不应继续直接使用 ThreadUtil: " + offenders, offenders.isEmpty());
}

@Test
public void shouldRemoveDefaultCompletableFutureCommonPoolUsage() throws Exception {
    List<String> offenders = findFilesContaining("CompletableFuture.supplyAsync(() -> {");
    Assert.assertTrue("主链路不应继续使用未指定执行器的 supplyAsync: " + offenders, offenders.isEmpty());
}
```

- [ ] **Step 2: Run the focused async regression**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=AgentContextConvergenceBoundaryTest -DskipTests=false`

Expected: FAIL because `ThreadUtil.execute(...)` and默认 `supplyAsync(...)` still exist

- [ ] **Step 3: Replace `ThreadUtil` and explicit common-pool usages with named executors**

```java
@Resource(name = "agentLlmExecutor")
private Executor agentLlmExecutor;

return CompletableFuture.supplyAsync(() -> {
    ChatResponse response = chatClient.prompt().call().chatResponse();
    return response.getResult().getOutput().getText();
}, agentLlmExecutor).orTimeout(timeout, TimeUnit.SECONDS);
```

```java
@Resource(name = "agentToolExecutor")
private Executor agentToolExecutor;

CompletableFuture<List<AiClientApiVO>> future =
        CompletableFuture.supplyAsync(() -> repository.queryAiClientApiVOList(ids), agentToolExecutor);
```

```java
@Deprecated
public class ThreadUtil {
    private ThreadUtil() {
    }
}
```

- [ ] **Step 4: Run the full regression set**

Run:
- `mvn test -pl ai-agent-station-study-app -Dtest=ConversationSessionOwnershipApplicationServiceTest,VisitorIdentityFilterTest,AgentExecutorConfigurationTest,AiAgentControllerVisitorBindingTest,AgentQueryServiceVisitorPropagationTest,AgentContextConvergenceBoundaryTest -DskipTests=false`
- `mvn test -pl ai-agent-station-study-app -Dtest=ExecutionLedgerQueryServiceTest -DskipTests=false`
- `mvn test -pl ai-agent-station-study-domain -am -DskipTests=false`

Expected: PASS

- [ ] **Step 5: Commit the async governance cleanup**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/llm/LLM.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/VectorService.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/business/data/impl/AiClientLoadDataStrategy.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/business/data/impl/AiClientApiLoadDataStrategy.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/business/data/impl/AiClientModelLoadDataStrategy.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/rag/DataAgentQueryServiceImpl.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/agent/BaseAgent.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/step/Step2PlanExecuteNode.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/util/ThreadUtil.java ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java
git commit -m "refactor: replace thread util with managed agent executors"
```

## Final Verification Checklist

- [ ] 匿名用户首次访问会自动写入 `ai_agent_visitor` 并返回 `HttpOnly` Cookie
- [ ] 同站点生产配置下，Cookie 具备 `Secure` 与受控 `SameSite` 策略
- [ ] `/web/api/v1/gpt/queryAgentStreamIncr` 会解析当前 `visitorId`，并在内部 `/AutoAgent` 转发时继续透传
- [ ] 前端继续自生成 `sessionId`，后端首次使用时自动完成 `sessionId -> visitorId` 绑定
- [ ] `conversation sessions list/detail` 只返回当前匿名访客自己的会话
- [ ] 知道别人的 `sessionId` 也不能回放对方会话历史
- [ ] 文件上传入口在 `sessionId` 归属校验失败时明确拒绝
- [ ] `DialogueRun` 和 `DialogueSession` 都能记录 `visitorId`
- [ ] `AiAgentController` 和 `ReactorController` 不再手动 `newScheduledThreadPool(...)`
- [ ] 主链路不再依赖 `ThreadUtil.execute(...)`
- [ ] `LLM.java`、`VectorService.java`、配置加载策略的 `supplyAsync(...)` 都显式指定受控执行器
- [ ] 执行器拒绝时返回“系统繁忙”，而不是静默吞任务
- [ ] `application-dev.yml` 与 `application-prod.yml` 都已补齐匿名访客和执行器配置

## Out of Scope

- 后端 `sessionId` 串行执行保护
- 多实例部署与分布式会话路由
- 匿名访客升级为正式账号
- Redis / MQ / 作业中心
- UI 侧会话 ID 生成逻辑重构
