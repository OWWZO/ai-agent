# Reactor DDD Phase 1 Boundary Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不重写 `reactor` 运行时内核的前提下，把 `domain/agent/reactor` 中放错层的 HTTP 入口、Spring 装配类与 ledger 持久化依赖收口回现有 DDD 模块边界，为第二阶段按 `runtime / ledger / data-agent` 子域继续拆分打基础。

**Architecture:** 本阶段采用“先止血、再抽口”的策略。`trigger` 接管 `ReactorController` 与 `DataAgentController` 等 HTTP 入口，`app` 接管回放与数据初始化的 Spring 装配，`domain` 只保留运行时编排、领域模型与仓储契约，`infrastructure` 接管 ledger DAO / PO / 仓储实现。`BaseAgent`、`agent/tool/*`、`agent/llm/*`、`reactor/data/*` 与 `ReactorConfig` 的彻底去 Spring 化不在本阶段落地，避免运行时主链路回归。

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
5. `infrastructure` 已经存在工具输出读写实现，但仍反向依赖 `domain.reactor.mapper`，说明边界未闭合。

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

---

## 本阶段完成标准

1. `domain/agent/reactor/controller/**` 目录被删除，HTTP 入口全部位于 `trigger` 模块。
2. `ReplayProjectorAutoConfiguration`、`Es7HighLevelClientConfig`、`DataAgentInitRunner` 不再位于 `domain` 模块。
3. `AgentExecutionRecorderImpl` 与 `ExecutionLedgerQueryServiceImpl` 不再直接依赖任何 `*Dao` / `@Mapper` 类型。
4. ledger 相关 `DAO + PO + Mapper XML namespace` 全部归入 `infrastructure`，`domain` 只依赖仓储接口。
5. 现有回放、会话历史、structured output 回归测试保持通过。

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
| `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/IDialogueRunLedgerDao.java` | ledger run DAO |
| `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/IDialogueSessionLedgerDao.java` | ledger session DAO |
| `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/ILlmInvocationLedgerDao.java` | ledger llm DAO |
| `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/IToolInvocationLedgerDao.java` | ledger tool DAO |
| `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/IArtifactLedgerDao.java` | ledger artifact DAO |
| `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/DialogueRunPO.java` | `ai_agent_dialogue_run` PO |
| `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/DialogueSessionPO.java` | `ai_agent_dialogue_session` PO |
| `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/LlmInvocationPO.java` | `ai_agent_llm_invocation` PO |
| `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ToolInvocationPO.java` | `ai_agent_tool_invocation` PO |
| `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ArtifactRecordPO.java` | `ai_agent_artifact` PO |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorHttpControllerTest.java` | legacy Reactor / DataAgent 入口迁移回归 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerBoundaryTest.java` | 验证 `domain` 服务不再直依赖 DAO |

### 修改文件

| 文件路径 | 修改内容 |
| --- | --- |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java` | 改为依赖写侧仓储契约 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java` | 改为依赖读侧仓储契约 |
| `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml` | namespace 切到 `infrastructure.dao` |
| `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_session_ledger_mapper.xml` | namespace 切到 `infrastructure.dao` |
| `ai-agent-station-study-app/src/main/resources/mybatis/mapper/llm_invocation_ledger_mapper.xml` | namespace 切到 `infrastructure.dao` |
| `ai-agent-station-study-app/src/main/resources/mybatis/mapper/tool_invocation_ledger_mapper.xml` | namespace 切到 `infrastructure.dao` |
| `ai-agent-station-study-app/src/main/resources/mybatis/mapper/artifact_ledger_mapper.xml` | namespace 切到 `infrastructure.dao` |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutionLedgerRepositoryTest.java` | 对齐新的 repository 实现入口 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java` | 对齐新的 read repository 链路 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java` | 锁定会话历史接口不回归 |
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
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueRunLedgerDao.java` | DAO 归入 infrastructure |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueSessionLedgerDao.java` | DAO 归入 infrastructure |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ILlmInvocationLedgerDao.java` | DAO 归入 infrastructure |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IToolInvocationLedgerDao.java` | DAO 归入 infrastructure |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IArtifactLedgerDao.java` | DAO 归入 infrastructure |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueRun.java` | 持久化 PO 归入 infrastructure |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueSession.java` | 持久化 PO 归入 infrastructure |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/LlmInvocation.java` | 持久化 PO 归入 infrastructure |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/ToolInvocation.java` | 持久化 PO 归入 infrastructure |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/ArtifactRecord.java` | 持久化 PO 归入 infrastructure |

---

## Task 1: 提取 legacy HTTP 入口到 trigger 模块

**Files:**
- Create: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/ReactorController.java`
- Create: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/DataAgentController.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/controller/ReactorController.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/controller/DataAgentController.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactorHttpControllerTest.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java`

- [ ] **Step 1: 先写失败测试，锁定 legacy 路由和委派行为**

```java
@Test
public void shouldKeepLegacyReactorPrefixAfterMovingControllerToTrigger() {
    RequestMapping mapping = AnnotationUtils.findAnnotation(
            org.wwz.ai.trigger.http.agent.ReactorController.class,
            RequestMapping.class
    );
    Assert.assertNotNull(mapping);
    Assert.assertArrayEquals(new String[]{"/1"}, mapping.value());
}

