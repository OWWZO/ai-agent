# Research: Conversation History Projector Replay

## Decision 1: 新增独立 `ai_agent_dialogue_session` 主表，而不是继续把 run 当作会话头使用

- **Decision**: 新增 `ai_agent_dialogue_session`，一行代表一个稳定会话；`ai_agent_dialogue_run` 保持“一次请求一行”的执行账本语义。
- **Rationale**: 当前 `run` 虽然带 `session_id`，但缺少会话级摘要、排序和低成本统计，导致无法稳定支持系统范围列表、当前会话恢复和详情一致性。把会话头与请求账本分开后，列表查询和详情聚合职责更清晰。
- **Alternatives considered**:
  - 继续直接扫 `ai_agent_dialogue_run` 聚合会话摘要：查询成本高，排序和统计逻辑会散落到读侧
  - 把会话级字段堆回 `ai_agent_dialogue_run` 最新一行：会制造“哪一行代表当前会话头”的隐式规则，不利于维护

## Decision 2: 会话主表只保存低成本摘要，不复制 LLM/tool 细节

- **Decision**: `ai_agent_dialogue_session` 只落 `title / status / latest_request_id / latest_query_text / latest_summary_text / run_count / finished_run_count / failed_run_count / started_at / last_active_at` 等低成本字段。
- **Rationale**: 这些字段足以承接近期会话列表排序、默认展示与详情一致性校验；更细的 LLM/tool/artifact 细节仍应继续从既有账本读取，避免形成第二套真相源。
- **Alternatives considered**:
  - 在会话表冗余最近一轮完整 display payload：会放大写侧成本，也会让会话表承担展示事实
  - 每次列表查询实时跨 run/tool/artifact 聚合：实现复杂且性能不稳定

## Decision 3: 历史恢复继续复用现有执行账本与 rich tool structured output

- **Decision**: 会话详情读取继续走 `ai_agent_dialogue_run -> ai_agent_llm_invocation -> ai_agent_tool_invocation -> ai_agent_tool_output_* -> ai_agent_artifact`，不新建历史专用存储。
- **Rationale**: 当前 `ExecutionLedgerQueryServiceImpl` 已能把 `ToolInvocationView.structuredOutput` 回填给 rich tool projector；structured output 与 artifact 已经是稳定事实源，本期应该复用而不是再定义一套历史模型。
- **Alternatives considered**:
  - 为会话详情新增一张历史快照表：会制造并行真相源
  - 只保留 run + 最终总结，不回放 LLM/tool/artifact 细节：不满足“历史与进行中细节一致”的要求

## Decision 4: `ReplayProjector` 统一收口实时与历史语义，LLM 类型直接由 `agent_name` 判定

- **Decision**: 在 `ReplayProjector` 中集中封装 `agent_name -> messageType` 规则，历史与实时都走同一套投影能力；本期不新增 `semantic_kind` 等数据库字段。
- **Rationale**: 当前 `BaseAgentResponseHandler` 仍通过硬编码 `switch` 组装实时 `eventData`，而历史侧只有工具投影。直接复用既有 `agent_name` 约定可以最小化改动，并避免实时/历史各维护一套语义映射。
- **Alternatives considered**:
  - 为 `ai_agent_llm_invocation` 新增 `semantic_kind`：需要数据库演进，且当前 `agent_name` 已满足需求
  - 在 controller 或前端做语义映射：会破坏边界，且重复维护规则

## Decision 5: 历史回放按 run 粒度恢复，缺少显式最终答案时由 `finalSummaryText` 兜底

- **Decision**: `ConversationHistoryReplayService` 按会话查询所有 runs，再逐个 run 构造 `ReplayFactBundle`；若某个 run 最终没有稳定 `result` 事件，则用 `run.finalSummaryText` 合成最终结果块。
- **Rationale**: 规格明确要求“每轮必须有清晰最终结论”，同时当前账本中确实存在只记录总结但没有独立最终回答事件的情况。把兜底逻辑放在 replay 阶段，可以保证实时链和写侧不被额外耦合。
- **Alternatives considered**:
  - 详情接口返回空结果并让前端兜底：会把领域语义泄漏到 UI
  - 回写数据库补历史数据：会引入历史修复副作用，不适合本期

## Decision 6: 会话列表与会话详情契约分离，列表默认只返回最近 20 条轻量摘要

- **Decision**: 新增独立的列表接口和详情接口；列表默认返回最近 20 条、按 `last_active_at DESC` 排序，只暴露标题、最近查询预览、状态、最近活动和轮次概览。
- **Rationale**: 规格明确要求“列表轻量、详情按需读取”。将两类契约分离可以避免为渲染列表而读取完整 replay 内容，也能稳定控制首页初始开销。
- **Alternatives considered**:
  - 一个接口同时返回列表和详情：返回体过重，不利于分页和后续扩展
  - 列表接口直接带最近总结内容：与澄清结果冲突，且存在更高信息暴露风险

## Decision 7: 系统范围近期会话可见性在本期按“受控内部环境”处理，不引入 owner/device 自动归属

- **Decision**: 按澄清结果允许终端用户查看系统内所有近期会话摘要，但显式记录该能力仅适用于当前受控内部环境；首页自动恢复仍只允许使用当前 `sessionId`，失败时保持空白或初始界面。
- **Rationale**: 当前仓库中没有稳定的会话 owner / tenant / device 归属模型，贸然设计权限字段会扩大本期范围。将系统范围列表限定为内部受控环境，可以满足当前需求，同时保留后续在 `dialogue_session` 上补 owner 维度的扩展点。
- **Alternatives considered**:
  - 立即引入 owner/tenant 权限模型：需要额外数据源、鉴权与迁移，超出本期范围
  - 自动回退到最近会话：与澄清结果冲突，且会误展示无关历史

## Decision 8: 前端继续复用现有 `combineData / handleTaskData`，用 replay frames hydrate 回 `ConversationHistory`

- **Decision**: 后端详情接口返回尽量贴近现有 SSE `eventData` 的 replay frames；前端新增 `hydrateConversationFromReplayFrames(detail)`，通过 `combineData`、`buildTaskFromEventData` 和 `handleTaskData` 恢复历史会话。
- **Rationale**: 当前 UI 渲染链已经围绕 `multiAgent.tasks / conclusion / plan_thought / task_summary` 组织。重用这套成熟链路能降低 UI 改动面，并确保历史与实时界面一致。
- **Alternatives considered**:
  - 新建 history-only 前端模型和组件：会形成第二套展示系统
  - 后端直接返回前端完整 `ConversationHistory`：把前端状态细节绑定进后端，后续演进成本更高
