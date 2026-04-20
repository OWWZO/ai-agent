# Implementation Plan: ReAct / PlanSolve 完整链路会话上下文复原

**Branch**: `[006-session-context-memory]` | **Date**: `2026-04-19` | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-session-context-memory/spec.md`

## Summary

在不修改现有 `ai_agent_session_memory` 压缩链路、不新增 MySQL 表结构的前提下，重构 `REACT / PLAN_SOLVE` 的续聊上下文复原。核心做法是把 `ai_agent_message_event` 从“仅历史展示源”升级为“工作上下文重建源”，让 `ai_agent_session_memory` 继续只承担“已压缩历史边界 + 摘要来源”，而对边界之后的已完成轮次统一从 `ai_agent_message + ai_agent_message_event` 恢复完整 transcript，并把这些信息以 richer preloaded messages + `history_dialogue` + `sessionFiles` 的组合注入现有 Agent 执行链。

## Technical Context

**Language/Version**: Java 17（仅后端主链路，本期不改 `ui/`、`reactor-tool/`、`reactor-client/`）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、OkHttp SSE、现有 ReAct / PlanSolve Agent 框架  
**Storage**: MySQL 既有 `ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`、`ai_agent_session_memory`；本期无新增表/列  
**Testing**: `mvn test -pl ai-agent-station-study-app -DskipTests=false`，重点覆盖 session memory / history replay / stream guard 相关测试  
**Target Platform**: Spring Boot 后端 `send-stream -> AgentRequest -> RootNode/Step1SopRecallAndPrepareNode -> Agent memory` 执行链  
**Project Type**: Maven 多模块后端功能改造  
**Performance Goals**: 非 `CHAT` 续聊的工作记忆重建保持批量查询模型，目标仍为“1 次快照查询 + 1 次已完成消息查询 + 1 次事件批量查询”，不引入 N+1 事件读取  
**Constraints**: 严守 DDD 边界；优先复用现有会话、事件、历史回放、文件恢复链路；不修改压缩/摘要快照逻辑；`ai_agent_session_memory` 不是边界后 turn 的主记忆来源；不改 `CHAT` 模式；长输出正文默认走引用或结构化结果片段  
**Scale/Scope**: 主要影响 `ai-agent-station-study-domain` 与 `ai-agent-station-study-app`，预计涉及工作记忆装配、内部请求消息模型、事件查询、Mapper XML、测试夹具与回归测试

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更遵守 `types/api/domain/infrastructure/trigger/app` 职责边界：业务语义与上下文装配留在 `domain`，SQL/Mapper 落在 `app`
- [x] 优先复用了现有 Agent、Tool、Prompt、DAO、配置装配能力：继续沿用 `AgentStreamPersistServiceImpl`、`SessionWorkingMemoryAssembler`、`ConversationEventPayloadNormalizer`、`ConversationReplayAssembler`
- [x] 为关键改动点定义了可执行验证方式：Assembler、guard、history reopen、event persistence、history replay 均有明确测试/手工验收路径
- [x] 已将外部调用、流式链路、任务编排的异常与可观测性纳入方案：守卫失败、事件缺失、旧格式 payload、长输出引用化均有兜底
- [x] 当前方案未引入必须额外说明的复杂度违例：不新增持久化模型，不重写 compaction pipeline

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
├── src/main/java/org/wwz/ai/domain/agent/reactor/config/
├── src/main/java/org/wwz/ai/domain/agent/reactor/model/
├── src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/
├── src/main/java/org/wwz/ai/domain/agent/reactor/service/support/
└── src/main/java/org/wwz/ai/domain/agent/service/execute/
ai-agent-station-study-app/
├── src/main/resources/db/
├── src/main/resources/mybatis/mapper/
└── src/test/java/org/wwz/ai/test/domain/
ai-agent-station-study-trigger/
ui/
reactor-tool/
reactor-client/
```

**Structure Decision**: 本期限定在后端主链路完成闭环。`domain` 承担 transcript 恢复、内部消息语义扩展和 Agent 注入；`app` 承担 Mapper XML 与回归测试；`trigger`、`ui`、Python 子系统保持不变。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 重建工作记忆、扩展 `AgentRequest.Message` / runtime transcript 模型、将 event ledger 转为可预装的上下文消息、保持 ReAct / PlanSolve 节点注入一致 |
| `ai-agent-station-study-app` | modify | 调整 MyBatis Mapper XML 的事件批量查询语义，补齐测试夹具与回归测试 |
| `ai-agent-station-study-infrastructure` | none | 当前数据访问接口定义在 `domain`，本期无需新增 infrastructure 适配层 |
| `ai-agent-station-study-trigger` | none | 外部 HTTP API 与 SSE 返回结构保持不变 |
| `ui` | none | 历史展示链路继续复用现有 replay 语义，本期不改前端 |
| `reactor-tool` / `reactor-client` | none | 工具执行侧不变，本期仅消费其既有落库结果 |

