# Implementation Plan: Agent Legacy Bridge 实质删除与子域再收敛

**Branch**: `[020-prune-agent-bridges]` | **Date**: `2026-05-05` | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/020-prune-agent-bridges/spec.md`

## Summary

在 `019-agent-ddd-convergence` 已完成第一轮目录收敛和主边界切分的基础上，本轮专门清理尚留在 `domain/agent/service` 与 `domain/agent/reactor` 中的 legacy bridge，把 GPT query、multi-agent、dataagent、image generation 及其残留模型/配置/步骤工厂继续收敛到稳定的 case/domain/infrastructure seam，并用更严格的目录守卫区分“必须删除的旧桥”“允许延期的历史契约”“已经稳定的子域归属”三类状态。

## Technical Context

**Language/Version**: Java 17（仅后端 Maven 多模块主链路；本期不改 `ui/`、`reactor-tool/`、`reactor-client/`）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / MyBatis-Plus 3.5.14、MySQL 8、OkHttp、现有 `ai-agent-station-study-case` 应用编排层、runtime/ledger/memory/rag/role 子域骨架、既有 dataquery / image generation / history replay / session memory 能力  
**Storage**: 复用现有 MySQL 会话、账本、tool-output 与 session memory 持久化；本期不新增表、不改 schema  
**Testing**: `mvn test -pl ai-agent-station-study-app -am -DskipTests=false` 下的边界守卫与聚焦回归，包括 `AgentContextConvergenceBoundaryTest`、`ReactorHttpControllerTest`、`DataAgentCapabilityDegradeTest`、`ExecutionLedgerBoundaryTest`、`SpringRuntimeBoundaryTest`、`SessionContextMemoryIntegrationTest`、`ReplayProjectorBeanTopologyTest`、`AgentImageGenerationControllerTest`、`WorkspaceImageGenerationServiceTest`  
**Target Platform**: Spring Boot 后端服务  
**Project Type**: Maven multi-module brownfield backend architecture convergence  
**Performance Goals**: 不引入可感知性能退化；bridge 删除后保持既有 query/dataagent/image generation/history replay/session memory 入口的响应行为与基线一致；验收重点是编译、回归与边界扫描全部通过  
**Constraints**: 必须保持 `Trigger -> Case -> Domain <- Infrastructure`；`trigger` 只做协议适配；`app` 只做装配；删除 bridge 时不得把 HTTP/JDBC/Spring runtime lookup 重新带回 `domain`；允许明确延期的历史配置契约暂存，但不得继续扩张为 catch-all 入口  
**Scale/Scope**: 影响 `ai-agent-station-study-case`、`ai-agent-station-study-domain`、`ai-agent-station-study-infrastructure`、`ai-agent-station-study-trigger`、`ai-agent-station-study-app`；聚焦 `reactor/service/**`、`reactor/model/**`、`reactor/config/data/**`、`service/execute/**`、`service/armory/**` 及其在 case/trigger/app/infrastructure 的残留依赖

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界：入口仍经由 `trigger/case`，领域仅保留稳定语义与端口，技术实现继续留在 `infrastructure/app`
- [x] 优先复用了现有 Agent、Tool、Prompt、RAG、DAO、配置装配能力：建立在 `019` 已落地的 seam、dataquery adapter、stream adapter、ledger/memory/rag/role 子域骨架之上继续收敛
- [x] 已为每个关键改动点定义可执行验证方式：目录扫描、禁止依赖扫描、聚焦编译和聚焦回归已纳入验证计划
- [x] 已将外部调用、流式链路、任务编排的异常与可观测性纳入方案：bridge 删除不改变既有错误收口与日志记录职责归属
- [x] 当前方案无额外宪章违例；复杂度来自棕地 bridge 删除与模型归属收敛本身，但比长期保留旧桥和旧模型双轨更简单

Phase 1 设计复核结果：仍然通过，无额外宪章例外。

## Project Structure

### Documentation (this feature)

```text
specs/020-prune-agent-bridges/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── bridge-removal-contract.md
│   └── subdomain-ownership-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
ai-agent-station-study-case/
└── src/main/java/org/wwz/ai/application/agent/
ai-agent-station-study-domain/
└── src/main/java/org/wwz/ai/domain/agent/
ai-agent-station-study-infrastructure/
└── src/main/java/org/wwz/ai/infrastructure/
ai-agent-station-study-trigger/
└── src/main/java/org/wwz/ai/trigger/
ai-agent-station-study-app/
├── src/main/java/org/wwz/ai/config/
└── src/test/java/org/wwz/ai/test/domain/
```

**Structure Decision**: 不新增模块或数据库结构，直接在既有后端多模块链路内完成 bridge 删除与归属再收敛。`case` 继续拥有应用编排与入口收口；`domain` 继续按 `runtime / ledger / memory / rag / role` 演进；`infrastructure` 承接 dataquery、remote adapter 与 image generation 技术实现；`app` 承接 dataagent 配置装配和边界测试；`trigger` 保持现有控制器与协议适配。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-case` | modify | 把当前仅“收口入口”的 query/dataquery seam 升级为稳定主链路，删除对 legacy bridge 的直接依赖 |
| `ai-agent-station-study-domain` | modify | 删除 `reactor/service` 与 `service/**` 下的旧桥、迁移残余模型/工厂/步骤节点、建立新的稳定领域契约 |
| `ai-agent-station-study-infrastructure` | modify | 承接必要的技术适配实现与残余 legacy 模型/请求执行支持的落点 |
| `ai-agent-station-study-trigger` | modify | 适配新的 case/domain seam，去除对旧请求模型或旧 bridge 的不必要耦合 |
| `ai-agent-station-study-app` | modify | 更新 auto-configuration、配置绑定、Mapper type 引用、边界守卫与测试 |
| `ui` | none | 本期无前端契约或工作流变更 |
| `reactor-tool` / `reactor-client` | none | 本期不改 Python 子系统 |

## Layer Boundary Notes

- `case`
  - 拥有 GPT query、dataagent 与既有 execute/dispatch 主链路的应用编排入口
  - 允许依赖稳定领域请求/响应模型与领域服务契约
  - 不允许继续直接依赖 `reactor/service` bridge
- `domain`
  - 继续承载 runtime、ledger、memory、rag、role 及必要的稳定请求/响应契约
  - 允许保留明确延期的共享配置契约，但不得继续把 bridge、控制流编排或技术工具类塞回旧目录
  - `service/**` 仅允许保留真正属于领域语义的步骤节点/工厂，且应逐步归入更清晰子域
- `infrastructure`
  - 承接 dataquery、remote HTTP/stream、file/image generation、DAO/repository 和必要技术配置支持
  - 不承载业务判断
- `trigger`
  - 继续负责 HTTP/SSE/Job 入口适配
  - 控制器不得依赖已删除 bridge，也不负责重建业务编排
- `app`
  - 负责 Spring Bean 装配、配置绑定与测试/Mapper 资源
  - 在本轮中承担 legacy package type 引用修正与边界守卫升级

## Data / Config / Contract Changes

- **Database**: N/A。本期不新增表、不改 `schema.sql`、不新增数据迁移
- **Config**:
  - 可能调整 `DataAgentInitRunner` 与相关配置绑定对 legacy config/data model 的依赖归属
  - `ReactorConfig` 与 `reactor/config/data/**` 需要被重新分类为稳定契约或进一步迁移目标
- **Contract**:
  - 新增 bridge 删除 contract：明确哪些 bridge 本轮必须删除、哪些延期项允许存在
  - 新增子域归属 contract：明确请求/响应模型、dataagent 配置、image generation 模型与步骤工厂归属
  - 不新增终端用户 HTTP API
- **Compatibility**:
  - 现有 controller 路由、query/dataagent/image generation/history replay/session memory 主链路保持稳定
  - 若保留少量历史包名，只能作为稳定契约或明确延期项存在，不能继续承担兼容委派

## Verification Plan

- **Java**:
  - `mvn --% compile -pl ai-agent-station-study-case,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests`
  - `mvn --% compile -pl ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-app -am -DskipTests`
  - `mvn --% compile -pl ai-agent-station-study-types,ai-agent-station-study-api,ai-agent-station-study-case,ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests`
  - `mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,DataAgentCapabilityDegradeTest,SessionContextMemoryIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
  - `mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=ExecutionLedgerBoundaryTest,ReplayProjectorBeanTopologyTest,SpringRuntimeBoundaryTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
  - `mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentImageGenerationControllerTest,WorkspaceImageGenerationServiceTest,MultiAgentServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
- **UI**: N/A
- **Python**: N/A
- **Manual**:
  - 扫描 `reactor/service/**` 和 `service/**` 剩余 bridge
  - 扫描 `case/trigger/app/infrastructure` 对 legacy bridge 与 legacy model 包的引用
  - 抽查 controller、dataagent init runner、image generation 入口与文档说明

## Phase 0: Research Summary

- 已确认上一轮遗留的 bridge 主体集中在：
  - `domain/agent/reactor/service/IGptProcessService`
  - `domain/agent/reactor/service/IMultiAgentService`
  - `domain/agent/reactor/service/DataAgentService`
  - `domain/agent/reactor/service/Nl2SqlService`
- 已确认上一轮遗留的“目录收敛但语义仍挂旧包”主要集中在：
  - `domain/agent/reactor/model/**`
  - `domain/agent/reactor/config/data/**`
  - `domain/agent/reactor/service/imagegeneration/**`
  - `domain/agent/service/execute/**`
  - `domain/agent/service/armory/**`
- 已确认 `case.query` 与 `case.dataquery` 目前仍只是入口收口层，底层仍直接依赖 legacy bridge，尚未形成稳定 seam
- 已确认 `app`、`trigger`、`infrastructure` 与部分测试仍大量引用 `reactor.model.*`、`reactor.config.data.*` 与 `reactor.service.*`，这正是本轮子域再收敛的主要对象
- 研究结论已固化到 [research.md](./research.md)，没有遗留待澄清项

## Phase 1: Design Decisions

### 1. 先删除“仅剩 case 委派”的 bridge，再建立稳定领域语义入口

- `IGptProcessService`、`IMultiAgentService`、`GptProcessServiceImpl` 视为第一优先级删除对象
- `case.query` 改为依赖新的稳定领域语义接口，而不是继续以“应用服务 -> 过渡接口 -> 旧实现”三段式调用

### 2. dataagent 从“入口收口”升级为真正的稳定 seam

- `DataAgentService`、`Nl2SqlService` 不再作为长期 bridge
- chat、NL2SQL、schema recall、模型元数据、向量/ES 同步要被拆为可解释的稳定职责
- `DataAgentInitRunner` 依赖的配置与服务也必须对应到新的稳定归属

### 3. 将 residual model/config 归类为“稳定契约”或“待清空旧桥”

- `reactor/model/**` 中的请求/响应、事件、image generation 模型需要逐类归属
- `reactor/config/data/**` 要么升级为稳定 dataquery/image generation 契约，要么列为下一阶段明确延期项
- 目录允许暂存的前提是“有明确稳定意义”，而不是“因为迁移麻烦先放着”

### 4. `service/execute/**` 与 `service/armory/**` 继续按运行时语义收敛

- 执行步骤节点、策略工厂、armory 节点与加载策略若仍被 case/runtime 真正依赖，应逐步归入 runtime 或 role/rag 等稳定边界
- 没有真实生产依赖方的旧步骤与旧工厂应直接删除

### 5. 守卫从“零引用旧根接口”升级为“区分三类 legacy 状态”

- 守卫需要区分：
  - 本轮必须清空的 bridge 目录
  - 允许延期但不得扩张的稳定历史契约目录
  - 已完成收敛的稳定子域目录
- 文档与测试必须使用同一分类口径，避免验收标准模糊

## Phase 2: Implementation Strategy

### User Story 1 - 开发者可以沿唯一主链路维护 legacy query 与 dataagent 能力

- 将 `case.query` 与 `case.dataquery` 从 legacy bridge 委派改为稳定领域 seam
- 删除 GPT query / multi-agent / dataagent 相关过渡接口和实现
- 更新 controller、job、auto-configuration 与聚焦回归

### User Story 2 - 维护者可以按稳定子域理解剩余 legacy 模型与配置归属

- 迁移 `reactor/model/**`、`reactor/config/data/**`、`reactor/service/imagegeneration/**` 与剩余执行/armory 节点
- 将仍有效的稳定契约归入 runtime/rag/ledger 或 infrastructure/app
- 清除不再需要的旧目录副本和旧包依赖

### User Story 3 - 交付团队可以用最终守卫锁定“bridge 已删完”的边界状态

- 升级 `AgentContextConvergenceBoundaryTest`、`ReactorHttpControllerTest`、`DataAgentCapabilityDegradeTest`、`SpringRuntimeBoundaryTest`
- 更新根级与模块级 `CLAUDE.md`
- 在 quickstart 和 contracts 中固化 bridge 删除与延期项边界

## Post-Design Constitution Check

- [x] DDD 边界保持清晰：应用编排继续在 `case`，领域语义继续在 `domain`，技术实现继续在 `infrastructure`，装配和入口分别在 `app/trigger`
- [x] 已优先复用 `019` 已落地的 seam、adapter、dataquery 端口与边界测试
- [x] 关键改动点均有自动化编译、测试或扫描验证路径
- [x] 已覆盖 legacy bridge 删除、dataagent seam 重组、legacy model/config 归属与守卫升级等风险点
- [x] 当前复杂度来自棕地收敛的必要最小闭环，不需要额外复杂度豁免

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | N/A | N/A |
