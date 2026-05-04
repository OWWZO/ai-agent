# Agent Bounded Context Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `ai-agent-station-study-domain` 中平行演化的 `domain/agent/service` 与 `domain/agent/reactor` 收敛为统一的 `agent` 领域上下文，并把执行编排、SSE 输出、运行时装配与技术调用迁回 `Case / Trigger / Infrastructure / App`，形成真实可执行的六边形边界。

**Architecture:** 本次收敛采用“两步走”策略。第一步新增 `ai-agent-station-study-case` 模块，以 `org.wwz.ai.application.agent` 作为应用编排层，承接 `dispatch / execute / armory / task` 这些当前混在 `domain/agent/service` 里的用例编排职责，并通过 `AgentSessionStream` 等应用端口切断 `SseEmitter` 对领域层的污染。第二步把 `domain/agent/reactor` 重组为 `agent.runtime / agent.ledger / agent.memory / agent.rag / agent.role / agent.adapter` 六个子域包，保留真正的领域模型与领域服务，把 JDBC / OkHttp / MCP / Spring Bean 装配等技术实现下沉到 `infrastructure` 和 `app`。

**Tech Stack:** Java 17, Spring Boot 3.4.3, Maven multi-module, MyBatis/MyBatis-Plus, Spring AI 1.1.4, OkHttp SSE, JUnit 4

---

## 当前问题基线

### 1. `service` 与 `reactor` 实际属于同一领域上下文，但分层错位

- `domain/agent/service/dispatch`、`domain/agent/service/execute` 实际在做应用编排。
- `domain/agent/reactor/agent`、`domain/agent/reactor/model/ledger`、`domain/agent/reactor/service/replay` 更接近领域运行时内核。
- 两棵目录当前已经互相依赖，说明它们不是两个独立领域，而是“一个 `agent` 领域被按历史技术视角切裂了”。

### 2. `domain` 内仍存在明显的 Trigger / Case / Infrastructure 职责

- [IAgentDispatchService](/D:/Java Code/ai-agent/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/IAgentDispatchService.java:1) 直接暴露 `SseEmitter`。
- [ReactAgentExecuteStrategy](/D:/Java Code/ai-agent/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/ReactAgentExecuteStrategy.java:1) 同时处理历史上下文组装、SSE、执行链调度和账本终态。
- [GptProcessServiceImpl](/D:/Java Code/ai-agent/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/GptProcessServiceImpl.java:1) 返回 `SseEmitter`，不是纯领域服务。
- [MultiAgentServiceImpl](/D:/Java Code/ai-agent/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/MultiAgentServiceImpl.java:1) 在领域层内直接构建 `OkHttpClient` 和本机 HTTP 调用。

### 3. 运行时与技术适配边界仍混在 `reactor` 顶层

- `reactor/config/**`、`reactor/data/jdbc/**`、`reactor/util/**`、`reactor/agent/tool/mcp/runtime/**` 中大量内容属于基础设施。
- `reactor/service/tooloutput/**`、`reactor/service/imagegeneration/**` 中一部分是领域契约，一部分是技术执行器，当前没有明确拆开。

### 4. 已有 Phase 2 工作需要避免冲突

- 仓库中已经存在 `reactor-ddd-phase2-spring-runtime-decoupling`、`reactor-ddd-phase2-data-engine-extraction`、`reactor-ddd-phase2-remote-port-adapters` 等 OpenSpec 草案。
- 本计划必须复用这些方向，不能重新发明另一套包边界。

---

## 目标结构

### Maven 模块结构

```text
ai-agent-station-study-types
ai-agent-station-study-api
ai-agent-station-study-case            # 新增；模块名叫 case，包名不用 case
ai-agent-station-study-domain
ai-agent-station-study-infrastructure
ai-agent-station-study-trigger
ai-agent-station-study-app
```

### Java 包结构

```text
org.wwz.ai.application.agent           # 位于 ai-agent-station-study-case
├── dispatch/
├── execute/
├── armory/
└── task/

org.wwz.ai.domain.agent                # 位于 ai-agent-station-study-domain
├── adapter/
│   ├── port/
│   └── repository/
├── model/
├── runtime/
├── ledger/
├── memory/
├── rag/
└── role/

org.wwz.ai.infrastructure.agent        # 位于 ai-agent-station-study-infrastructure
├── adapter/port/
├── adapter/repository/
├── runtime/
├── dataquery/
└── gateway/
```

### 目录迁移映射

