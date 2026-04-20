# Research: 对齐 free-code 的请求前会话压缩

## Decision 1: 请求前压缩必须在占位消息插入前完成

- **Decision**: 在 `AgentStreamPersistServiceImpl` 的非 `CHAT` 请求链路中，把 session memory compaction preflight 放到占位消息插入之前执行；只有 preflight 通过后，才允许继续插入占位消息和启动下游主执行。
- **Rationale**: 当前实现先插占位消息再 build request，如果 compaction 在这之后失败，会留下脏占位 turn。free-code 的 `query.ts` 也是在真正调用主模型前完成 microcompact/autocompact 判断，因此请求入口前移是对齐其核心行为的必要条件。
- **Alternatives considered**:
  - 保持当前“先插占位消息再压缩”：失败时会污染会话账本。
  - 把 preflight 和占位消息插入并行执行：会引入竞态，且难以保证拒绝路径无副作用。

## Decision 2: 触发时机从“请求完成后刷新”改为“下一次请求开始前判断”

- **Decision**: 取消 `persistTurnAndEvents()` 对 `refreshSessionMemory()` 的主触发职责，改为在下一次请求开始前根据真实 working memory 体量判断是否需要 compaction；请求完成后只保留 turn/event 落库，不主动生成新 snapshot。
- **Rationale**: 用户明确要求压缩不能等到本轮结束后才做。free-code 的 `shouldAutoCompact()`/`trySessionMemoryCompaction()` 都是在下一次 query 发送前进行，这样被压缩保护的是“真正要进模型的那一轮请求”。
- **Alternatives considered**:
  - 继续保留完成后刷新作为主链路：与规格冲突，也无法保护当前请求。
  - 请求完成后和请求开始前双触发：会造成重复 compaction 和版本混乱。

## Decision 3: 结构化会话记忆采用 free-code 风格固定 section markdown

- **Decision**: 将 `summary_text` 升级为 free-code session memory 风格的固定结构 markdown，至少包含：
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
- **Rationale**: free-code 的价值不在“写一段总结”，而在于用固定 section 管理可继续工作的 session memory。相比当前 `SessionMemorySummaryBuilder` 的字符串拼接，这种结构更适合多轮任务推进、错误修正和结果追溯。
- **Alternatives considered**:
  - 继续字符串拼接旧摘要：无法满足“像 free-code 一样直接复用逻辑”的目标。
  - 纯 JSON 结构化存储：机器友好，但不适合直接注入现有 prompt 与分析查看。

## Decision 4: 结构化会话记忆由模型根据“旧 memory + 新增历史”重写或更新

- **Decision**: 每次需要 compaction 时，由模型基于上一版结构化 memory、最新已完成 transcript、稳定引用与边界信息直接重写或更新新的结构化会话记忆；不再依赖 `SessionMemorySummaryBuilder` 的规则拼接。
- **Rationale**: 这是用户在 clarify 中明确确认的要求，也与 free-code 的 `buildSessionMemoryUpdatePrompt()` + LLM 更新会话记忆思路一致。模型擅长跨轮归纳“当前状态、错误修正、工作流”，而规则拼接只能堆砌流水账。
- **Alternatives considered**:
  - 完全规则生成：难以高质量表达 `Current State`、`Errors & Corrections`、`Learnings` 等语义。
  - 直接复用 `SummaryAgent`：它现有协议面向任务总结和文件标记，不适合作为 session memory 更新器。

## Decision 5: `ai_agent_session_memory` 改为 append-only 版本快照，而不是 upsert

- **Decision**: 废弃当前 `session_id` 唯一键 + `upsert` 覆盖写策略，改为同一 `session_id` 可存在多条 snapshot version；每次 compaction 成功新增一条记录，运行时默认按“最新有效版本”加载。
- **Rationale**: 用户明确要求“新一轮压缩结果不能覆盖旧结果，旧的要留在 MySQL 里用于分析”。append-only 还能保留完整演进历史，便于回溯和问题分析。
- **Alternatives considered**:
  - 保持 `upsert`：与用户要求冲突。
  - 新建独立历史表：可行，但会增加迁移与运行时复杂度；本期直接复用现有表更稳。

## Decision 6: 最新有效 snapshot 以 `session_id + id desc` 查询，旧版本保留分析索引

