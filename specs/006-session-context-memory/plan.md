# Implementation Plan: ReAct / PlanSolve 会话上下文记忆

**Branch**: `[006-session-context-memory]` | **Date**: 2026-04-19 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/006-session-context-memory/spec.md`

## Summary

在不引入 Spring AI ChatMemory、也不使用 Markdown 记忆文件的前提下，为 `REACT` 与 `PLAN_SOLVE` 建立一套基于 MySQL 的会话上下文记忆机制。整体方案复用现有 `ai_agent_conversation / ai_agent_message / ai_agent_message_event` 作为会话账本，新增单会话唯一的摘要快照表，把 free-code 的 `mutableMessages + compact boundary + summary + recent window` 映射为本项目的“工作记忆重建器 + 摘要快照 + 最近详细窗口 + 文件产物恢复”。每次请求开始前，后端按 `sessionId` 重建 `SessionWorkingMemory`，将摘要注入 `history_dialogue`，将最近详细消息预装入 Agent 的 `Memory.messages`，并把稳定文件引用恢复为 `AgentContext.productFiles`；每轮结束后仅对 `COMPLETED` 消息更新摘要快照，`ERROR / FORCE_STOPPED` 只保留展示历史，不进入后续记忆。

## Technical Context

**Language/Version**: Java 17（Spring Boot 多模块主链路）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis-Plus 3.5.14、OkHttp、MySQL 8、现有 ReAct / PlanSolve Agent 框架  
**Storage**: MySQL（既有 `ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`，新增会话记忆摘要快照表）  
**Testing**: `mvn test -pl ai-agent-station-study-app` 下的领域/持久化测试；补充会话记忆装配、压缩、并发守卫、模式守卫相关测试；手工 SSE 续聊冒烟验证  
**Target Platform**: Spring Boot SSE 后端  
**Project Type**: 棕地 Maven 多模块后端增量改造  
**Performance Goals**: 单次续聊的上下文重建应收敛为“1 次会话查询 + 1 次摘要快照查询 + 1 次最近消息窗口查询 + 1 次事件批量查询”；长会话进入模型的记忆载荷必须稳定受阈值控制，不能无上限拼接全部历史  
**Constraints**: 严守 DDD 边界；记忆真相源必须是 MySQL；必须复用现有会话与历史详情体系；`REACT / PLAN_SOLVE` 共用一套记忆策略；同 `sessionId` 禁止并发与模式切换；稳定文件引用要恢复为真实可用文件上下文，而不是只保留文字  
**Scale/Scope**: 主要影响 `ai-agent-station-study-domain` 与 `ai-agent-station-study-app`，并对 `ai-agent-station-study-trigger` 做薄适配层改动以继续复用现有 `/send-stream` 与 `/stop` 接口；不新增前端页面与新接口；`CHAT` 模式不在本次范围内

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界；记忆装配、压缩、守卫与 Agent 预装配逻辑全部落在 `domain`，数据库结构与 Mapper 落在 `app`，`trigger` 保持入口代理职责
- [x] 优先复用了现有 Agent、Prompt、会话三表、历史详情装配、`artifactRefs` 归一化与 `AgentContext.productFiles` 机制，没有引入新的记忆插件或并行历史子系统
- [x] 已为关键改动点定义可执行验证方式，包括会话续聊、长会话压缩、历史重开、并发冲突、模式冲突、异常轮次排除等测试与手工路径
- [x] 已将 SSE 流式链路、异常终态、文件恢复、并发写保护与压缩可观测性纳入方案
- [x] 本方案复杂度来自“在现有自研 Agent 框架上复刻 free-code 的上下文记忆效果”，但未引入超出需求的新层次，`Complexity Tracking` 无需额外豁免

Phase 1 设计复核结果：仍然通过，无额外宪章例外。

## Project Structure

### Documentation (this feature)

```text
specs/006-session-context-memory/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── send-stream-session-memory.md
│   ├── session-memory-rebuild.md
│   └── session-memory-storage.md
└── tasks.md
```

### Source Code (repository root)

```text
ai-agent-station-study-domain/
├── src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/
├── src/main/java/org/wwz/ai/domain/agent/reactor/service/support/
├── src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/
└── src/main/java/org/wwz/ai/domain/agent/service/execute/
   ├── react/step/
   └── planexecute/step/