## Layer Boundary Notes

- `domain` 持有 transcript 恢复规则、event -> context block 转换、工作记忆整形、Agent 预装消息构造，不把这些判断下沉到 Controller 或 Mapper XML
- `app` 只负责 `ai_agent_message` / `ai_agent_message_event` 的批量查询 SQL、现有 schema 对照与测试资源
- `trigger` 不新增接口，不暴露新的外部请求参数；`send-stream` 的行为变化完全体现在内部重建与注入逻辑
- `ai_agent_session_memory` 继续只作为“已压缩历史的边界和摘要来源”，不承担边界后 turn 的主重建来源；`SessionMemoryCompactionService` 保留但不在本期变更
- `ConversationReplayAssembler` 与新工作记忆装配共享同一份规范化 payload 语义，但各自保持“展示装配”和“运行时上下文装配”的单一职责

## Data / Config / Contract Changes

- **Database**: 不做 DDL 变更；调整事件读取契约，使工作记忆装配能够按 `message_id + seq_no` 批量读取完整 final events，而不是只读取带 `artifactRefs` 的事件
- **Config**: 不新增配置项；继续复用现有 `autobots.autoagent.session-memory.*` 开关和窗口参数，但本期不改其含义
- **Contract**: 外部 `/api/agent/message/send-stream` 请求/响应保持兼容；内部 `AgentRequest.Message` 采用“保留 `content` 兼容文本 + 新增 `messageType`、`toolCalls`、`toolCallId`、`artifactRefs`、`referenceOnly`、`files`”的结构化扩展方案，再由 `RootNode/Step1SopRecallAndPrepareNode.convertMessages` 映射到现有 `agent.dto.Message`
- **Payload Mapping**: `ConversationEventPayloadNormalizer` 产出的 canonical payload 必须定义确定性的 `event -> block` 规则；`toolUseId` 按 `payload.toolUseId -> payload.toolCall.id -> payload.tool.id -> messageId:seqNo` 顺序提取，`tool_result` 优先按同一 `toolUseId` 配对，缺失时再按同轮最近未闭合调用回退
- **Compatibility**: 无 event 的旧会话继续退化为 `query / response`；已被快照覆盖的旧历史继续沿用现有摘要；`CHAT` 模式路径与现有历史详情接口保持不变

## Verification Plan

- **Java**:
  - `mvn test -pl ai-agent-station-study-app -DskipTests=false`
  - 重点回归现有：
    - `org.wwz.ai.test.domain.sessionmemory.SessionWorkingMemoryAssemblerTest`
    - `org.wwz.ai.test.domain.sessionmemory.SessionMemoryReopenResumeTest`
    - `org.wwz.ai.test.domain.sessionmemory.AgentStreamPersistServiceSessionGuardTest`
    - `org.wwz.ai.test.domain.ConversationHistoryArtifactTest`
  - 需要新增/改造：
    - event -> transcript block 转换测试
    - `AgentStreamPersistServiceImpl.buildWorkingMemoryMessages` 的 tool chain 预装测试
    - old event / missing event / long output reference-only 回退测试
- **UI**: N/A
- **Python**: N/A
- **Manual**:
  - 同 `sessionId` 连续两轮工具链续聊
  - 历史重开后继续引用上一轮 MCP / skilltool / 命令 / 文件结果
  - 长报告正文仅通过稳定引用复用
  - `mode_conflict` / `session_busy` / `FORCE_STOPPED` 排除验证

## Phase 0: Research Summary

- 已确认本期没有新的业务歧义，也不需要额外外部调研
- 当前阻塞点是代码和数据契约不一致：事件表里有结构化 transcript，但工作记忆仅读 `query + response + artifactRefs`
- 研究结论已固化到 [research.md](./research.md)，可直接支撑 Phase 1 设计

## Phase 1: Design Decisions

### 1. Storage / Query Strategy

- 继续以 `ai_agent_message` + `ai_agent_message_event` 作为 transcript 真相源
- 继续读取 `ai_agent_session_memory` 作为已压缩历史边界和摘要来源，但不改其生成逻辑
- 工作记忆装配改为“边界后已完成消息 + 全量 final events 批量读取 + payload 规范化”