@Test
public void shouldDelegateDataChatQueryFromTriggerLayer() throws Exception {
    DataAgentService dataAgentService = Mockito.mock(DataAgentService.class);
    SseEmitter emitter = new SseEmitter();
    Mockito.when(dataAgentService.webChatQueryData(Mockito.any(DataAgentChatReq.class)))
            .thenReturn(emitter);

    org.wwz.ai.trigger.http.agent.DataAgentController controller =
            new org.wwz.ai.trigger.http.agent.DataAgentController();
    ReflectionTestUtils.setField(controller, "dataAgentService", dataAgentService);

    Assert.assertSame(emitter, controller.chatQuery(new DataAgentChatReq()));
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ReactorHttpControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL，提示 `trigger.http.agent.ReactorController` / `DataAgentController` 尚不存在。

- [ ] **Step 3: 在 trigger 模块创建新 controller，保持原 URL、参数与返回类型不变**

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
    public SseEmitter autoAgent(@RequestBody AgentRequest request) throws UnsupportedEncodingException {
        // 迁移现有心跳与 dispatch 逻辑，不改请求/响应契约
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

    @PostMapping(value = "chatQuery", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatQuery(@RequestBody DataAgentChatReq req) throws Exception {
        return dataAgentService.webChatQueryData(req);
    }
}
```

- [ ] **Step 4: 删除 domain 中旧 controller，并确认 trigger 模块承担唯一 HTTP 入口职责**

```java
// 删除后，domain/agent/reactor 下不再保留 controller 包。
// 如需兼容旧 import，仅在 trigger 层调整引用，不在 domain 中保留壳类。
```

- [ ] **Step 5: 跑控制器回归并提交**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ReactorHttpControllerTest,ConversationHistoryControllerTest -Dsurefire.failIfNoSpecifiedTests=false
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
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerBoundaryTest.java`

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

- [ ] **Step 4: 修改 domain service，只依赖新契约**

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

- [ ] **Step 5: 跑边界测试并提交**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ExecutionLedgerBoundaryTest,ExecutionLedgerQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/adapter/repository/IExecutionLedgerWriteRepository.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/adapter/repository/IExecutionLedgerReadRepository.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerBoundaryTest.java
git commit -m "refactor: add ledger repository ports in domain"
```

---

## Task 4: 把 ledger DAO 与持久化 PO 迁入 infrastructure，并补齐 repository 实现

**Files:**
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/IDialogueRunLedgerDao.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/IDialogueSessionLedgerDao.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/ILlmInvocationLedgerDao.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/IToolInvocationLedgerDao.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/IArtifactLedgerDao.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/DialogueRunPO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/DialogueSessionPO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/LlmInvocationPO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ToolInvocationPO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ArtifactRecordPO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/repository/ExecutionLedgerWriteRepository.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/repository/ExecutionLedgerReadRepository.java`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_session_ledger_mapper.xml`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/llm_invocation_ledger_mapper.xml`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/tool_invocation_ledger_mapper.xml`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/artifact_ledger_mapper.xml`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueRunLedgerDao.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueSessionLedgerDao.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ILlmInvocationLedgerDao.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IToolInvocationLedgerDao.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IArtifactLedgerDao.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueRun.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueSession.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/LlmInvocation.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/ToolInvocation.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/ArtifactRecord.java`

- [ ] **Step 1: 先写失败测试，锁定 infrastructure 具备完整的 ledger repository 实现**

```java
@Test
public void shouldProvideInfrastructureLedgerRepositories() {
    Assert.assertNotNull(new ExecutionLedgerWriteRepository(null, null, null, null, null, null));
    Assert.assertNotNull(new ExecutionLedgerReadRepository(null, null, null, null, null));
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=AgentExecutionLedgerRepositoryTest,ExecutionLedgerQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL，repository 实现和新的 infrastructure DAO / PO 尚不存在。

- [ ] **Step 3: 在 infrastructure 建立 DAO + PO，并切换 XML namespace**

```java
@Mapper
public interface IDialogueRunLedgerDao {

    int insertRun(DialogueRunPO entity);

    int updateRunFinish(DialogueRunPO entity);

    DialogueRunPO queryByRequestId(@Param("requestId") String requestId);

    List<DialogueRunPO> queryBySessionId(@Param("sessionId") String sessionId);
}
```

```xml
<mapper namespace="org.wwz.ai.infrastructure.dao.IDialogueRunLedgerDao">
    <resultMap id="DialogueRunMap" type="org.wwz.ai.infrastructure.dao.po.DialogueRunPO">
        <!-- 复用现有列映射，保持 SQL 语义不变 -->
    </resultMap>
</mapper>
```

- [ ] **Step 4: 在 infrastructure 实现 read / write repository，并在仓储层完成 PO 与领域视图转换**

```java
@Repository
@RequiredArgsConstructor
public class ExecutionLedgerWriteRepository implements IExecutionLedgerWriteRepository {

    private final IDialogueRunLedgerDao dialogueRunLedgerDao;
    private final IDialogueSessionLedgerDao dialogueSessionLedgerDao;
    private final ILlmInvocationLedgerDao llmInvocationLedgerDao;
    private final IToolInvocationLedgerDao toolInvocationLedgerDao;
    private final IArtifactLedgerDao artifactLedgerDao;
    private final ToolOutputWriter toolOutputWriter;

    @Override
    public Long createRun(DialogueRunStartRecord record) {
        // 在这里做 PO 组装与数据库写入，不再让 domain service 看到 DAO
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

    @Override
    public ExecutionRunDetail queryRunDetail(String requestId) {
        // 在仓储层拼装视图，不把 PO 暴露给 domain
    }
}
```

- [ ] **Step 5: 跑 ledger 回归并提交**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=AgentExecutionLedgerRepositoryTest,ExecutionLedgerQueryServiceTest,ConversationHistoryControllerTest,ToolStructuredOutputWriterTest,ToolStructuredOutputReaderTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

```bash
git add ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao
git add ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/repository/ExecutionLedgerWriteRepository.java
git add ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/repository/ExecutionLedgerReadRepository.java
git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml
git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_session_ledger_mapper.xml
git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/llm_invocation_ledger_mapper.xml
git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/tool_invocation_ledger_mapper.xml
git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/artifact_ledger_mapper.xml
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueRunLedgerDao.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueSessionLedgerDao.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ILlmInvocationLedgerDao.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IToolInvocationLedgerDao.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IArtifactLedgerDao.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueRun.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueSession.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/LlmInvocation.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/ToolInvocation.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/ArtifactRecord.java
git commit -m "refactor: move ledger persistence to infrastructure"
```

---

## Task 5: 完整回归并更新项目文档，锁定第一阶段边界

**Files:**
- Modify: `CLAUDE.md`
- Modify: `ai-agent-station-study-domain/CLAUDE.md`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorBeanTopologyTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutionLedgerRepositoryTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java`

- [ ] **Step 1: 更新文档，明确第一阶段后的边界**

```md
- `trigger`：承接所有 HTTP / job / listener 入口；`reactor` legacy controller 也在这一层。
- `domain`：保留 Agent Runtime、领域服务、仓储契约；不再承接 controller、DAO、Spring 装配。
- `infrastructure`：承接 ledger DAO、PO、repository 实现与外部网关。
- `app`：承接 Spring Boot 装配、第三方客户端 Bean、初始化 Runner。
```

- [ ] **Step 2: 跑后端聚焦回归**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ReactorHttpControllerTest,ReplayProjectorBeanTopologyTest,AgentExecutionLedgerRepositoryTest,ExecutionLedgerQueryServiceTest,ConversationHistoryControllerTest,ToolStructuredOutputWriterTest,ToolStructuredOutputReaderTest -Dsurefire.failIfNoSpecifiedTests=false
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
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor" | rg "controller|mapper/IDialogue|mapper/ILlm|mapper/IToolInvocation|mapper/IArtifact|config/ReplayProjectorAutoConfiguration|config/data/Es7HighLevelClientConfig|config/DataAgentInitRunner"
```

Expected: 无输出；若仍有输出，说明第一阶段边界未收干净。

- [ ] **Step 5: 最终提交**

```bash
git add CLAUDE.md
git add ai-agent-station-study-domain/CLAUDE.md
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorBeanTopologyTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentExecutionLedgerRepositoryTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java
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
| 把 ledger DAO / PO / repository 实现收口到 infrastructure | Task 4 |
| 锁定文档与回归边界 | Task 5 |

### 2. Placeholder 扫描

- 没有 `TODO / TBD / later`
- 没有“自行处理边界情况”这类空描述
- 每个任务都给了真实路径、命令和验收标准

### 3. 类型一致性

- `domain` 只出现 `IExecutionLedgerWriteRepository / IExecutionLedgerReadRepository`
- `infrastructure` 承接 `IDialogue*Dao / *PO / ExecutionLedger*Repository`
- `trigger` 承接 `ReactorController / DataAgentController`
- `app` 承接 `ReplayProjectorAutoConfiguration / Es7HighLevelClientConfig / DataAgentInitRunner`

### 4. 风险提醒

1. `ReactorConfig` 本阶段不物理迁移，执行时不要把它混进同一批改动，否则范围会爆。
2. `BaseAgent`、`LLM`、`Tool` 运行链不得在第一阶段顺手重构；任何“顺手优化”都可能引发不可控回归。
3. ledger DAO 迁移时，`mybatis/mapper/*.xml` 的 namespace 与 resultMap type 必须和新 `infrastructure.dao` / `infrastructure.dao.po` 一起改，不能只挪 Java 文件。
4. `ConversationHistoryControllerTest`、`ExecutionLedgerQueryServiceTest`、`ToolStructuredOutputReaderTest` 是本阶段高价值回归口，不能跳过。
