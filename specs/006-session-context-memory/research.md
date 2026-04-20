# Research: ReAct / PlanSolve 完整链路会话上下文复原

## Decision 1: 本期不修改 MySQL 表结构，也不新增新的记忆表

- **Decision**: 继续复用既有 `ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`、`ai_agent_session_memory` 四张表，本期不新增表、不加列、不改现有压缩快照结构。
- **Rationale**: 当前缺陷不在“存不下”，而在“没把已存的事件链用起来”。`ai_agent_message_event` 已包含 `seq_no / event_type / content_text / payload_json`，足以支撑 transcript 恢复；用户也明确要求本期先不要碰上下文压缩。
- **Alternatives considered**:
  - 新增 transcript block 表：会重复表达现有 event ledger，带来迁移和一致性成本。
  - 在 `ai_agent_message_event` 上追加 tool 专属列：只有在现有 payload 无法稳定恢复时才值得考虑，本期优先走兼容解析。

## Decision 2: `ai_agent_session_memory` 只作为“已压缩历史边界 + 摘要来源”，不再作为本期主改造对象

- **Decision**: 对已经被 `ai_agent_session_memory.boundary_sort_order` 覆盖的旧历史，继续沿用现有摘要快照；本期 transcript 恢复仅聚焦边界之后的已完成轮次。
- **Rationale**: 这是用户最新确认的范围边界。这样可以在不重写压缩逻辑的前提下，把主要精力放到“当前仍可直接读取的丰富事件链”。
- **Alternatives considered**:
  - 顺手重写 `SessionMemoryCompactionService`：与用户要求冲突，且会扩大影响面。
  - 完全忽略快照边界：会和当前生产行为冲突，也会让已压缩历史重复进入上下文。

## Decision 3: 工作记忆必须读取完整 final events，而不是只读取 artifact 事件

- **Decision**: 把 `SessionWorkingMemoryAssembler` 和 `AgentSessionMemoryServiceImpl` 的事件读取，从当前 `queryArtifactEventsByMessageIds(...)` 改为面向工作记忆的“完整 final events 批量查询”。
- **Rationale**: 现状只提取 `artifactRefs`，直接导致 `tool_thought`、`tool_result`、`deep_search`、命令执行结果等全部丢失。要让 LLM 知道自己上次做过什么，必须先把这些事件读出来。
- **Alternatives considered**:
  - 保持 artifact-only 查询，再从 `response` 文本里反推工具链：信息不完整且容易失真。
  - 只读 `content_text` 不读 `payload_json`：会丢失结构化工具参数、文件引用和 message identity。

## Decision 4: 新的运行时上下文模型采用“turn + ordered transcript blocks”，而不是继续沿用 `userMessage + assistantMessage`

- **Decision**: 把当前 `SessionTurnMemory` 的简单双字符串模型升级为“单轮 + 有序上下文块”，至少覆盖 `user_input`、`assistant_thought`、`assistant_answer`、`tool_use`、`tool_result`、`artifact_reference`。
- **Rationale**: 用户明确指出现有消息模型太简单，无法表达复杂交互历史。只要仍然把一轮压成 `query + response`，工具链和思考过程就无法进入运行时上下文。
- **Alternatives considered**:
  - 继续沿用旧 `SessionTurnMemory`，只是在 `assistantMessage` 里拼大文本：实现快，但会把工具链语义重新打平。
  - 直接在 `history_dialogue` 中拼接所有事件：会让提示词失控，也不利于后续结构化注入。

## Decision 5: 内部请求消息契约要补齐 tool chain 语义，并复用现有 Agent `Message` / LLM 能力

- **Decision**: 扩展内部 `AgentRequest.Message` 到足以表达 `assistant thought`、`toolCalls`、`toolCallId`、文件/产物引用等信息，并同步修改 `RootNode` / `Step1SopRecallAndPrepareNode` 的 `convertMessages(...)`，映射到现有 `org.wwz.ai.domain.agent.reactor.agent.dto.Message`。
- **Rationale**: Agent/LLM 内部消息模型已经支持 assistant tool calls 和 tool results，但 `AgentRequest.Message` 与节点转换逻辑仍然只保留 `role + content`。要让续聊真正感知工具调用链，必须把中间那层打通。
- **Alternatives considered**:
  - 只在 `history_dialogue` 里塞文本：LLM 能“看到”一些历史，但无法以 tool-aware 形式复用。
  - 完全重写 Agent 内部 `Message` 模型：收益不如复用现有结构，且影响面过大。

## Decision 6: 长工具输出采用“关键结果 + 稳定引用”策略，而不是整段内联回灌

- **Decision**: 对 `deepsearch report` 正文、超长 `stdout/stderr`、大 `diff`、大文件读取结果等内容，默认只保留工具执行事实、关键结果摘要和稳定引用；正文不进入 preloaded messages。
- **Rationale**: 这是用户明确约束，也能避免模型窗口被大段观察结果污染。真正需要正文时，应通过稳定文件/产物引用回取。
- **Alternatives considered**:
  - 原样回灌全部输出：上下文成本过高，且容易淹没真正关键的约束和结论。
  - 只保留最终状态：会让 LLM 不知道上一次具体做了什么、产出了什么类型的结果。

## Decision 7: Prompt 摘要、预装消息、稳定文件三条注入路径继续分工，而不是重新合并为一条

- **Decision**:
  - `history_dialogue` 继续承载快照摘要、关键事实和简要文件提示
  - `messages` 负责注入最近直接回放窗口内的 richer transcript chain
  - `sessionFiles` 继续恢复稳定文件对象，供工具链和 prompt 双侧复用
- **Rationale**: 当前系统已经分别预留了这三条入口。复用这些入口可以最小化影响面，同时避免把文件对象、摘要文本、工具链都塞到同一个字段里。
- **Alternatives considered**:
  - 只保留 `messages`：会丢掉现有 prompt 模板里的 `{{history_dialogue}}` 价值。
  - 只保留 `history_dialogue`：无法让 richer message chain 真正参与后续推理。

## Decision 8: 事件规范化继续统一走 `ConversationEventPayloadNormalizer`

- **Decision**: 读取侧和写入侧都继续复用 `ConversationEventPayloadNormalizer`，把 legacy `fileInfo/fileList`、缺失态 artifact 和 `artifactRefs[]` 统一收敛为 canonical payload。
- **Rationale**: 历史详情装配已经依赖这层规范化。本期如果再造一套 event 解析规则，会导致“展示链”和“续聊链”理解同一条事件的方式不一致。
- **Alternatives considered**:
  - 在工作记忆装配里单独写一套 payload 解析器：短期可做，但长期会与 replay 语义漂移。
  - 只读取写入后的新格式，不兼容旧格式：会让老会话直接失忆。

## Decision 9: 验证重点放在“装配正确性 + 守卫不回归 + 历史重开”，而不是压缩质量

- **Decision**: 本期测试重点是 assembler、preloaded message 构建、mode/session guard、history reopen、old event fallback、long output reference-only 行为；现有 compaction 相关测试只要求继续通过。
- **Rationale**: 本期不改压缩逻辑，因此验证资源应聚焦真正会变的路径。这样既符合宪章中的“Verification Over Assumption”，也能避免把测试面拉得过大。
- **Alternatives considered**:
  - 连 compaction 流程一起全面重测：价值有限，且偏离本期范围。
  - 只做手工验证：不足以覆盖 event 缺失、旧格式 payload、tool chain 映射等高风险分支。
