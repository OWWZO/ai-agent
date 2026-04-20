# Implementation Plan: 对齐 free-code 的请求前会话压缩

**Branch**: `[007-freecode-session-compaction]` | **Date**: `2026-04-20` | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/007-freecode-session-compaction/spec.md`

## Summary

将 `REACT / PLAN_SOLVE` 的会话压缩从“请求完成后异步刷新”改为“请求入口先做压缩决策，再开始本轮主执行”。核心技术路线是复用现有会话账本与 `006-session-context-memory` 的 rich transcript 能力，在请求前根据真实工作记忆体量判断是否触发压缩；当需要压缩时，使用模型生成 free-code 风格的结构化会话记忆，按版本化快照追加写入 `ai_agent_session_memory`，并按 token 预算保留最近原始上下文窗口与工具链完整性。

## Technical Context

**Language/Version**: Java 17（仅后端主链路，本期不改 `ui/`、`reactor-tool/`、`reactor-client/`）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、现有 Reactor `LLM`/`ChatClient` 装配能力、OkHttp SSE、既有 rich transcript 组装链路  
**Storage**: MySQL 既有 `ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`、`ai_agent_session_memory`；其中 `ai_agent_session_memory` 演进为同一 `session_id` 的多版本快照  
**Testing**: `mvn test -pl ai-agent-station-study-app -DskipTests=false`，重点覆盖 session memory、send-stream preflight、schema/mapper 回归、history reopen 与 guard 相关测试  
**Target Platform**: Spring Boot `send-stream -> session memory preflight -> AgentRequest -> RootNode/Step1SopRecallAndPrepareNode` 后端执行链  
**Project Type**: Maven 多模块后端功能改造  
**Performance Goals**: 非压缩路径维持“最新快照 + 批量已完成消息 + 批量事件”读取形态，不引入 N+1；触发压缩时最多增加 1 次结构化记忆模型调用和 1 次版本快照插入；最新快照查询保持按 `session_id` 索引命中  
**Constraints**: 严守 DDD 分层边界；请求前压缩必须在占位消息插入前完成；`CHAT` 模式保持现状；压缩快照 append-only；最近窗口以 token 预算为主且必须保持工具链完整；超长输出默认通过稳定引用保留；压缩失败时遵守“硬上限内降级继续，否则拒绝”  
**Scale/Scope**: 主要影响 `ai-agent-station-study-domain` 与 `ai-agent-station-study-app`，涉及 session memory 服务接口、请求入口时序、LLM 结构化摘要生成、`ai_agent_session_memory` schema/mapper、配置绑定与测试夹具

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更是否遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界？
- [x] 是否优先复用了现有 Agent、Tool、Prompt、RAG、DAO、配置装配能力？
- [x] 是否为每个关键改动点定义了可执行验证方式？
- [x] 是否将外部调用、流式链路、任务编排的异常与可观测性纳入方案？
- [x] 若提高了复杂度，是否在 `Complexity Tracking` 中给出合理说明？

## Project Structure

### Documentation (this feature)

```text
specs/007-freecode-session-compaction/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── send-stream-session-compaction.md
│   ├── session-memory-rebuild.md
│   └── session-memory-storage.md
└── tasks.md
```

### Source Code (repository root)

```text
ai-agent-station-study-domain/
├── src/main/java/org/wwz/ai/domain/agent/reactor/config/
├── src/main/java/org/wwz/ai/domain/agent/reactor/entity/
├── src/main/java/org/wwz/ai/domain/agent/reactor/mapper/
├── src/main/java/org/wwz/ai/domain/agent/reactor/model/
├── src/main/java/org/wwz/ai/domain/agent/reactor/service/
├── src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/
└── src/main/java/org/wwz/ai/domain/agent/reactor/service/support/
ai-agent-station-study-app/
├── src/main/resources/db/
├── src/main/resources/mybatis/mapper/
└── src/test/java/org/wwz/ai/test/domain/
ai-agent-station-study-trigger/
ui/
reactor-tool/
reactor-client/
```

**Structure Decision**: 本期限定在后端主链路交付。`domain` 承担请求前 compaction decision、结构化会话记忆生成、最近窗口裁剪、失败熔断与 working memory 重建；`app` 承担 schema、Mapper XML 与自动化测试；`trigger`、`ui`、`reactor-tool`、`reactor-client` 不变。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 实现请求前 compaction preflight、结构化 session memory 生成、版本快照选择、token 预算窗口、失败节流与 AgentRequest 装配 |
| `ai-agent-station-study-app` | modify | 调整 `schema.sql`、`ai_agent_session_memory_mapper.xml`、测试夹具与回归测试 |
| `ai-agent-station-study-infrastructure` | none | 当前会话记忆 DAO 接口定义与使用都在 `domain/app`，本期无需新增 infrastructure 适配层 |
| `ai-agent-station-study-trigger` | none | 对外 HTTP 接口保持兼容，只调整内部 preflight 行为 |
| `ui` | none | 不改历史展示与前端交互 |
| `reactor-tool` / `reactor-client` | none | 不改 Python 子系统，仅消费既有工具结果与稳定引用 |

## Layer Boundary Notes

- `domain` 负责 compaction decision、结构化会话记忆 prompt/LLM 调用、snapshot 版本选择、最近窗口保留策略、失败熔断状态与 working memory 重建；不把这些规则下沉到 Controller 或 Mapper XML。
- `app` 只负责 `ai_agent_session_memory` 的 DDL/Mapper XML 演进、测试数据与回归用例；不承载业务判断。
- `trigger` 层继续只暴露现有 `/api/agent/message/send-stream`，新的请求前压缩作为 `domain` 预处理能力被调用。
- `ai_agent_message` / `ai_agent_message_event` 继续作为会话账本真相源；`ai_agent_session_memory` 演进为 append-only 版本快照，不引入新表。
- 结构化会话记忆采用 free-code 风格固定 section schema，但通过本项目现有 LLM 装配能力生成，不把 free-code 的文件型 memory 目录直接照搬进来。

## Data / Config / Contract Changes

- **Database**: `ai_agent_session_memory` 从单行 upsert 改为 append-only 版本快照；需要移除 `uk_session_id`，新增最新快照/历史分析索引；运行时查询由 `queryBySessionId` 演进为“按 `session_id` 取最新有效快照”和“按 `session_id` 查历史版本”
- **Config**: 保留 `session-memory.enabled`、`compaction-threshold-tokens`、`summary-max-length`；新增或演进 `hard-limit-tokens`、`recent-window-max-tokens`、`recent-window-min-messages`、`max-consecutive-failures` 等请求前压缩配置；`recent-window-turns` 退居兼容或测试兜底角色
- **Contract**: 外部 send-stream 请求/响应结构保持兼容，但在 preflight 阶段新增“压缩拒绝”分支；内部新增请求前 compaction decision / rebuild contract、版本化 snapshot storage contract
- **Compatibility**: 老数据中只有单条 snapshot 时，最新快照查询应兼容；历史重开默认读取最新快照；旧测试和旧会话中不存在结构化 markdown memory 时，仍应安全退化为现有摘要文本和 rich transcript 组合

## Verification Plan

- **Java**:
  - `mvn test -pl ai-agent-station-study-app -DskipTests=false`
  - 重点回归与扩展：
    - `org.wwz.ai.test.domain.ConversationHistoryPersistenceTest`
    - `org.wwz.ai.test.domain.sessionmemory.SessionMemoryCompactionServiceTest`
    - `org.wwz.ai.test.domain.sessionmemory.SessionWorkingMemoryAssemblerTest`
    - `org.wwz.ai.test.domain.sessionmemory.SessionMemoryReopenResumeTest`
    - `org.wwz.ai.test.domain.sessionmemory.AgentStreamPersistServiceSessionGuardTest`
    - 新增版本快照/请求前 preflight/circuit breaker 相关测试
- **UI**: N/A
- **Python**: N/A
- **Manual**:
  - 低阈值配置下验证第二轮请求在进入主执行前先触发 compaction
  - 验证 `ai_agent_session_memory` 同一 `session_id` 产生多条版本快照且运行时读取最新一条
  - 验证重开会话后仍使用最新快照 + 最近窗口
  - 验证超长输出只保留关键结果与稳定引用
  - 验证 compaction 失败时的降级继续 / 拒绝分支不污染会话账本

## Phase 0: Research Summary

- 已确定本期关键未知点：请求前触发时机、free-code 风格结构化 memory 格式、append-only snapshot 存储、token 预算窗口、失败熔断与硬上限策略、LLM 接入复用方式
- 研究结论已固化到 [research.md](./research.md)，没有遗留 `NEEDS CLARIFICATION`
- 研究结果直接支撑 Phase 1 设计与 Phase 2 任务拆分

## Phase 1: Design Decisions

### 1. Request Entry Preflight

- 在 `AgentStreamPersistServiceImpl` 中把非 `CHAT` 会话的 compaction decision 前移到占位消息插入之前
- 新增统一的 request-preflight service/entry method，负责：加载最新 snapshot、评估真实上下文体量、必要时生成新 snapshot、回传 working memory 或拒绝结果
- `persistTurnAndEvents()` 不再承担“完成后立即生成新 snapshot”的主职责，避免与请求前压缩双写

### 2. Structured Session Memory Format

- `summary_text` 升级为 free-code 风格的结构化 markdown memory，采用固定 sections：
  - `Session Title`
  - `Current State`
  - `Task specification`
  - `Files and Functions`
  - `Workflow`
  - `Errors & Corrections`
  - `Codebase and System Documentation`
  - `Learnings`
  - `Key results`
  - `Worklog`
- `facts_json` 退化为兼容性投影或分析索引，不再作为主摘要来源
- `artifact_refs_json` 继续由确定性链路维护，不依赖模型自由生成

### 3. Snapshot Versioning

- `ai_agent_session_memory` 改为 append-only 版本快照
- 运行时统一读取“最新有效版本”，历史版本保留用于分析
- DAO/Mapper 从 `upsert/queryBySessionId` 演进到 `insert/queryLatest/queryHistory` 语义

### 4. Preserved Recent Window

- 最近窗口以 token 预算为主，不再以固定 `recent-window-turns` 作为主策略
- 参考 free-code `sessionMemoryCompact.ts` 的规则，保留：
  - 最小真实消息窗口
  - 工具调用/结果配对完整性
  - 不拆断同一逻辑消息的关键片段
- 超长输出只保留关键结果和稳定引用

### 5. Failure Guardrails

- 请求前 compaction 失败时：
  - 若原始上下文仍低于硬上限，降级继续
  - 若仍超限或上下文损坏，直接拒绝且不插入占位消息
- 增加按 `sessionId` 维护的轻量级内存熔断状态，限制连续失败重试次数

### 6. LLM Integration Strategy

- 新增专用 `SessionMemorySummaryGenerator` / prompt builder，复用现有 Reactor `LLM`/`ChatClient` 装配能力
- 不直接复用 `SummaryAgent` 现有 `taskSummary + $$$file markers` 协议，因为它的输出契约是“任务总结”，不是“结构化 session memory”
- 结构化 memory 生成输入由“上一版 memory + 新增 completed transcript + 稳定引用”组成

## Phase 2: Implementation Strategy

### User Story 1 - 请求开始前自动判断并压缩

- 调整 `AgentStreamPersistServiceImpl` 时序，让 preflight compaction 发生在占位消息插入前
- 扩展 `IAgentSessionMemoryService`，拆出“请求前准备工作记忆 / compaction decision”能力
- 让 threshold/hard-limit 判断基于真实 working memory 体量，而不是 post-turn 字符串估算

### User Story 2 - 会话压缩摘要升级为结构化工作记忆

- 新增结构化 session memory prompt builder 与 LLM generator
- 重写 `SessionMemoryPromptFormatter` / `SessionMemoryCompactionService` 的摘要策略，使 `summary_text` 成为结构化 markdown memory，而不是字符串拼接流水账
- 保持 `artifact_refs_json` 的确定性归档与恢复

### User Story 3 - 压缩后保留最近真实上下文与调用关系

- 以 token 预算实现 preserved recent window 选择
- 保证 tool-use/tool-result、重复调用、关键推理片段在压缩后仍保持顺序与配对关系
- 在 history reopen 和在线续聊两条路径上统一使用“最新 snapshot + 最近窗口”语义

## Post-Design Constitution Check

- [x] 设计仍遵守 DDD 分层边界，未把业务判断放进 `trigger/app`
- [x] 复用了现有 Reactor LLM/ChatClient、session ledger、rich transcript 与 DAO/Mapper 能力
- [x] 为 preflight、storage、LLM summary、window selection、guardrail 都定义了验证路径
- [x] 已纳入异常兜底、日志与回退策略，避免 silent failure
- [x] 复杂度提升集中在必要的请求前压缩与快照版本化，理由清晰

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | N/A | N/A |