| 当前目录 | 目标归属 | 原因 |
| --- | --- | --- |
| `domain/agent/service/dispatch/**` | `application.agent.dispatch/**` | 纯用例编排，不是领域规则 |
| `domain/agent/service/execute/**` | `application.agent.execute/**` | 执行策略调度 + 运行链编排，属于 Case |
| `domain/agent/service/armory/**` | `application.agent.armory/**` | 装配流程编排，不是领域核心 |
| `domain/agent/service/task/**` | `application.agent.task/**` | 调度与触发流程编排 |
| `domain/agent/service/rag/**` | `domain.agent.rag/**` | 领域能力，可保留在 Domain |
| `domain/agent/service/role/**` | `domain.agent.role/**` | 领域规则，可保留在 Domain |
| `domain/agent/reactor/agent/**` | `domain.agent.runtime/**` | Agent 运行时核心 |
| `domain/agent/reactor/model/ledger/**` | `domain.agent.ledger/model/**` | 账本领域模型 |
| `domain/agent/reactor/service/replay/**` | `domain.agent.ledger/service/replay/**` | 回放领域能力 |
| `domain/agent/reactor/model/memory/**` | `domain.agent.memory/model/**` | 会话记忆领域模型 |
| `domain/agent/reactor/service/SessionContextMemoryService.java` | `domain.agent.memory/service/**` | 记忆领域接口 |
| `domain/agent/reactor/config/**` | `app/config/**` 或 `infrastructure/**` | 配置与装配不属于 Domain |
| `domain/agent/reactor/data/jdbc/**` | `infrastructure.agent.dataquery.jdbc/**` | JDBC 技术执行器 |
| `domain/agent/reactor/util/HttpUtils.java` | `infrastructure.agent.gateway/**` | HTTP 技术工具 |

---

## 非目标

- 本计划不改数据库表结构。
- 本计划不重写前端协议，只允许最小必要的 import / wiring 调整。
- 本计划不一次性替换所有运行时模型名称，优先保持类名稳定、先改边界。
- 本计划不在第一批任务中物理迁走所有 `reactor` 文件；允许通过兼容桥逐步迁移。

---

## Task 1: 新增 `case` 模块并建立边界守卫

**Files:**
- Modify: `pom.xml`
- Create: `ai-agent-station-study-case/pom.xml`
- Create: `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/package-info.java`
- Create: `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/stream/AgentSessionStream.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java`

- [ ] **Step 1: 先写失败测试，锁定新的模块与禁用边界**

```java
@Test
public void shouldIntroduceCaseModuleForAgentOrchestration() {
    Assert.assertTrue(Files.exists(Path.of("ai-agent-station-study-case", "pom.xml")));
}

@Test
public void shouldEventuallyRemoveSseEmitterFromDomainAgentPackages() throws Exception {
    List<Path> sourceFiles = Files.walk(Path.of(
            "ai-agent-station-study-domain", "src", "main", "java", "org", "wwz", "ai", "domain", "agent"))
            .filter(path -> path.toString().endsWith(".java"))
            .toList();
    for (Path sourceFile : sourceFiles) {
        String text = Files.readString(sourceFile, StandardCharsets.UTF_8);
        Assert.assertFalse("domain agent package should not reference SseEmitter: " + sourceFile,
                text.contains("SseEmitter"));
    }
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=AgentContextConvergenceBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL，提示 `ai-agent-station-study-case/pom.xml` 不存在，且 `domain.agent` 仍引用 `SseEmitter`。

- [x] **Step 3: 新增 `case` 模块骨架，并定义应用层会话输出端口**

```xml
<module>ai-agent-station-study-case</module>
```

```xml
<artifactId>ai-agent-station-study-case</artifactId>
<dependencies>
    <dependency>
        <groupId>org.wwz.ai</groupId>
        <artifactId>ai-agent-station-study-api</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>org.wwz.ai</groupId>
        <artifactId>ai-agent-station-study-domain</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencies>
```

```java
package org.wwz.ai.application.agent.stream;

/**
 * 应用层会话输出端口。
 * 触发层可以用 SseEmitter、WebSocket、MQ 等方式实现，Case/Domain 不直接依赖具体输出协议。
 */
public interface AgentSessionStream {

    void send(Object payload) throws Exception;

    void complete();

    void completeWithError(Throwable throwable);
}
```

- [x] **Step 4: 运行模块编译，确认新的模块依赖链能成立**

Run:

```bash
mvn clean compile -pl ai-agent-station-study-case -am
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 提交模块骨架**

```bash
git add pom.xml
git add ai-agent-station-study-case/pom.xml
git add ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/package-info.java
git add ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/stream/AgentSessionStream.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentContextConvergenceBoundaryTest.java
git commit -m "refactor: add case module for agent application layer"
```

---

