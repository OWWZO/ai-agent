# Quickstart: Agent Legacy Bridge 实质删除与子域再收敛

## 1. 前置准备

- 确认当前分支为 `020-prune-agent-bridges`
- 确认 `.specify/feature.json` 已指向 `specs/020-prune-agent-bridges`
- 阅读以下上下文后再开始实现：
  - 根级 `CLAUDE.md`
  - `ai-agent-station-study-domain/CLAUDE.md`
  - `ai-agent-station-study-infrastructure/CLAUDE.md`
  - `ai-agent-station-study-trigger/CLAUDE.md`
  - `specs/019-agent-ddd-convergence/spec.md`
  - `specs/019-agent-ddd-convergence/plan.md`
  - `docs/superpowers/plans/2026-05-04-agent-service-reactor-ddd-convergence-remaining-plan.md`

## 2. 推荐实现顺序

1. 删除 GPT query / multi-agent 过渡 bridge，并建立稳定 seam
2. 删除 dataagent / nl2sql 过渡 bridge，并拆出稳定职责
3. 重新归类 `reactor/model/**`、`reactor/config/data/**` 与 image generation 相关稳定契约
4. 清理 `service/execute/**`、`service/armory/**` 中无依赖方或可迁移的残余语义
5. 升级守卫与文档，区分“已删除 bridge”“允许延期契约”“稳定子域归属”

## 3. 实施前基线扫描

```powershell
chcp 65001
rg -n "IGptProcessService|IMultiAgentService|DataAgentService|Nl2SqlService" ai-agent-station-study-case ai-agent-station-study-trigger ai-agent-station-study-app ai-agent-station-study-domain ai-agent-station-study-infrastructure
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service"
rg -n "org\.wwz\.ai\.domain\.agent\.reactor\.model|org\.wwz\.ai\.domain\.agent\.reactor\.config\.data|org\.wwz\.ai\.domain\.agent\.reactor\.service" ai-agent-station-study-case ai-agent-station-study-trigger ai-agent-station-study-app ai-agent-station-study-infrastructure
```

目标：

- 明确 must-delete bridge 的真实依赖面
- 明确 residual model/config/service 的 legacy 包依赖面
- 为“桥接删除”和“子域再收敛”分别建立基线
- 明确哪些历史包已经被登记为 allowlist 契约，哪些文件必须清零

## 4. 编译与测试命令

### US1 聚焦验证

```powershell
chcp 65001
mvn --% compile -pl ai-agent-station-study-case,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests
rg -n "IGptProcessService|IMultiAgentService|DataAgentService|Nl2SqlService" ai-agent-station-study-case ai-agent-station-study-trigger ai-agent-station-study-app ai-agent-station-study-infrastructure
mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,DataAgentCapabilityDegradeTest,SessionContextMemoryIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

### US2 聚焦验证

```powershell
chcp 65001
mvn --% compile -pl ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-app -am -DskipTests
rg -n "org\.wwz\.ai\.domain\.agent\.reactor\.model|org\.wwz\.ai\.domain\.agent\.reactor\.config\.data" ai-agent-station-study-case ai-agent-station-study-trigger ai-agent-station-study-app ai-agent-station-study-infrastructure
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/armory"
mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=ExecutionLedgerBoundaryTest,ReplayProjectorBeanTopologyTest,AgentImageGenerationControllerTest,WorkspaceImageGenerationServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

### US3 聚焦验证

```powershell
chcp 65001
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service"
rg -n "IGptProcessService|IMultiAgentService|DataAgentService|Nl2SqlService" ai-agent-station-study-case ai-agent-station-study-trigger ai-agent-station-study-app ai-agent-station-study-infrastructure
mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,DataAgentCapabilityDegradeTest,SpringRuntimeBoundaryTest,ReplayProjectorBeanTopologyTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

### 最终验收

```powershell
chcp 65001
rg -n "IGptProcessService|IMultiAgentService|DataAgentService|Nl2SqlService" ai-agent-station-study-case ai-agent-station-study-trigger ai-agent-station-study-app ai-agent-station-study-infrastructure ai-agent-station-study-domain
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model"
rg --files "ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service"
mvn --% compile -pl ai-agent-station-study-types,ai-agent-station-study-api,ai-agent-station-study-case,ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests
mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,DataAgentCapabilityDegradeTest,ExecutionLedgerBoundaryTest,ReplayProjectorBeanTopologyTest,SpringRuntimeBoundaryTest,AgentImageGenerationControllerTest,WorkspaceImageGenerationServiceTest,SessionContextMemoryIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

## 5. 手工验收清单

### 场景 A: bridge 已经实质删除

1. 检查 `case.query`、`case.dataquery`
2. 确认它们不再注入 `IGptProcessService`、`DataAgentService`、`Nl2SqlService`
3. 确认控制器与 job 入口仍保持可用

### 场景 B: 子域与稳定契约归属更清晰

1. 检查 `reactor/model/**` 剩余内容是否都能解释为稳定契约或明确延期项
2. 检查 `reactor/config/data/**` 是否具有明确归属与禁止扩张说明
3. 检查 `reactor/service/**`、`service/execute/**`、`service/armory/**`、`service/runtime/**` 是否都已被登记为稳定历史契约或已删空

### 场景 C: 旧目录不再是默认落点

1. 查看本轮新增或修改文件
2. 确认新逻辑没有继续落入旧 bridge 目录
3. 确认文档和守卫都能发现旧目录回流

## 6. 关键文件抽查

```powershell
chcp 65001
Get-Content -Encoding utf8 ai-agent-station-study-case\src\main\java\org\wwz\ai\application\agent\query\GptQueryApplicationService.java
Get-Content -Encoding utf8 ai-agent-station-study-case\src\main\java\org\wwz\ai\application\agent\dataquery\DataAgentApplicationService.java
Get-Content -Encoding utf8 ai-agent-station-study-app\src\main\java\org\wwz\ai\config\reactor\DataAgentInitRunner.java
Get-Content -Encoding utf8 ai-agent-station-study-app\src\test\java\org\wwz\ai\test\domain\AgentContextConvergenceBoundaryTest.java
Get-Content -Encoding utf8 ai-agent-station-study-app\src\test\java\org\wwz\ai\test\domain\ReactorHttpControllerTest.java
```

检查目标：

- case seam 已不再委派 legacy bridge
- dataagent 初始化与运行时依赖指向稳定归属
- 边界守卫能区分 bridge 删除与稳定契约延期

## 8. 最终 Allowlist 口径

执行完 `020-prune-agent-bridges` 后，以下历史包允许继续存在，但必须被视为“稳定历史契约”，而不是 bridge：

- `reactor/config/data/**`
- `reactor/model/req|response|multi|dto|imagegeneration/**`
- `reactor/service/**`
- `service/execute/**`
- `service/armory/**`
- `service/runtime/**`

以下对象必须为 0：

- `IGptProcessService`
- `IMultiAgentService`
- `DataAgentService`
- `Nl2SqlService`
- `AgentHandlerService`
- `AgentHandlerFactory`
- `PlanSolveHandlerImpl`
- `ReactHandlerImpl`

## 7. 交付备注

- 本特性没有新增数据库结构、前端接口或 Python 子系统变更
- 本轮不采用 TDD 流程，验收证据以目录扫描、聚焦编译和聚焦回归为主
- 若保留少量历史包名，必须在 contracts 与模块文档中明确说明其稳定意义和禁止扩张边界
