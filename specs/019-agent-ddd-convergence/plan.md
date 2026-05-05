# Implementation Plan: Agent 领域边界最终收敛

**Branch**: `[019-agent-ddd-convergence]` | **Date**: `2026-05-04` | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/019-agent-ddd-convergence/spec.md`

## Summary

在已完成第一阶段 `Trigger -> Case -> Domain` 收敛的基础上，继续清理 `domain/agent/service` 与 `domain/agent/reactor` 的历史残留，把 Agent 主链路稳定收敛为“应用编排在 `case`、领域语义在 `domain`、技术执行器在 `infrastructure`、协议适配与装配分别在 `trigger/app`”的最终边界。实现上不新增终端用户能力，而是通过子域重组、SSE 抽象统一、HTTP/JDBC/Tool Runtime 下沉、边界守卫扩充和文档锁定，完成 Agent 有界上下文的最终六边形收敛。

## Technical Context

**Language/Version**: Java 17（仅后端 Maven 多模块主链路；本期不改 `ui/`、`reactor-tool/`、`reactor-client/`）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / MyBatis-Plus 3.5.14、MySQL 8、OkHttp、现有 `ai-agent-station-study-case` 应用编排层、execution ledger / history replay / session memory 领域能力  
**Storage**: 复用现有 MySQL 会话、执行账本、tool-output 与 session memory 持久化；本期不新增表、不改 schema  
**Testing**: `mvn test -pl ai-agent-station-study-app -am -DskipTests=false` 下的边界守卫与主链路聚焦回归，包括 `AgentContextConvergenceBoundaryTest`、`ReactorHttpControllerTest`、`SessionContextMemoryIntegrationTest`、`SpringRuntimeBoundaryTest`、`ReactorPersistenceBoundaryTest`、`AgentHandlerAutoConfigurationTest`、`ReplayProjectorBeanTopologyTest`  
**Target Platform**: Spring Boot 后端服务  
**Project Type**: Maven multi-module brownfield backend architecture convergence  
**Performance Goals**: 不新增可感知性能退化；主链路边界迁移后保持现有 Agent 请求、历史回放与 session memory 的响应特征不劣于基线；验收重点是编译、回归和边界扫描全部通过  
**Constraints**: 必须保持 `Trigger -> Case -> Domain <- Infrastructure`；`SseEmitter` 只能停留在 `trigger`；`domain` 不得直接依赖 `OkHttpClient`、`JdbcDataProvider`、`SpringContextHolder` 或 `applicationContext.getBean(...)`；不得新增数据库结构与前端工作流；复用 Phase 1 已落地的 `case` 模块、会话流抽象和边界测试  
**Scale/Scope**: 影响 `ai-agent-station-study-case`、`ai-agent-station-study-domain`、`ai-agent-station-study-infrastructure`、`ai-agent-station-study-trigger`、`ai-agent-station-study-app`；主要涉及旧 `domain/agent/service` 兼容树、旧 `domain/agent/reactor` 总包、边界测试与模块文档

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界：应用编排留在 `case`，领域子域留在 `domain`，技术执行器与仓储适配留在 `infrastructure`，协议与装配分别留在 `trigger/app`
- [x] 优先复用了现有 Agent、Tool、Prompt、RAG、DAO、配置装配能力：基于已落地的 `case` 模块、`AgentSessionStream`、history replay、execution ledger、session memory 与现有边界测试继续收敛
- [x] 已为每个关键改动点定义可执行验证方式：目录扫描、关键字符串扫描、模块编译、边界守卫测试与主链路回归均已纳入验证计划
- [x] 已将流式链路、任务编排、外部调用与运行时装配的异常和可观测性纳入方案：通过协议抽象、port/repository seam 和边界守卫锁定责任归属
- [x] 当前方案无额外宪章违例；复杂度来自棕地收敛本身，但比继续保留旧 service/reactor 双树更简单

Phase 1 设计复核结果：仍然通过，无额外宪章例外。

## Project Structure

### Documentation (this feature)

```text
specs/019-agent-ddd-convergence/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── application-boundary-contract.md
│   └── domain-infrastructure-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
ai-agent-station-study-case/
└── src/main/java/org/wwz/ai/application/agent/
ai-agent-station-study-domain/
└── src/main/java/org/wwz/ai/domain/agent/
ai-agent-station-study-infrastructure/
└── src/main/java/org/wwz/ai/infrastructure/agent/
ai-agent-station-study-trigger/
└── src/main/java/org/wwz/ai/trigger/
ai-agent-station-study-app/
├── src/main/java/org/wwz/ai/config/
└── src/test/java/org/wwz/ai/test/domain/
```

**Structure Decision**: 不引入新的并行模块或新数据库结构，直接在既有 `case/domain/infrastructure/trigger/app` 链路内完成最终收敛。`case` 固定为应用编排主入口；`domain` 内部按 `runtime / ledger / memory / rag / role` 重组；`infrastructure` 承接 HTTP / JDBC / Tool Runtime 等技术适配；`trigger` 保留 SSE 与 HTTP 协议适配；`app` 保留 Spring 装配与测试资源。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-case` | modify | 固定 dispatch / execute / armory / task 的应用编排所有权，承接统一会话输出抽象 |
| `ai-agent-station-study-domain` | modify | 移除旧 service/reactor 双树主路径，重组 runtime/ledger/memory/rag/role 子域，清退 SSE/HTTP/JDBC/Spring runtime 耦合 |
| `ai-agent-station-study-infrastructure` | modify | 承接 port/repository 实现、HTTP/JDBC/Tool Runtime 技术执行器与必要 gateway |
| `ai-agent-station-study-trigger` | modify | 保持 HTTP / SSE 入口稳定，并承接 `SseEmitter` 生命周期与协议适配 |
| `ai-agent-station-study-app` | modify | 更新自动装配、测试、Mapper/Bean wiring 与模块级文档，锁定最终边界 |
| `ui` | none | 本期无终端用户工作流与前端契约变化 |
| `reactor-tool` / `reactor-client` | none | 本期不改 Python 子系统 |