## Task 2: 迁移 `dispatch / execute / armory / task` 到 `case` 模块

**Files:**
- Create: `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/dispatch/IAgentDispatchService.java`
- Create: `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/dispatch/AgentDispatchService.java`
- Create: `ai-agent-station-study-case/src/main/java/org/wwz/ai/application/agent/execute/IExecuteStrategy.java`
- Move: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/**`
- Move: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/dispatch/**`
- Move: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory/**`
- Move: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/task/**`
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/reactor/ReactorController.java`
- Create: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/reactor/support/SseEmitterAgentSessionStream.java`

- [ ] **Step 1: 先写失败测试，锁定这些编排职责不再留在 domain/service**

```java
@Test
public void shouldRemoveAgentDispatchAndExecutePackagesFromDomainServiceTree() {
    Assert.assertFalse(Files.exists(Path.of(
            "ai-agent-station-study-domain", "src", "main", "java", "org", "wwz", "ai", "domain", "agent", "service", "dispatch")));
    Assert.assertFalse(Files.exists(Path.of(
            "ai-agent-station-study-domain", "src", "main", "java", "org", "wwz", "ai", "domain", "agent", "service", "execute")));
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=AgentContextConvergenceBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL，`dispatch` 与 `execute` 目录仍存在于 `domain/service` 下。

- [x] **Step 3: 在 `case` 模块重建应用层接口，并把 `SseEmitter` 替换为 `AgentSessionStream`**

```java
package org.wwz.ai.application.agent.dispatch;

import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;

public interface IAgentDispatchService {

    void dispatch(AgentRequest request, AgentSessionStream stream) throws Exception;
}
```

```java
package org.wwz.ai.application.agent.dispatch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.execute.IExecuteStrategy;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.reactor.agent.enums.AgentType;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.types.exception.BizException;

import javax.annotation.Resource;
import java.util.Map;

@Slf4j
@Service
public class AgentDispatchService implements IAgentDispatchService {

    @Resource
    private Map<String, IExecuteStrategy> executeStrategyMap;

    @Override
    public void dispatch(AgentRequest request, AgentSessionStream stream) throws Exception {
        String strategy = AgentType.WORKFLOW.getValue().equals(request.getAgentType())
                ? "flowAgentExecuteStrategy"
                : AgentType.PLAN_SOLVE.getValue().equals(request.getAgentType())
                ? "planSolveAgentExecuteStrategy"
                : "reactAgentExecuteStrategy";
        IExecuteStrategy executeStrategy = executeStrategyMap.get(strategy);
        if (executeStrategy == null) {
            throw new BizException("不存在的执行策略类型 strategy:" + strategy);
        }
        executeStrategy.execute(request, stream);
    }
}
```

- [x] **Step 4: 让 Trigger 负责把 `SseEmitter` 适配为应用层流接口**

```java
package org.wwz.ai.trigger.http.reactor.support;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.application.agent.stream.AgentSessionStream;

public class SseEmitterAgentSessionStream implements AgentSessionStream {

    private final SseEmitter emitter;

    public SseEmitterAgentSessionStream(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void send(Object payload) throws Exception {
        emitter.send(payload);
    }

    @Override
    public void complete() {
        emitter.complete();
    }

    @Override
    public void completeWithError(Throwable throwable) {
        emitter.completeWithError(throwable);
    }
}
```

```java
agentDispatchService.dispatch(request, new SseEmitterAgentSessionStream(emitter));
```

- [x] **Step 5: 跑编译和关键回归**

Run:

```bash
mvn clean compile -pl ai-agent-station-study-case,ai-agent-station-study-trigger -am
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ReactorHttpControllerTest,AgentContextConvergenceBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: BUILD SUCCESS，且路由委派回归通过。

---

## Task 3: 把 `reactor` 重组为 `agent` 领域子域

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime/package-info.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger/package-info.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/memory/package-info.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/rag/package-info.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/role/package-info.java`
- Move: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/**`
- Move: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/**`
- Move: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/**`
- Move: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/**`
- Move: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/**`
- Move: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/rag/**`
- Move: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/role/**`

- [ ] **Step 1: 先写失败测试，锁定顶层 `reactor` 不再作为总包继续增长**

```java
@Test
public void shouldStopUsingTopLevelReactorPackageAsCatchAllDomainBucket() {
    Assert.assertFalse(Files.exists(Path.of(
            "ai-agent-station-study-domain", "src", "main", "java", "org", "wwz", "ai", "domain", "agent", "reactor", "service")));
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=AgentContextConvergenceBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL，说明 `domain/agent/reactor/service` 仍然存在。

- [ ] **Step 3: 先补新的子域包说明，再按子域逐批移动**

```java
/**
 * Agent 运行时子域。
 * 负责 Agent 生命周期、工具调用主循环、上下文模型与运行时状态机。
 */
