# Reactor DDD Phase 1 Boundary Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不重写 `reactor` 运行时内核、也不提前触碰 `tool-output / session-memory / workspace-image` 持久化类型的前提下，把 `domain/agent/reactor` 中放错层的 HTTP 入口与低风险 Spring 装配类迁回既有模块边界，并为 `execution ledger` 建立第一条稳定的 repository seam，让 `domain` 服务先摆脱对 ledger DAO 的直接依赖。

**Architecture:** 本阶段采用“先止血、再抽缝”的策略。`trigger` 接管 `ReactorController` 与 `DataAgentController` 的全部现有路由；`app` 接管回放和数据初始化的低风险 Spring 装配；`domain` 新增 `IExecutionLedgerWriteRepository / IExecutionLedgerReadRepository` 端口，`infrastructure` 提供 adapter 实现。为了控制范围，Phase 1 的 repository adapter 继续复用当前 `domain.reactor.mapper` 与 `domain.reactor.entity` 作为过渡型 persistence contract；`SessionContextMemoryServiceImpl`、`ToolOutputWriterImpl / ToolOutputReaderImpl`、`WorkspaceImageGenerationServiceImpl` 与 `ReactorConfig` 的进一步收口延后到第二阶段。

**Tech Stack:** Java 17, Spring Boot 3.4.3, MyBatis / MyBatis-Plus, Maven multi-module, JUnit 4, Mockito

---

## 当前基线（2026-05-04）

### 已确认的真实问题

1. `domain/agent/reactor` 下面仍然存在放错层的 HTTP 入口：
   - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/controller/ReactorController.java`
   - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/controller/DataAgentController.java`
2. `domain` 模块内仍然承载 Spring 装配类：
   - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReplayProjectorAutoConfiguration.java`
   - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/data/Es7HighLevelClientConfig.java`
   - `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/DataAgentInitRunner.java`
3. `AgentExecutionRecorderImpl` 仍直接依赖 DAO：
   - `IDialogueRunLedgerDao`
   - `IDialogueSessionLedgerDao`
   - `ILlmInvocationLedgerDao`
   - `IToolInvocationLedgerDao`
   - `IArtifactLedgerDao`
4. `ExecutionLedgerQueryServiceImpl` 也通过 DAO 直接拼装读模型，导致 `domain` 和 MyBatis 绑定。
5. `infrastructure` 已经存在工具输出读写实现，但仍反向依赖 `domain.reactor.mapper`，说明 persistence 边界还没有完全收干净。

### 本阶段明确延后

1. `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java`
   - 当前有 30+ 直接依赖点，并且存在 `ApplicationContext.getBean(ReactorConfig.class)` 的运行期拉取。
   - 本阶段不做物理迁移，只把它标记为“过渡态共享配置契约”，第二阶段再通过 `SettingsProvider` / `Port` 收口。
2. `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java`
   - 它是运行时主循环、工具执行与账本挂接的核心，不在本阶段调整。
3. `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/data/**`
   - 当前已经演化成独立子域，单独规划更合理。
4. `ChatModelInfoMapper / ChatModelSchemaMapper` 及其 PO / Service
   - 这部分与 ledger 收口无直接关系，本阶段不扩散。
5. `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/SessionContextMemoryServiceImpl.java`
   - 当前直接依赖 `ILlmInvocationLedgerDao / IToolInvocationLedgerDao / IArtifactLedgerDao` 与 ledger 实体，本阶段不改。
6. `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputWriterImpl.java`
   `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputReaderImpl.java`
   `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/WorkspaceImageGenerationServiceImpl.java`
   - 当前仍直接依赖 `IToolOutput*Dao / IArtifactLedgerDao`，这部分属于第二阶段的 tool-output seam，不纳入本期。
7. ledger DAO / 实体的物理迁移、`mybatis/mapper/*.xml` namespace 切换
   - 本阶段先建立 repository seam，不做全量持久化类型迁移。

---

## 本阶段完成标准