## Layer Boundary Notes

- `case`
  - 拥有 dispatch、execute、armory、task 的主链路编排
  - 负责策略选择、会话流桥接和跨领域流程组织
- `domain`
  - 只保留 runtime、ledger、memory、rag、role 五类子域能力
  - 声明 repository / port seam 与协议无关输出契约
  - 不再直接承接 `SseEmitter`、JDBC provider、HTTP 客户端和 Spring service locator
- `infrastructure`
  - 承接 DAO、repository adapter、HTTP gateway、JDBC/dataquery、tool runtime adapter
  - 不承接业务判断与领域规则
- `trigger`
  - 只负责 HTTP/SSE/Job 入口适配、心跳与错误收口
  - 不再穿透旧 `domain.agent.service` 根接口
- `app`
  - 负责 Spring Bean 装配、运行时注册、测试资源和边界文档同步
  - 不承载领域规则和技术实现细节的业务判断
- 特性边界
  - 允许短期 bridge，但必须显式标注删除时机
  - 本期不改数据库结构、不改前端、不重命名与业务语义相关的大量类名

## Data / Config / Contract Changes

- **Database**: N/A。本期不新增表、不改 `schema.sql`、不新增数据迁移
- **Config**:
  - 可能调整 `app` 中的 Spring Bean 装配归属
  - `ReactorConfig` 继续作为已明确延后的共享配置契约，除非被单独授权，不在本期物理迁移
- **Contract**:
  - 应用边界 contract：`trigger -> case -> domain` 的主链路所有权与会话流抽象
  - 领域/基础设施 seam：port/repository contract、协议无关输出 contract、边界守卫 contract
  - 不新增终端用户 HTTP API
- **Compatibility**:
  - 现有控制器路由、任务调度、history replay、tool-output 恢复、session memory 主链路保持稳定
  - 若为了迁移需要保留 bridge，必须有显式删除计划

## Verification Plan

- **Java**:
  - `mvn --% compile -pl ai-agent-station-study-case,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests`
  - `mvn --% compile -pl ai-agent-station-study-domain -am -DskipTests`
  - `mvn --% compile -pl ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-app -am -DskipTests`
  - `mvn --% compile -pl ai-agent-station-study-types,ai-agent-station-study-api,ai-agent-station-study-case,ai-agent-station-study-domain,ai-agent-station-study-infrastructure,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests`
  - `mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,SpringRuntimeBoundaryTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
  - `mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,SessionContextMemoryIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
  - `mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorPersistenceBoundaryTest,SpringRuntimeBoundaryTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
  - `mvn --% test -pl ai-agent-station-study-app -am -DskipTests=false "-Dtest=AgentContextConvergenceBoundaryTest,ReactorHttpControllerTest,AgentHandlerAutoConfigurationTest,ReplayProjectorBeanTopologyTest,ReactorPersistenceBoundaryTest,SpringRuntimeBoundaryTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