package org.wwz.ai.domain.agent.runtime;
```

```java
/**
 * Agent 执行账本子域。
 * 负责 run / llm invocation / tool invocation / artifact / replay 等领域模型与领域服务。
 */
package org.wwz.ai.domain.agent.ledger;
```

```java
/**
 * Agent 会话记忆子域。
 * 负责 session history、react cycle memory 与上下文重建规则。
 */
package org.wwz.ai.domain.agent.memory;
```

- [ ] **Step 4: 按“运行时、账本、记忆、RAG、Role”五批迁移，不做跨批次顺手重构**

```text
批次 1: reactor/agent/**, handler/**, runtime/** -> domain/agent/runtime/**
批次 2: reactor/entity/**, reactor/model/ledger/**, reactor/service/replay/** -> domain/agent/ledger/**
批次 3: reactor/model/memory/**, SessionContextMemoryService.java -> domain/agent/memory/**
批次 4: service/rag/**, reactor/service/TableRagService.java, SchemaRecallService.java, SopRecallService.java -> domain/agent/rag/**
批次 5: service/role/**, IFixRoleService.java -> domain/agent/role/**
```

- [ ] **Step 5: 跑模块编译并用 `rg` 确认旧目录只剩兼容桥**

Run:

```bash
mvn clean compile -pl ai-agent-station-study-domain -am
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor"
```

Expected: 只剩明确标注为过渡兼容的桥接文件；不能再出现新的 `service/*` 聚合目录。

---

## Task 4: 把技术执行器从 Domain 下沉到 Infrastructure / App

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/adapter/port/IAutoAgentGateway.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/adapter/port/IDataQueryExecutionPort.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/agent/adapter/port/AutoAgentGateway.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/agent/dataquery/jdbc/JdbcDataQueryExecutionAdapter.java`
- Move: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/data/jdbc/**`
- Move: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/data/provider/jdbc/**`
- Move: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/util/HttpUtils.java`
- Move: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/**`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/MultiAgentServiceImpl.java`

- [ ] **Step 1: 先写失败测试，锁定 Domain 不再直接构建 OkHttp / JDBC 技术对象**

```java
@Test
public void shouldNotCreateOkHttpOrJdbcInfrastructureInsideDomainAgentSource() throws Exception {
    List<Path> sourceFiles = Files.walk(Path.of(
            "ai-agent-station-study-domain", "src", "main", "java", "org", "wwz", "ai", "domain", "agent"))
            .filter(path -> path.toString().endsWith(".java"))
            .toList();
    for (Path sourceFile : sourceFiles) {
        String text = Files.readString(sourceFile, StandardCharsets.UTF_8);
        Assert.assertFalse("domain agent source should not create OkHttpClient: " + sourceFile,
                text.contains("new OkHttpClient"));
        Assert.assertFalse("domain agent source should not expose JdbcDataProvider: " + sourceFile,
                text.contains("JdbcDataProvider"));
    }
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=AgentContextConvergenceBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL，`MultiAgentServiceImpl` 与 `DataAgentService` 仍命中技术关键字。

- [ ] **Step 3: 先定义领域端口，再把技术实现下沉**

```java
package org.wwz.ai.domain.agent.adapter.port;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;

public interface IAutoAgentGateway {

    void handle(AgentRequest request, SseEmitter emitter);
}
```

```java
package org.wwz.ai.infrastructure.agent.adapter.port;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.adapter.port.IAutoAgentGateway;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;

@Slf4j
@Component
public class AutoAgentGateway implements IAutoAgentGateway {

    @Override
    public void handle(AgentRequest request, SseEmitter emitter) {
        OkHttpClient client = new OkHttpClient.Builder().build();
        // 这里承接原 MultiAgentServiceImpl 的 HTTP 调用细节
    }
}
```

- [ ] **Step 4: 对 DataQuery 采用相同策略，下沉 `jdbc / provider / util / config`**

```text
1. 保留 query model、schema model、NL2SQL 请求语义在 Domain。
2. 将连接池、catalog、dialect、JdbcDataProvider、JdbcUtils、HttpUtils、ESUtil 迁到 Infrastructure。
3. `app` 只负责 Spring Bean 装配，不放业务判断。
```

- [ ] **Step 5: 跑聚焦回归并提交**

Run:

```bash
mvn clean compile -pl ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-app -am
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=AgentContextConvergenceBoundaryTest,ReactorPersistenceBoundaryTest,SpringRuntimeBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: BUILD SUCCESS，且领域边界测试通过。

---

## Task 5: 清理旧目录、更新文档并锁定最终边界

**Files:**
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/**`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/**`
- Modify: `CLAUDE.md`
- Modify: `ai-agent-station-study-domain/CLAUDE.md`
- Modify: `ai-agent-station-study-infrastructure/CLAUDE.md`
- Modify: `ai-agent-station-study-trigger/CLAUDE.md`

- [ ] **Step 1: 更新模块文档，明确新的依赖方向**

```md
- Trigger 只负责 HTTP / SSE / Job / Listener 入口适配。
- Case 模块负责 dispatch / execute / armory / task 等应用编排。
- Domain 模块只保留 agent 领域模型、领域服务、port / repository 契约。
- Infrastructure 模块承接 JDBC / HTTP / MCP / Tool Runtime / Repository Adapter。
- App 模块承接 Spring Bean 装配与运行时注册。
```

- [ ] **Step 2: 跑目录扫描，确认旧历史树已清空**

Run:

```bash
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor"
```

Expected: 无输出；如果仍有输出，必须先说明哪些文件属于兼容桥、何时删除，再决定是否允许保留。

- [ ] **Step 3: 跑全量编译回归**

Run:

```bash
mvn clean compile -pl ai-agent-station-study-types,ai-agent-station-study-api,ai-agent-station-study-case,ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-trigger,ai-agent-station-study-app -am
```

Expected: BUILD SUCCESS

- [ ] **Step 4: 跑最终边界回归**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,ReplayProjectorBeanTopologyTest,ReactorPersistenceBoundaryTest,SpringRuntimeBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

- [ ] **Step 5: 提交收尾变更**

```bash
git add CLAUDE.md
git add ai-agent-station-study-domain/CLAUDE.md
git add ai-agent-station-study-infrastructure/CLAUDE.md
git add ai-agent-station-study-trigger/CLAUDE.md
git commit -m "docs: lock agent bounded context ddd convergence"
```

---

## 风险与约束

### 1. 不要把“移动目录”当成重构完成

只有当以下条件同时成立，才算真正完成：

- `domain` 不再依赖 `SseEmitter`
- `domain` 不再依赖 `OkHttpClient` / JDBC 执行器 / Spring Bean 装配
- `dispatch / execute / armory / task` 已迁入 `case`
- `trigger` 与 `app` 的职责文档同步更新

### 2. 优先兼容主链路，不要一次性大爆炸改名

- 类名可以短期保持稳定，先改包边界。
- Bean name 可以短期兼容旧值，先让运行时和测试稳定。
- 如需大规模 rename，必须在第二轮单独提交。

### 3. 与现有 Phase 2 OpenSpec 对齐

- `spring-runtime-decoupling` 负责去掉 `ApplicationContext.getBean(...)`
- `remote-port-adapters` 负责去掉 HTTP / MCP / Tool Runtime 技术调用
- `data-engine-extraction` 负责去掉 JDBC / SQL / Catalog 技术执行器

本计划不替代它们，而是给出这三条变更在 `agent/service + reactor` 收敛场景下的统一落点。

---

## 执行顺序建议

1. 先做 Task 1，建立 `case` 模块和边界守卫。
2. 再做 Task 2，把最脏的 `dispatch / execute` 从 Domain 挪出去。
3. 第三步做 Task 3，重组 `agent` 领域内部结构。
4. 第四步做 Task 4，下沉技术执行器。
5. 最后做 Task 5，删除旧目录并锁文档。

这个顺序的核心原因是：先切“层”，再切“包”，最后切“技术实现”。如果反过来做，会一直陷入 import 改动和循环依赖。

---

## 自我审查

### 1. 范围覆盖

- 回答了“`service` 和 `reactor` 是否应该融合”：应该融合到同一个 `agent` 上下文。
- 回答了“如何保持 DDD”：通过新增 `case` 模块，把编排迁出 Domain，同时把 Domain 内部再按子域重组。
- 回答了“目录怎么办”：最终删除历史的 `domain/agent/service` 和 `domain/agent/reactor` 两棵并列树。

### 2. 无占位符检查

- 没有 `TODO / TBD / 后续补充`
- 每个任务都给了具体路径、命令和验收方式

### 3. 类型一致性

- Maven 模块名使用 `ai-agent-station-study-case`
- Java 包名统一使用 `org.wwz.ai.application.agent`
- 领域根统一使用 `org.wwz.ai.domain.agent`
- `SseEmitter` 只允许停留在 Trigger 适配器，不再进入 Case/Domain
