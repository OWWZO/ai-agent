# Quickstart: Agent 领域边界最终收敛

## 1. 前置准备

- 确认当前分支为 `019-agent-ddd-convergence`
- 确认 `.specify/feature.json` 已指向 `specs/019-agent-ddd-convergence`
- 阅读以下上下文后再开始实现：
  - 根级 `CLAUDE.md`
  - `ai-agent-station-study-domain/CLAUDE.md`
  - `ai-agent-station-study-infrastructure/CLAUDE.md`
  - `ai-agent-station-study-trigger/CLAUDE.md`
  - `docs/superpowers/plans/2026-05-04-agent-service-reactor-ddd-convergence-plan.md`
  - `docs/superpowers/plans/2026-05-04-agent-service-reactor-ddd-convergence-remaining-plan.md`

## 2. 推荐实现顺序

1. 清理 `domain/agent/service` 剩余根接口与旧编排实现
2. 建立 `runtime / ledger / memory / rag / role` 五个子域归属
3. 把 `SseEmitter` 从 `domain` 彻底收口到 `trigger`
4. 把 HTTP / JDBC / Tool Runtime / Spring runtime lookup 下沉到 `infrastructure` 或 `app`
5. 删除兼容桥并更新边界测试与模块文档

## 3. 实施前基线扫描

```powershell
chcp 65001
rg -n "org\.wwz\.ai\.domain\.agent\.service\.(IAgentDispatchService|IExecuteStrategy|IArmoryService|ITaskService)" ai-agent-station-study-case ai-agent-station-study-trigger ai-agent-station-study-app ai-agent-station-study-domain
rg -n "SseEmitter" ai-agent-station-study-domain\src\main\java\org\wwz\ai\domain\agent
rg -n "new OkHttpClient|JdbcDataProvider|ApplicationContext.getBean|SpringContextHolder" ai-agent-station-study-domain\src\main\java\org\wwz\ai\domain\agent
```

目标：

- 明确旧根接口剩余依赖面
- 明确 `domain` 中 SSE、技术执行器与运行时查找残留位置

## 4. 编译与测试命令

### US1 聚焦验证

```powershell
chcp 65001
mvn --% compile -pl ai-agent-station-study-case,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests
rg -n "import org\.wwz\.ai\.domain\.agent\.service\.(IAgentDispatchService|IExecuteStrategy|IArmoryService|ITaskService);" ai-agent-station-study-case/src/main/java ai-agent-station-study-trigger/src/main/java
rg -n "import org\.wwz\.ai\.domain\.agent\.service\.(IFixRoleService|IRagService);" ai-agent-station-study-trigger/src/main/java
mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,SessionContextMemoryIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

### US2 聚焦验证

```powershell
chcp 65001
mvn --% compile -pl ai-agent-station-study-domain -am -DskipTests
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/runtime"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/ledger"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/memory"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/rag"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/role"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor"
mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorPersistenceBoundaryTest,SessionContextMemoryIntegrationTest,ReplayProjectorBeanTopologyTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

### US3 聚焦验证

```powershell
chcp 65001
rg -n "SseEmitter" ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent
rg -n "new OkHttpClient|JdbcDataProvider|ApplicationContext.getBean|SpringContextHolder" ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent
mvn --% compile -pl ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-app -am -DskipTests
mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,SpringRuntimeBoundaryTest,ReactorHttpControllerTest,AgentHandlerAutoConfigurationTest,DataAgentCapabilityDegradeTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

### 最终验收

```powershell
chcp 65001
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor"
mvn --% compile -pl ai-agent-station-study-types,ai-agent-station-study-api,ai-agent-station-study-case,ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests
mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,AgentHandlerAutoConfigurationTest,ReplayProjectorBeanTopologyTest,ReactorPersistenceBoundaryTest,SpringRuntimeBoundaryTest,DataAgentCapabilityDegradeTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

## 5. 手工验收清单

### 场景 A: 入口仍然可用

1. 检查 `AiAgentController`、`ReactorController`、`AgentTaskJob`
2. 确认它们只依赖 `case` 暴露的应用服务
3. 确认主链路仍能从入口进入既有执行路径

### 场景 B: 领域层不再持有 SSE 协议

1. 搜索 `domain/agent/**` 中的 `SseEmitter`
2. 确认剩余命中为 0
3. 确认 `trigger/http/reactor/support/SseEmitterAgentSessionStream.java` 仍存在

### 场景 C: 领域层不再直接创建技术执行器

1. 搜索 `new OkHttpClient`
2. 搜索 `JdbcDataProvider`
3. 搜索 `ApplicationContext.getBean` 与 `SpringContextHolder`
4. 确认这些命中全部从 `domain` 消失，且 runtime 通过 `RemoteHttpPort` / `RemoteStreamPort` / `FileArtifactPort` 装配

### 场景 D: 子域边界清晰

1. 检查 `runtime / ledger / memory / rag / role` 是否存在并具备明确职责说明
2. 确认旧 `reactor` 总包不再继续承载新逻辑
3. 确认 `service/rag`、`service/role` 已清空或迁移完成

### 场景 E: 文档和守卫同步

1. 检查根级与模块级 `CLAUDE.md`
2. 检查 `AgentContextConvergenceBoundaryTest`
3. 确认文档与测试都反映最终边界，而不是 Phase 1 过渡态

## 6. 关键文件抽查

```powershell
chcp 65001
Get-Content -Encoding utf8 ai-agent-station-study-case\src\main\java\org\wwz\ai\application\agent\stream\AgentSessionStream.java
Get-Content -Encoding utf8 ai-agent-station-study-trigger\src\main\java\org\wwz\ai\trigger\http\reactor\support\SseEmitterAgentSessionStream.java
Get-Content -Encoding utf8 ai-agent-station-study-app\src\test\java\org\wwz\ai\test\domain\AgentContextConvergenceBoundaryTest.java
Get-Content -Encoding utf8 ai-agent-station-study-app\src\test\java\org\wwz\ai\test\domain\SpringRuntimeBoundaryTest.java
Get-Content -Encoding utf8 ai-agent-station-study-app\src\test\java\org\wwz\ai\test\domain\ReactorPersistenceBoundaryTest.java
```

检查目标：

- 应用层已有协议无关流契约
- 触发层持有 SSE 协议适配
- 边界守卫测试覆盖旧目录、SSE、技术执行器与运行时查找
- dataagent 退化回归覆盖 case seam 与 runtime adapter 装配

## 7. 交付备注

- 本特性没有新增数据库结构、前端接口或 Python 子系统变更
- 允许存在短期 bridge，但必须在代码注释与任务拆解中写清删除时机
- 不要为了“顺手整理”扩大范围到 `ui/`、`reactor-tool/`、`reactor-client/`
- 当前最终验收结果：
  - `domain` 内 `SseEmitter`、`new OkHttpClient`、`JdbcDataProvider`、`SpringContextHolder`、`applicationContext.getBean(...)` 扫描均为 0 命中
  - `mvn --% compile -pl ai-agent-station-study-types,ai-agent-station-study-api,ai-agent-station-study-case,ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests` 已通过
  - `mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,AgentHandlerAutoConfigurationTest,ReplayProjectorBeanTopologyTest,ReactorPersistenceBoundaryTest,SpringRuntimeBoundaryTest,DataAgentCapabilityDegradeTest" "-Dsurefire.failIfNoSpecifiedTests=false"` 已通过
  - `domain/agent/service`、`domain/agent/reactor` 目录仍存在兼容桥与 legacy 模型，因此最终“目录清空式收敛”尚未完成