- **UI**: N/A
- **Python**: N/A
- **Manual**:
  - 扫描旧根接口引用
  - 扫描 `SseEmitter`、`new OkHttpClient`、`JdbcDataProvider`、`applicationContext.getBean(...)`、`SpringContextHolder`
  - 抽查控制器、会话流适配器、边界守卫测试与模块文档

## Phase 0: Research Summary

- 已确认 `case` 模块、`AgentSessionStream` 与 `SseEmitterAgentSessionStream` 已落地，可作为本轮收敛的稳定基础
- 已确认 `domain` 仍残留：
  - 旧 `IAgentDispatchService`、`IExecuteStrategy`、`IArmoryService`、`ITaskService`
  - 多处 `SseEmitter` 依赖
  - `new OkHttpClient`
  - `JdbcDataProvider`
  - Spring runtime 边界守卫未完全覆盖的残留路径
- 已确认现有边界测试已具备基础框架，但 `AgentContextConvergenceBoundaryTest` 仍只锁定第一阶段边界，需要扩充为最终边界守卫
- 研究结论已固化到 [research.md](./research.md)，没有遗留待澄清项

## Phase 1: Design Decisions

### 1. 应用编排边界固定

- `dispatch / execute / armory / task` 统一以 `case` 为唯一主入口
- 旧 `domain/agent/service` 根接口与实现视为迁移对象，不再保留长期生产归属

### 2. 领域子域重组

- `reactor` 只作为历史迁移来源
- `runtime / ledger / memory / rag / role` 成为新的稳定子域边界
- 每类能力建立唯一主归属，避免继续使用总包兜底

### 3. 协议与技术耦合收口

- `SseEmitter` 只留在 `trigger`
- `OkHttpClient`、JDBC provider、tool runtime、file gateway 等通过显式 seam 下沉到 `infrastructure`
- `domain` 只保留语义 contract 与请求/结果模型

### 4. 守卫与文档同步

- 复用并扩充现有边界测试，而不是另起一套审计机制
- 根级和模块级 `CLAUDE.md` 必须与最终边界保持一致

### 5. 兼容桥控制

- 允许短期 bridge，但必须写明依赖方、保留原因和删除时机
- 最终交付前默认目标是清空旧 `service` 与旧 `reactor` 主路径

## Phase 2: Implementation Strategy

### User Story 1 - 开发者可在清晰边界内扩展 Agent 能力

- 清理 `domain/agent/service` 剩余根接口与旧编排实现
- 让控制器、任务入口和装配只依赖 `case` 暴露接口
- 统一应用层会话流抽象与执行策略选择路径

### User Story 2 - 维护者可按子域理解和演进 Agent 核心能力

- 建立 `runtime / ledger / memory / rag / role` 五个子域目录与职责说明
- 分批迁移旧 `reactor` 目录中的运行时、账本、记忆、RAG、角色相关能力
- 将技术执行器与配置装配逐步从 `domain` 抽离

### User Story 3 - 交付团队可用守卫与文档锁定最终边界

- 扩充 `AgentContextConvergenceBoundaryTest` 与现有运行时/持久化边界测试
- 更新根级与模块级 `CLAUDE.md`
- 用目录扫描和关键字符串扫描锁定最终边界，防止后续回流

## Post-Design Constitution Check

- [x] DDD 边界保持清晰：应用编排在 `case`，领域语义在 `domain`，技术执行器在 `infrastructure`，协议/装配分别在 `trigger/app`
- [x] 已优先复用现有 `case`、会话流抽象、execution ledger、history replay、session memory 与边界测试
- [x] 关键改动点均有自动化编译、测试或扫描验证路径
- [x] 已覆盖流式链路、运行时查找、技术执行器与兼容桥控制等风险点
- [x] 当前复杂度来自棕地收敛的必要最小闭环，不需要额外复杂度豁免

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | N/A | N/A |