1. `ReactorController` 与 `DataAgentController` 的**全部现有路由**在 `trigger` 模块完成等价迁移后，`domain` 中旧 controller 才允许删除。
2. `ReplayProjectorAutoConfiguration`、`Es7HighLevelClientConfig`、`DataAgentInitRunner` 不再位于 `domain` 模块。
3. `AgentExecutionRecorderImpl` 与 `ExecutionLedgerQueryServiceImpl` 不再直接依赖任何 ledger `*Dao` / `@Mapper` 类型。
4. `ExecutionLedgerFixtureFactory` 及其依赖测试改为通过 repository seam 装配，不再直接 new 旧 service 构造器。
5. `SessionContextMemoryServiceImpl`、`ToolOutputWriterImpl / ToolOutputReaderImpl`、`WorkspaceImageGenerationServiceImpl` 在本阶段保持现状且回归通过。

---

## 文件结构映射

### 新建文件

| 文件路径 | 职责 |
| --- | --- |
| `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/ReactorController.java` | 承接原 `domain` 中 `/1/**` legacy Reactor HTTP 入口 |
| `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/DataAgentController.java` | 承接原 `domain` 中 `/data/**` HTTP 入口 |
| `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/ReplayProjectorAutoConfiguration.java` | 承接回放 Bean 装配 |
| `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/data/Es7HighLevelClientConfig.java` | 承接 ES7 客户端装配 |
| `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/data/DataAgentInitRunner.java` | 承接数据初始化 Runner |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/adapter/repository/IExecutionLedgerWriteRepository.java` | ledger 写侧仓储契约 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/adapter/repository/IExecutionLedgerReadRepository.java` | ledger 读侧仓储契约 |
| `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/repository/ExecutionLedgerWriteRepository.java` | ledger 写侧实现 |
| `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/repository/ExecutionLedgerReadRepository.java` | ledger 读侧实现 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorHttpControllerTest.java` | legacy Reactor / DataAgent 入口迁移回归 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerBoundaryTest.java` | 验证 `domain` 服务不再直依赖 DAO |

### 修改文件

| 文件路径 | 修改内容 |
| --- | --- |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java` | 改为依赖写侧仓储契约 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java` | 改为依赖读侧仓储契约 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java` | 让测试夹具改经 repository seam 装配 ledger service |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorBeanTopologyTest.java` | 锁定装配位置与 bean 图不回归 |
| `CLAUDE.md` | 更新 `reactor` 分层说明 |
| `ai-agent-station-study-domain/CLAUDE.md` | 更新 domain 模块职责说明 |

### 删除文件