### 2. Runtime Model Strategy

- 把当前 `SessionTurnMemory(userMessage + assistantMessage + artifactRefs)` 升级为“turn + ordered transcript blocks”的运行时模型
- 新增 `TranscriptContextBlock` / `TranscriptBlockType`，明确 `toolUseId`、关键入参、结构化结果、稳定引用、`referenceOnly` 等字段
- `AgentRequest.Message` 选择结构化扩展方案而不是 markdown 拼接方案：`content` 继续作为兼容预览文本，`messageType/toolCalls/toolCallId/artifactRefs/referenceOnly/files` 承载 richer transcript 语义
- 长输出正文不做全文预装，只保留关键结果与稳定引用

### 2A. Event Mapping Contract

- 所有运行时装配统一消费 `ConversationEventPayloadNormalizer` 的 canonical payload，不在 working memory 链路再造第二套解析器
- `event_type=plan_thought/tool_thought` 默认映射为 `ASSISTANT_THOUGHT`
- 带 tool call 结构的 payload 映射为 `TOOL_USE`，并提取稳定 `toolUseId / toolName / arguments`
- `tool_result / deep_search / code / file / data_analysis / browser ...` 等结果型事件映射为 `TOOL_RESULT` 或 `ARTIFACT_REFERENCE`
- 对 `deepsearch report`、超长 `stdout/stderr`、大 `diff` 等长输出，统一标记 `referenceOnly=true`，仅保留关键结果和稳定引用

### 2B. AgentRequest Message Strategy

- `AgentRequest.Message` 保留 `role + content` 兼容当前链路，但不再以纯字符串表达全部上下文
- `messageType` 用于标识 `assistant_thought / assistant_answer / tool_use / tool_result / artifact_reference`
- `toolCalls` 复用现有 Agent message tool-call 结构；`toolCallId` 复用现有 tool-result 关联方式
- `artifactRefs/files/referenceOnly` 让节点层能区分“可直接内联的信息”和“仅引用复用的信息”

### 3. Injection Strategy

- `historyDialogue` 继续负责快照摘要、关键事实和恢复文件摘要
- `sessionFiles` 继续负责稳定文件对象恢复
- `messages` 改为承载最近直接回放窗口内的 richer transcript chain，用于真正参与 ReAct / PlanSolve 推理

### 4. Compatibility Strategy

- 外部 API 不变
- 旧事件或缺事件 turn 自动降级
- 已压缩历史不回溯展开
- 当前 compaction/summary 相关测试只要求“不回归”，不在本期做行为变化

## Phase 2: Implementation Strategy

### User Story 1 - 同会话延续完整执行链

- 放宽 `IAgentMessageEventDao` / Mapper XML 的事件读取范围，支持获取工作记忆所需的完整 final events
- 增加 event -> transcript block 转换器，统一解析 `plan_thought`、`tool_thought`、`tool_result`、`deep_search`、`file`、`code`、`data_analysis` 等事件，并收口 `toolUseId`/reference-only 规则
- 重构 `SessionWorkingMemoryAssembler` 与 `AgentStreamPersistServiceImpl.buildWorkingMemoryMessages`，避免仅保留 `query + response`

### User Story 2 - 历史重开仍可恢复完整上下文

- 让 `rebuildWorkingMemory` 与历史重开共用同一条数据库装配路径
- 保证 `ConversationReplayAssembler` 与 working memory assembler 共享同样的 payload 规范化前提
- 补充 reopen 场景下的 MCP / 文件 / skilltool / 命令结果恢复测试

### User Story 3 - 当前可直接回放窗口内的长链路不退化

- 明确 reference-only 规则：`deepsearch report` 正文、超长 `stdout/stderr`、大 `diff` 等仅保留关键结果与稳定引用
- 保证对仍在直接回放窗口内的 turn，不再退化成 summary-only
- 保证对已被快照覆盖的 turn，继续沿用现有摘要，不引入 compaction 重构

## Post-Design Constitution Check

- [x] DDD 边界仍然清晰，未把业务判断塞入 `trigger/app`
- [x] 仍然优先复用了现有 Agent/Prompt/DAO/历史回放/文件恢复能力
- [x] 每个关键设计点都有对应验证路径
- [x] 异常、长输出、旧格式数据、并发冲突都有明确兜底
- [x] 未引入额外复杂度违例，无需填写 `Complexity Tracking`

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | N/A | N/A |