ai-agent-station-study-app/
├── src/main/resources/db/
├── src/main/resources/mybatis/mapper/
└── src/test/java/org/wwz/ai/test/domain/
ai-agent-station-study-trigger/
└── src/main/java/org/wwz/ai/trigger/http/agent/
```

**Structure Decision**: 仅在现有 `domain + app + trigger` 链路内增量实现。`domain` 新增会话记忆重建、压缩、守卫、文件恢复与 Agent 预装配能力；`app` 负责表结构、Mapper XML 与测试夹具；`trigger` 继续复用现有 `/api/agent/message/send-stream`、`/api/agent/message/stop` 与历史详情接口，但只承担参数绑定、协议适配与服务委派，不额外新增 memory 专用入口或业务判断。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 新增工作记忆重建器、摘要压缩策略、会话并发/模式守卫、文件恢复、Agent Memory 预装配，并修复 `sessionId=requestId` 的错误传播 |
| `ai-agent-station-study-app` | modify | 新增会话记忆快照表、Mapper XML、配置项与相关测试 |
| `ai-agent-station-study-trigger` | modify | 继续复用现有 SSE 发送、停止与历史详情接口，仅补齐薄适配层委派，不把会话记忆、压缩或停止业务判断放进 Controller |
| `ai-agent-station-study-infrastructure` | none | 本次不新增外部网关或额外仓储层，沿用项目当前 MyBatis/DAO 组织方式 |
| `ui` | none | 当前会话列表、详情与续聊入口已能复用 `sessionId`，本次记忆能力全部由后端补齐 |
| `reactor-tool` / `reactor-client` | none | 不涉及 Python 工具协议变更 |

## Layer Boundary Notes

- `domain`
  - 负责从持久化账本重建 `SessionWorkingMemory`
  - 负责压缩摘要生成、边界推进、文件恢复、并发守卫与模式守卫
  - 负责把记忆注入 `history_dialogue`、`AgentContext.productFiles` 与 `BaseAgent.memory`
- `app`
  - 负责新增记忆快照表、Mapper XML、配置项和测试资源
  - 负责为会话记忆查询提供必要索引与 SQL
- `trigger`
  - 继续只负责接收 `/send-stream`、`/stop` 请求并委派服务
  - 只做参数绑定、SSE/HTTP 协议适配和结果返回
  - 不在 Controller 中拼装历史记忆、判断会话模式、查询快照或维护停止状态
- 明确禁止
  - 在 `trigger` 增加会话记忆业务判断
  - 在 `ui` 额外新增一套 memory 专用续聊协议
  - 在 `domain` 直接依赖 Markdown 文件或 Spring AI ChatMemory 插件

## Data / Config / Contract Changes

- **Database**:
  - 保留 `ai_agent_conversation` 作为会话主档与模式归属源
  - 保留 `ai_agent_message` 作为每轮消息账本，同时复用其 `status` 做并发执行守卫
  - 保留 `ai_agent_message_event` 作为稳定 `artifactRefs` 与最终可见细节块来源
  - 新增 `ai_agent_session_memory` 作为每个 `sessionId` 唯一的当前生效摘要快照
  - 视实现需要补充会话消息状态索引与快照唯一索引
- **Config**:
  - 新增会话记忆开关、压缩阈值、最近窗口轮数、摘要长度上限等配置
  - 继续复用现有 prompt 模板中的 `{{history_dialogue}}`
- **Contract**:
  - 对外仍使用现有 `/api/agent/message/send-stream` SSE 协议
  - 历史详情接口保持不变，但其 `artifactRefs` 将被会话记忆装配复用
  - 新增内部 `SessionWorkingMemory` / `AgentSessionMemorySnapshot` 装配契约
- **Compatibility**:
  - 老会话即使还没有摘要快照，也应能通过最近已完成轮安全退化续聊
  - `CHAT` 模式维持原状，不强行复用本次记忆机制
  - 旧历史详情能力与会话列表接口不破坏

## Verification Plan

- **Java**:
  - 扩展或新增针对以下能力的测试：
    - `sessionId` 传播修复
    - 工作记忆重建
    - `SessionWorkingMemory` 作为唯一请求级聚合视图的装配约束
    - 压缩边界推进
    - `ERROR / FORCE_STOPPED` 轮次排除
    - 同会话并发冲突
    - 同会话模式切换冲突
    - 历史文件引用恢复
    - 有快照续聊场景下遵守“1 次会话查询 + 1 次快照查询 + 1 次最近窗口查询 + 1 次事件批量查询”的低查询预算，不产生按消息逐条恢复事件的 N+1 查询
    - 超长样本压缩后 `estimatedTokens` 或等价字符载荷不高于未压缩全量历史的 40%，且仍保留最近至少 2 轮完整 `user / assistant` 消息
  - 回归现有：
    - `StepReactNodeRoutingTest`
    - `ConversationHistoryPersistenceTest`
    - `ConversationHistoryArtifactTest`
    - `ConversationHistoryDetailApiTest`
- **UI**: N/A，本次不计划改动前端代码；仅做现有 UI 续聊冒烟验证
- **Python**: N/A
- **Manual**:
  - `REACT` 与 `PLAN_SOLVE` 是否都能在同一 `sessionId` 两轮续聊中继承约束
  - `REACT` 与 `PLAN_SOLVE` 的长会话是否都只带“摘要 + 最近窗口”，且载荷压缩比例满足目标
  - `REACT` 与 `PLAN_SOLVE` 的历史重开后是否都能恢复上下文与文件
  - 同会话改模式是否被拒绝
  - 同会话并发续聊是否被拒绝
  - `ERROR / FORCE_STOPPED` 后续续聊是否不受污染

## Complexity Tracking

无宪章违规项，本节留空。
