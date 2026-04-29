# Research: TranscriptBlock 会话记忆重写

## Decision 1: 用新表硬替换旧账本，而不是在旧表上继续叠兼容语义

- **Decision**: 引入 `ai_agent_turn`、`ai_agent_transcript_block`、`ai_agent_display_event`，并重写 `ai_agent_session_memory`；上线切换时直接删除 `ai_agent_message`、`ai_agent_message_event` 及其旧代码。
- **Rationale**: 用户明确要求“彻底重写、不要兼容”。继续在旧表上叠加新语义会让历史、续聊和压缩三条链路继续共享一堆历史包袱，复杂度只会更高。
- **Alternatives considered**:
  - 保留旧表，只重解释字段：表名和字段语义继续误导后续实现，且旧链路仍会借尸还魂
  - 保留旧表并增加兼容迁移层：最不符合需求，也最容易把任务拖回“双轨维护”

## Decision 2: 每次新请求开始前同步判断压缩；超阈值时先压缩，再执行请求

- **Decision**: 压缩触发时机固定为请求前同步判断。只有真正需要压缩的那次请求，才执行压缩并立即消费压缩结果。
- **Rationale**: 这能保证“下一轮真正会失忆的请求”被保护到。当前仓库已有 `prepareForRequest()` 入口，适合作为新链路唯一压缩入口。
- **Alternatives considered**:
  - 请求结束后再压缩：当前链路已经证明这是错位时机
  - 异步后台压缩：会引入最终一致性窗口，和同步 display read model 的目标冲突

## Decision 3: 压缩失败后直接跳过压缩并继续当前请求

- **Decision**: 删除当前 `hard-limit + reject + circuit-open` 的主链路决策；压缩失败时直接放弃本次压缩结果，继续执行当前请求。
- **Rationale**: 这是用户在 clarify 阶段的明确决策。新方案的目标是简化主链路，而不是让压缩异常控制请求生死。
- **Alternatives considered**:
  - 压缩失败但超限时拒绝请求：能保护上下文预算，但与用户选择冲突
  - 使用连续失败熔断器：能减少失败重试，但会把新的工作流再次分裂成多分支状态机

## Decision 4: display read model 采用同步双写，并与 transcript facts 同事务落库

- **Decision**: `ai_agent_display_event` 作为前端历史详情唯一读模型，在 transcript blocks 写入时同步投影并同事务持久化。
- **Rationale**: UI 历史详情不再应该参与事实语义恢复。同步双写能让历史查询直接读稳定结果，同时保证展示读模型和事实账本绝不漂移。
- **Alternatives considered**:
  - 历史查询时再从 transcript blocks 即时投影：读时开销更大，也会把展示逻辑重新散回查询路径
  - 异步投影 display events：会出现“事实已写入但历史详情暂时不一致”的窗口

## Decision 5: 会话记忆快照保留多版本，运行时只消费最新有效版本

- **Decision**: `ai_agent_session_memory` 作为多版本 snapshot 表保留历史版本，运行时只读取最新有效版本。
- **Rationale**: 多版本快照可以天然支持回溯、排障和边界重建，也避免单行覆盖把唯一快照改坏。
- **Alternatives considered**:
  - 单记录覆盖式快照：实现简单，但一旦摘要损坏就没有恢复余地
  - 不落 snapshot，只靠事实链临时重建：长会话压缩目标无法成立

## Decision 6: working memory 由 turn/block 直接构建，并通过单一 formatter 输出 `historyDialogue`

- **Decision**: 续聊上下文由 `ai_agent_turn + ai_agent_transcript_block + latest snapshot` 直接恢复，统一交给 `TranscriptPromptFormatter` 输出 prompt 文本，同时生成结构化 message 列表和 session files。
- **Rationale**: 现有 `BaseAgent` 仍以 `history_dialogue` 注入为关键边界，直接替换其数据来源比全面推翻 agent prompt 协议更稳；同时仍然保持“单一事实模型直达格式化输出”。
- **Alternatives considered**:
  - 全面重写 agent prompt 注入协议，只保留结构化 messages：改动面过大，不符合“优先复用现有 Agent 装配”的宪章
  - 继续保留 `SessionWorkingMemoryAssembler` 等旧装配链：会把新表数据再次拉回多层转换

## Decision 7: 历史详情 API 直接返回 turn + display events，UI 删除 history-only 兼容恢复链

- **Decision**: `GET /api/agent/conversation/detail` 返回 turn 元数据和 display events；前端不再通过 `restoreTurn -> combineData -> handleTaskData` 把旧 payload 修补成实时结构。
- **Rationale**: 这是“删除旧代码、不要多次转换”的直接体现。UI 继续复用现有组件，但不再复用旧历史恢复算法。
- **Alternatives considered**:
  - 维持旧详情契约，只在后端继续构造 live-like payload：仍然把旧兼容思路带进新方案
  - 让前端自己从 transcript blocks 再投影：把领域语义泄漏到 UI，边界错误