- **Decision**: 在单会话单请求守卫成立的前提下，以新增快照的自增 `id` 作为版本顺序主依据，运行时按 `session_id` 取 `deleted=0` 且 `id desc limit 1` 的最新快照；为历史分析增加按 `session_id`/`conversation_id` 的顺序索引。
- **Rationale**: 现有 `session_id` 唯一键被移除后，必须有一个简单稳定的“最新版本”选择语义。由于同一 `sessionId` 已有 `session_busy` 和模式守卫，自增 `id` 足以表达插入顺序。
- **Alternatives considered**:
  - 新增 `version_no` 字段：更显式，但当前用 `id` 已能满足运行时选择。
  - 以 `last_compacted_at` 作为唯一顺序：时间相同或写入异常时不如 `id` 稳定。

## Decision 7: 最近原始窗口采用 token 预算主导，辅以最小消息窗口和工具链不拆断规则

- **Decision**: 参考 free-code `sessionMemoryCompact.ts` 的 `minTokens / minTextBlockMessages / maxTokens + adjustIndexToPreserveAPIInvariants()` 思路，以 token 预算为主保留最近原始窗口，同时约束：
  - 至少保留一个最小真实消息窗口
  - 不拆断 `tool_use / tool_result`
  - 不丢失与保留消息属于同一逻辑响应的关键片段
- **Rationale**: 用户在 clarify 中明确选择“以 token 预算为主”。固定按最近 N 轮保留无法适应长短不一的报告、命令输出和工具链。
- **Alternatives considered**:
  - 继续固定 `recent-window-turns`：对长输出会过宽或过窄。
  - 压缩后只保留摘要：无法继续追问最近真实执行链。

## Decision 8: 压缩失败采用“硬上限内降级继续，否则拒绝”并配 session 级熔断

- **Decision**: 请求前 compaction 失败时，如果原始 working memory 仍未超过硬上限，则降级继续；如果仍超限或上下文损坏，则拒绝请求。同时引入按 `sessionId` 维护的轻量熔断状态，限制连续失败重试次数，参考 free-code `MAX_CONSECUTIVE_AUTOCOMPACT_FAILURES` 的做法。
- **Rationale**: 这是用户在 clarify 中确认的策略，也与 free-code “压缩失败不应无限重试、但也不能无脑中断可继续请求”的思路一致。
- **Alternatives considered**:
  - 压缩失败就一律拒绝：可用性过差。
  - 压缩失败后始终继续：当上下文已超限时会把失败推迟到更深层链路。
  - 把失败次数持久化到 MySQL：实现更重，且属于短期运行态，不值得作为持久事实。

## Decision 9: 配置演进沿用现有 `session-memory.*` 命名空间，不另起一套

- **Decision**: 保留现有：
  - `enabled`
  - `compaction-threshold-tokens`
  - `summary-max-length`
  
  并新增或演进：
  - `hard-limit-tokens`
  - `recent-window-max-tokens`
  - `recent-window-min-messages`
  - `max-consecutive-failures`
- **Rationale**: 这样最符合“复用现有配置装配能力”的宪章，也能把 free-code 的核心参数语义迁移进来，而不是生造并行配置体系。
- **Alternatives considered**:
  - 复用 `recent-window-turns` 充当主策略：与 token 预算主导的澄清结果冲突。
  - 新建独立 `session-memory-compact.*` 命名空间：会增加配置迁移成本。

## Decision 10: `facts_json` 保留为兼容投影，`artifact_refs_json` 继续确定性维护

- **Decision**: `summary_text` 成为结构化 session memory 的主载体；`facts_json` 保留为兼容性投影或分析辅助，不再承担主摘要职责；`artifact_refs_json` 继续由确定性链路维护，不交由模型自由生成。
- **Rationale**: 当前 `SessionWorkingMemoryAssembler` 和 `SessionMemoryPromptFormatter` 已使用 `facts_json`，直接删除会扩大影响面。保留它可以平滑兼容现有逻辑，同时把结构化 memory 放在 `summary_text` 中。
- **Alternatives considered**:
  - 完全移除 `facts_json`：需要更大范围改造现有 assembler/test。
  - 让模型同时自由生成 artifact refs：容易破坏稳定引用可信度。