| 文件路径 | 原因 |
| --- | --- |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/controller/ReactorController.java` | HTTP 入口移至 trigger |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/controller/DataAgentController.java` | HTTP 入口移至 trigger |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReplayProjectorAutoConfiguration.java` | Spring 装配移至 app |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/data/Es7HighLevelClientConfig.java` | Spring 装配移至 app |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/DataAgentInitRunner.java` | Spring Runner 移至 app |

### 本阶段显式保留

| 文件路径 | 原因 |
| --- | --- |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueRunLedgerDao.java` 及同组 ledger DAO | 作为 Phase 1 过渡态 persistence contract，供新的 infrastructure repository adapter 复用 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueRun.java` 及同组 ledger 实体 | 当前仍被 `SessionContextMemoryServiceImpl`、`ToolOutputReaderImpl` 等链路复用，本阶段不物理迁移 |
| `ai-agent-station-study-app/src/main/resources/mybatis/mapper/*ledger_mapper.xml` | Phase 1 不改 namespace / resultMap type，避免扩散到全量持久化迁移 |
| `SessionContextMemoryServiceImpl`、`ToolOutputWriterImpl / ToolOutputReaderImpl`、`WorkspaceImageGenerationServiceImpl` | 明确延后到 Phase 2 的 session-memory / tool-output seam |

---

## Task 1: 提取 legacy HTTP 入口到 trigger 模块

**Files:**
- Create: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/ReactorController.java`
- Create: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/DataAgentController.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/controller/ReactorController.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/controller/DataAgentController.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorHttpControllerTest.java`

- [ ] **Step 1: 先写失败测试，锁定 legacy 全量路由集合与各 endpoint 的轻量委派行为**

```java
@Test
public void shouldExposeAllLegacyRoutesFromTriggerControllers() {
    Assert.assertEquals(Set.of(
            "POST /1/AutoAgent",
            "ANY /1/web/health",
            "ANY /1/web/api/v1/gpt/queryAgentStreamIncr"
    ), collectRoutes(org.wwz.ai.trigger.http.agent.ReactorController.class));

    Assert.assertEquals(Set.of(
            "POST /data/queryModelInfo",
            "POST /data/vectorRecall",
            "POST /data/esRecall",
            "POST /data/chatQuery",
            "POST /data/apiChatQuery",
            "POST /data/testQuery",
            "POST /data/getNl2SqlReq",
            "GET /data/allModels",
            "GET /data/previewData"
    ), collectRoutes(org.wwz.ai.trigger.http.agent.DataAgentController.class));
}

@Test
public void shouldKeepRepresentativeDelegationContractsAfterMove() throws Exception {
    IGptProcessService gptProcessService = Mockito.mock(IGptProcessService.class);
    DataAgentService dataAgentService = Mockito.mock(DataAgentService.class);
    ChatModelInfoService chatModelInfoService = Mockito.mock(ChatModelInfoService.class);
    SchemaRecallService schemaRecallService = Mockito.mock(SchemaRecallService.class);
    SseEmitter emitter = new SseEmitter();
    List<ChatQueryData> apiResult = List.of(new ChatQueryData());
    List<Map<String, Object>> vectorResult = List.of(Map.of("column", "city"));
    List<Map<String, Object>> esResult = List.of(Map.of("value", "hangzhou"));
    List<Object> modelResult = List.of("model-a");
    NL2SQLReq nl2sqlReq = new NL2SQLReq();
    Object testQueryResult = Map.of("sql", "select 1");
    Object previewResult = List.of(Map.of("id", 1));

    Mockito.when(gptProcessService.queryMultiAgentIncrStream(Mockito.any(GptQueryReq.class))).thenReturn(emitter);
    Mockito.when(dataAgentService.webChatQueryData(Mockito.any(DataAgentChatReq.class)))
            .thenReturn(emitter);
    Mockito.when(dataAgentService.apiChatQueryData(Mockito.any(DataAgentChatReq.class))).thenReturn(apiResult);
    Mockito.when(dataAgentService.queryAllSchemaNl2SqlReq()).thenReturn(nl2sqlReq);
    Mockito.when(dataAgentService.testQuery(Mockito.any(DataAgentChatReq.class))).thenReturn(testQueryResult);
    Mockito.when(dataAgentService.getNl2SqlReq(Mockito.anyString())).thenReturn(nl2sqlReq);
    Mockito.when(schemaRecallService.vectorRecall(Mockito.any(ColumnVectorRecallReq.class))).thenReturn(vectorResult);
    Mockito.when(schemaRecallService.esValueRecall(Mockito.any(ColumnEsRecallReq.class))).thenReturn(esResult);
    Mockito.when(chatModelInfoService.queryAllModelsWithSchema()).thenReturn(modelResult);
    Mockito.when(chatModelInfoService.previewData("model-a")).thenReturn(previewResult);

    org.wwz.ai.trigger.http.agent.ReactorController reactorController =
            new org.wwz.ai.trigger.http.agent.ReactorController();
    ReflectionTestUtils.setField(reactorController, "gptProcessService", gptProcessService);

    org.wwz.ai.trigger.http.agent.DataAgentController dataController =
            new org.wwz.ai.trigger.http.agent.DataAgentController();
    ReflectionTestUtils.setField(dataController, "dataAgentService", dataAgentService);
    ReflectionTestUtils.setField(dataController, "schemaRecallService", schemaRecallService);
    ReflectionTestUtils.setField(dataController, "chatModelInfoService", chatModelInfoService);

    Assert.assertSame(emitter, reactorController.queryAgentStreamIncr(new GptQueryReq()));
    Assert.assertEquals("ok", reactorController.health().getBody());
    Assert.assertSame(emitter, dataController.chatQuery(new DataAgentChatReq()));
    Assert.assertSame(nl2sqlReq, dataController.queryModelInfo(new JSONObject()));
    Assert.assertSame(apiResult, dataController.apiChatQuery(new DataAgentChatReq()));
    Assert.assertSame(vectorResult, dataController.vectorRecall(new ColumnVectorRecallReq()));
    Assert.assertSame(esResult, dataController.esRecall(new ColumnEsRecallReq()));
    Assert.assertSame(testQueryResult, dataController.testQuery(new DataAgentChatReq()));
    DataAgentChatReq req = new DataAgentChatReq();
    req.setContent("生成 SQL");
    Assert.assertSame(nl2sqlReq, dataController.getNl2SqlReq(req));
    Assert.assertEquals(200, dataController.allModels().get("code"));
    Assert.assertEquals(previewResult, dataController.previewData("model-a").get("data"));
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ReactorHttpControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL，提示 `trigger.http.agent.ReactorController` / `DataAgentController` 尚不存在，或全量路由集合断言不成立。

- [ ] **Step 3: 在 trigger 模块创建新 controller，逐个迁入现有 endpoint，保持 URL、参数与返回类型完全不变**

```java
@Slf4j
@RestController
@RequestMapping("/1")
public class ReactorController {

    @Resource
    private IGptProcessService gptProcessService;

    @Resource
    private IAgentDispatchService agentDispatchService;

    @PostMapping("/AutoAgent")
    public SseEmitter AutoAgent(@RequestBody AgentRequest request) throws UnsupportedEncodingException {
        // 保留原有心跳、SSE monitor 与 ExecuteCommandEntity 组装逻辑
    }

    @RequestMapping(value = "/web/health", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    @RequestMapping(value = "/web/api/v1/gpt/queryAgentStreamIncr", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryAgentStreamIncr(@RequestBody GptQueryReq params) {
        return gptProcessService.queryMultiAgentIncrStream(params);
    }
}
```

```java
@Slf4j
@RestController
@RequestMapping("/data")
public class DataAgentController {

    @Autowired
    private DataAgentService dataAgentService;

    @Autowired
    private SchemaRecallService schemaRecallService;

    @Autowired
    private ChatModelInfoService chatModelInfoService;

    @PostMapping(value = "chatQuery", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatQuery(@RequestBody DataAgentChatReq req) throws Exception {
        return dataAgentService.webChatQueryData(req);
    }

    @PostMapping(value = "queryModelInfo")
    public NL2SQLReq queryModelInfo(@RequestBody JSONObject req) {
        return dataAgentService.queryAllSchemaNl2SqlReq();
    }

    @PostMapping(value = "vectorRecall")
    public List<Map<String, Object>> vectorRecall(@RequestBody ColumnVectorRecallReq req) {
        return schemaRecallService.vectorRecall(req);
    }

    @PostMapping(value = "esRecall")
    public List<Map<String, Object>> esRecall(@RequestBody ColumnEsRecallReq req) throws IOException {
        return schemaRecallService.esValueRecall(req);
    }

    @PostMapping(value = "apiChatQuery")
    public List<ChatQueryData> apiChatQuery(@RequestBody DataAgentChatReq req) {
        return dataAgentService.apiChatQueryData(req);
    }

    @PostMapping(value = "testQuery")
    public Object testQuery(@RequestBody DataAgentChatReq req) throws Exception {
        return dataAgentService.testQuery(req);
    }

    @PostMapping(value = "getNl2SqlReq")
    public NL2SQLReq getNl2SqlReq(@RequestBody DataAgentChatReq req) throws Exception {
        return dataAgentService.getNl2SqlReq(req.getContent());
    }

    @GetMapping(value = "allModels")
    public Map<String, Object> allModels() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", chatModelInfoService.queryAllModelsWithSchema());
        return result;
    }

    @GetMapping(value = "previewData")
    public Map<String, Object> previewData(@RequestParam("modelCode") String modelCode) throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", chatModelInfoService.previewData(modelCode));
        return result;
    }
}
```

- [ ] **Step 4: 仅在 `ReactorHttpControllerTest` 全量通过后删除旧 controller**

```java
// 删除条件：
// 1) Reactor 全量路由集合与旧类完全一致；
// 2) DataAgent 全量路由集合与旧类完全一致；
// 3) 代表性委派行为不变。
// 满足后再删 domain 下 controller，避免出现“删旧入口但未补齐新入口”的 API 回归。
```

- [ ] **Step 5: 跑控制器回归并提交**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ReactorHttpControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

```bash
git add ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/ReactorController.java
git add ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/DataAgentController.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorHttpControllerTest.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/controller/ReactorController.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/controller/DataAgentController.java
git commit -m "refactor: move reactor http entrypoints to trigger"
```

---

## Task 2: 提取低风险 Spring 装配类到 app 模块

**Files:**
- Create: `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/ReplayProjectorAutoConfiguration.java`
- Create: `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/data/Es7HighLevelClientConfig.java`
- Create: `ai-agent-station-study-app/src/main/java/org/wwz/ai/config/data/DataAgentInitRunner.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReplayProjectorAutoConfiguration.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/data/Es7HighLevelClientConfig.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/DataAgentInitRunner.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorBeanTopologyTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/DataAgentInitRunnerRefreshTest.java`

- [ ] **Step 1: 先写失败测试，锁定这些 Bean 不再从 domain.config 包暴露**

```java
@Test
public void shouldLoadReplayBeansFromAppConfigPackage() {
    Assert.assertTrue(org.wwz.ai.config.reactor.ReplayProjectorAutoConfiguration.class
            .getPackageName()
            .startsWith("org.wwz.ai.config"));
}

@Test
public void shouldKeepDomainFreeFromReplayAutoConfiguration() {
    Assert.assertFalse(new File(
            "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReplayProjectorAutoConfiguration.java"
    ).exists());
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ReplayProjectorBeanTopologyTest,DataAgentInitRunnerRefreshTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL，提示 app 配置类不存在，或旧 domain 配置文件仍然存在。

- [ ] **Step 3: 把装配类原样迁到 app/config 下，只迁位置，不改变装配结果**

```java
@Configuration
public class ReplayProjectorAutoConfiguration {

    @Bean
    public ToolInvocationProjectorRegistry toolInvocationProjectorRegistry(
            List<ToolInvocationProjector> projectors,
            DefaultToolInvocationProjector defaultProjector) {
        return new ToolInvocationProjectorRegistry(projectors, defaultProjector);
    }

    @Bean
    public ReplayProjector replayProjector(ToolInvocationProjectorRegistry registry) {
        return new ReplayProjector(registry);
    }
}
```

```java
@Configuration
public class Es7HighLevelClientConfig {
    // 保持现有 Bean 定义与参数绑定逻辑不变，只迁出 domain 模块
}
```

```java
@Component
public class DataAgentInitRunner implements CommandLineRunner {
    // 保持现有初始化逻辑不变，只迁出 domain 模块
}
```

- [ ] **Step 4: 明确本阶段不迁 `ReactorConfig`，只在计划内补注释和测试保护**

```java
// ReactorConfig 暂留 domain.config，作为高耦合过渡配置契约。
// 本阶段不挪包，不改 bean name，不改 getBean(ReactorConfig.class) 调用点。
```

- [ ] **Step 5: 跑装配回归并提交**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ReplayProjectorBeanTopologyTest,DataAgentInitRunnerRefreshTest,ConfigProfileLoadingTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

```bash
git add ai-agent-station-study-app/src/main/java/org/wwz/ai/config/reactor/ReplayProjectorAutoConfiguration.java
git add ai-agent-station-study-app/src/main/java/org/wwz/ai/config/data/Es7HighLevelClientConfig.java
git add ai-agent-station-study-app/src/main/java/org/wwz/ai/config/data/DataAgentInitRunner.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorBeanTopologyTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/dataagent/DataAgentInitRunnerRefreshTest.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReplayProjectorAutoConfiguration.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/data/Es7HighLevelClientConfig.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/DataAgentInitRunner.java
git commit -m "refactor: move reactor spring wiring to app module"
```

---

## Task 3: 为 execution ledger 建立 domain 仓储契约，去掉 service 对 DAO 的直接依赖

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/adapter/repository/IExecutionLedgerWriteRepository.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/adapter/repository/IExecutionLedgerReadRepository.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerBoundaryTest.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutionLedgerRepositoryTest.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java`

- [ ] **Step 1: 先写失败测试，锁定 domain service 中不再出现 `*Dao` 字段**

```java
@Test
public void shouldRemoveDaoFieldsFromDomainLedgerServices() {
    Assert.assertFalse(Arrays.stream(AgentExecutionRecorderImpl.class.getDeclaredFields())
            .anyMatch(field -> field.getType().getSimpleName().endsWith("Dao")));
    Assert.assertFalse(Arrays.stream(ExecutionLedgerQueryServiceImpl.class.getDeclaredFields())
            .anyMatch(field -> field.getType().getSimpleName().endsWith("Dao")));
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ExecutionLedgerBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL，当前两个 service 仍直接持有 DAO 字段。

- [ ] **Step 3: 在 domain 层定义读写仓储契约，只暴露当前 service 需要的领域级方法**

```java
public interface IExecutionLedgerWriteRepository {

    Long createRun(DialogueRunStartRecord record);

    void finishRun(DialogueRunFinishRecord record);

    Long createLlmInvocation(LlmInvocationStartRecord record);

    void finishLlmInvocation(LlmInvocationFinishRecord record);

    Map<String, Long> createToolInvocations(ToolInvocationBatchStartRecord record);

    void finishToolInvocation(ToolInvocationFinishRecord record);

    void recordArtifacts(List<ArtifactRecordCommand> records);
}
```

```java
public interface IExecutionLedgerReadRepository {

    ExecutionRunDetail queryRunDetail(String requestId);

    List<ToolInvocationView> queryRecentToolInvocations(String toolName, int limit);

    List<DialogueRunView> queryRecentSessionRuns(String sessionId, int limit);

    List<DialogueRunView> querySessionRuns(String sessionId);

    DialogueSessionView querySession(String sessionId);

    List<DialogueSessionView> queryRecentSessions(int limit);
}
```

- [ ] **Step 4: 修改 domain service 只依赖新契约，并同步改测试夹具经 repository seam 装配**

```java
@Service
@RequiredArgsConstructor
public class AgentExecutionRecorderImpl implements AgentExecutionRecorder {

    private final IExecutionLedgerWriteRepository writeRepository;
    private final ToolOutputWriter toolOutputWriter;

    @Override
    public Long createRun(DialogueRunStartRecord record) {
        return writeRepository.createRun(record);
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class ExecutionLedgerQueryServiceImpl implements ExecutionLedgerQueryService {

    private final IExecutionLedgerReadRepository readRepository;

    @Override
    public ExecutionRunDetail queryRunDetail(String requestId) {
        return readRepository.queryRunDetail(requestId);
    }
}
```

```java
ExecutionLedgerQueryServiceImpl queryService = new ExecutionLedgerQueryServiceImpl(
        new InMemoryExecutionLedgerReadRepository(runDao, sessionDao, llmDao, toolDao, artifactDao, toolOutputReader)
);
AgentExecutionRecorder recorder = new AgentExecutionRecorderImpl(
        new InMemoryExecutionLedgerWriteRepository(runDao, sessionDao, llmDao, toolDao, artifactDao),
        toolOutputWriter
);
```

```java
// ExecutionLedgerFixtureFactory 中新增两个仅测试使用的适配器：
// 1) InMemoryExecutionLedgerWriteRepository
// 2) InMemoryExecutionLedgerReadRepository
// 它们继续复用现有 in-memory DAO，目的只有一个：
// 在 Task 3 改掉 service 构造器后，先让全部 ledger 回归测试继续可编译、可运行。
```

- [ ] **Step 5: 跑边界测试并提交**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ExecutionLedgerBoundaryTest,AgentExecutionLedgerRepositoryTest,ExecutionLedgerQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/adapter/repository/IExecutionLedgerWriteRepository.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/adapter/repository/IExecutionLedgerReadRepository.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerBoundaryTest.java
git commit -m "refactor: add ledger repository ports in domain"
```

---

## Task 4: 在 infrastructure 落地 execution ledger repository adapter，继续复用现有 DAO / 实体

**Files:**
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/repository/ExecutionLedgerWriteRepository.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/repository/ExecutionLedgerReadRepository.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutionLedgerRepositoryTest.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java`

- [ ] **Step 1: 先写失败测试，锁定 production repository adapter 已经落在 infrastructure 包下**

```java
@Test
public void shouldPlaceLedgerRepositoriesInsideInfrastructurePackage() {
    Assert.assertEquals("org.wwz.ai.infrastructure.repository",
            ExecutionLedgerWriteRepository.class.getPackageName());
    Assert.assertEquals("org.wwz.ai.infrastructure.repository",
            ExecutionLedgerReadRepository.class.getPackageName());
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=AgentExecutionLedgerRepositoryTest,ExecutionLedgerQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL，`ExecutionLedgerWriteRepository` / `ExecutionLedgerReadRepository` 尚不存在。

- [ ] **Step 3: 在 infrastructure 实现 read / write repository，但继续复用现有 `domain.reactor.mapper` 与 `domain.reactor.entity`**

```java
@Repository
@RequiredArgsConstructor
public class ExecutionLedgerWriteRepository implements IExecutionLedgerWriteRepository {

    private final IDialogueRunLedgerDao dialogueRunLedgerDao;
    private final IDialogueSessionLedgerDao dialogueSessionLedgerDao;
    private final ILlmInvocationLedgerDao llmInvocationLedgerDao;
    private final IToolInvocationLedgerDao toolInvocationLedgerDao;
    private final IArtifactLedgerDao artifactLedgerDao;

    @Override
    public Long createRun(DialogueRunStartRecord record) {
        // 把原先 AgentExecutionRecorderImpl 中直接操作 DAO 的逻辑下沉到 adapter
    }
}
```

```java
@Repository
@RequiredArgsConstructor
public class ExecutionLedgerReadRepository implements IExecutionLedgerReadRepository {

    private final IDialogueRunLedgerDao dialogueRunLedgerDao;
    private final IDialogueSessionLedgerDao dialogueSessionLedgerDao;
    private final ILlmInvocationLedgerDao llmInvocationLedgerDao;
    private final IToolInvocationLedgerDao toolInvocationLedgerDao;
    private final IArtifactLedgerDao artifactLedgerDao;
    private final ToolOutputReader toolOutputReader;

    @Override
    public ExecutionRunDetail queryRunDetail(String requestId) {
        // 把原先 ExecutionLedgerQueryServiceImpl 中依赖 DAO 的查询拼装逻辑下沉到 adapter
    }
}
```

```java
// Phase 1 禁止动作：
// 1) 不移动 IDialogue*Dao / IArtifactLedgerDao 的 Java 文件；
// 2) 不删除 DialogueRun / DialogueSession / LlmInvocation / ToolInvocation / ArtifactRecord；
// 3) 不改 mybatis ledger mapper 的 namespace / resultMap type。
// 一旦开始改这三类内容，说明范围已经从 “ledger seam” 膨胀到 Phase 2。
```

- [ ] **Step 4: 把测试夹具切换成 production repository adapter，确保现有 ledger 回归真的覆盖 adapter**

```java
IExecutionLedgerWriteRepository writeRepository = new ExecutionLedgerWriteRepository(
        runDao, sessionDao, llmDao, toolDao, artifactDao
);
IExecutionLedgerReadRepository readRepository = new ExecutionLedgerReadRepository(
        runDao, sessionDao, llmDao, toolDao, artifactDao, toolOutputReader
);
ExecutionLedgerQueryServiceImpl queryService = new ExecutionLedgerQueryServiceImpl(readRepository);
AgentExecutionRecorder recorder = new AgentExecutionRecorderImpl(writeRepository, toolOutputWriter);
```

- [ ] **Step 5: 跑 ledger 回归并提交**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ExecutionLedgerBoundaryTest,AgentExecutionLedgerRepositoryTest,ExecutionLedgerQueryServiceTest,ConversationHistoryControllerTest,ToolStructuredOutputWriterTest,ToolStructuredOutputReaderTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

```bash
git add ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/repository/ExecutionLedgerWriteRepository.java
git add ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/repository/ExecutionLedgerReadRepository.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java
git commit -m "refactor: add execution ledger repository adapters"
```

---

## Task 5: 完整回归并更新项目文档，锁定第一阶段边界

**Files:**
- Modify: `CLAUDE.md`
- Modify: `ai-agent-station-study-domain/CLAUDE.md`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorHttpControllerTest.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorBeanTopologyTest.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerBoundaryTest.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutionLedgerRepositoryTest.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java`

- [ ] **Step 1: 更新文档，明确第一阶段后的边界**

```md
- `trigger`：承接所有 HTTP / job / listener 入口；`reactor` legacy controller 也在这一层。
- `domain`：保留 Agent Runtime、领域服务、仓储契约，以及过渡态 ledger mapper / entity persistence contract；不再承接 controller 与低风险 Spring 装配。
- `infrastructure`：承接 execution-ledger repository adapter 与外部网关；tool-output seam 和持久化类型物理迁移延后到 Phase 2。
- `app`：承接 Spring Boot 装配、第三方客户端 Bean、初始化 Runner。
```

- [ ] **Step 2: 跑后端聚焦回归**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ReactorHttpControllerTest,ReplayProjectorBeanTopologyTest,ExecutionLedgerBoundaryTest,AgentExecutionLedgerRepositoryTest,ExecutionLedgerQueryServiceTest,ConversationHistoryControllerTest,ToolStructuredOutputWriterTest,ToolStructuredOutputReaderTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

- [ ] **Step 3: 跑模块编译回归**

Run:

```bash
mvn clean compile -pl ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-trigger,ai-agent-station-study-app -am
```

Expected: BUILD SUCCESS

- [ ] **Step 4: 人工检查 domain 目录残留**

Run:

```bash
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor" | rg "controller|config/ReplayProjectorAutoConfiguration|config/data/Es7HighLevelClientConfig|config/DataAgentInitRunner"
```

Expected: 无输出；若仍有输出，说明 HTTP / 低风险装配边界还没收干净。

Run:

```bash
rg "private final .*Dao" ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java
```

Expected: 无输出；若仍有输出，说明 repository seam 没有真正建立。

Run:

```bash
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor" | rg "mapper/IDialogue|mapper/ILlm|mapper/IToolInvocation|mapper/IArtifact|entity/DialogueRun|entity/DialogueSession|entity/LlmInvocation|entity/ToolInvocation|entity/ArtifactRecord"
```

Expected: 这些过渡态 DAO / 实体仍然存在；若已经被删除，说明实现范围越界到了 Phase 2。

- [ ] **Step 5: 最终提交**

```bash
git add CLAUDE.md
git add ai-agent-station-study-domain/CLAUDE.md
git commit -m "docs: lock reactor phase1 ddd boundaries"
```

---

## 自我审查

### 1. 需求覆盖

| 目标 | 对应任务 |
| --- | --- |
| 把 HTTP 入口从 domain 移走 | Task 1 |
| 把低风险 Spring 装配类从 domain 移走 | Task 2 |
| 让 domain service 不再直依赖 DAO | Task 3 |
| 为 execution-ledger 建立 infrastructure repository adapter seam | Task 4 |
| 锁定文档与回归边界 | Task 5 |

### 2. Placeholder 扫描

- 没有 `TODO / TBD / later`
- 没有“自行处理边界情况”这类空描述
- 每个任务都给了真实路径、命令和验收标准

### 3. 类型一致性

- `domain` 只出现 `IExecutionLedgerWriteRepository / IExecutionLedgerReadRepository`
- `infrastructure` 在本阶段只新增 `ExecutionLedger*Repository` adapter
- `domain.reactor.mapper` 与 `domain.reactor.entity` 在本阶段仍作为过渡态 persistence contract 保留
- `trigger` 承接 `ReactorController / DataAgentController`
- `app` 承接 `ReplayProjectorAutoConfiguration / Es7HighLevelClientConfig / DataAgentInitRunner`

### 4. 风险提醒

1. `ReactorConfig` 本阶段不物理迁移，执行时不要把它混进同一批改动，否则范围会爆。
2. `BaseAgent`、`LLM`、`Tool` 运行链不得在第一阶段顺手重构；任何“顺手优化”都可能引发不可控回归。
3. 如果实现过程中开始移动 `IDialogue*Dao`、`IArtifactLedgerDao`、`DialogueRun`、`ArtifactRecord` 或 `mybatis/mapper/*ledger*.xml`，就已经越过了本计划的 Phase 1 边界。
4. `ExecutionLedgerFixtureFactory` 必须和 service 构造器一起演进；漏掉它，`AgentExecutionLedgerRepositoryTest` / `ExecutionLedgerQueryServiceTest` 会直接编译失败。
5. `ReactorHttpControllerTest` 必须覆盖 `/1/AutoAgent`、`/1/web/health`、`/1/web/api/v1/gpt/queryAgentStreamIncr` 以及 `/data/**` 的 9 个现有 endpoint；否则仍然抓不住 API 丢失回归。
