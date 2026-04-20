# Feature Specification: ReAct / PlanSolve 完整链路会话上下文复原

**Feature Branch**: `[006-session-context-memory]`  
**Created**: 2026-04-19  
**Status**: Draft  
**Input**: User description: "按 free-code-main 的完整记忆上下文机制重写当前方案：`ai_agent_message_event` 必须参与上下文构建，续聊需要感知完整思考过程、工具调用链、MCP / skilltool 返回、文件与命令执行结果，不采用微压缩，避免退化成仅保留用户问题加最终回答。"

## Existing System Context *(mandatory for brownfield features)*

- **Affected Modules**: `ai-agent-station-study-domain`、`ai-agent-station-study-infrastructure`、`ai-agent-station-study-trigger`、`ai-agent-station-study-app`
- **Existing Capabilities to Reuse**: 现有 `ai_agent_conversation / ai_agent_message / ai_agent_message_event` 持久化链路、`AgentStreamPersistServiceImpl` 的流式落库与事件归档能力、`ConversationReplayAssembler` 的历史事件装配链路、现有 `sessionId` 会话标识、现有文件/产物恢复能力、现有 `history_dialogue` 注入入口、现有会话冲突与模式守卫
- **Out of Scope**: 不引入 Spring AI 自带 ChatMemory、不新增跨会话用户长期画像、不改造成文件型记忆目录、不在本期重做普通 `CHAT` 模式续聊、不照搬 free-code-main 的微压缩或后台自动整理机制、不重做现有前端历史展示交互、本期不改动现有 `ai_agent_session_memory` / `SessionMemoryCompactionService` 上下文压缩与摘要快照链路
- **Current Constraints**: 会话记忆真相源必须落在 MySQL；需要保持 DDD 分层边界清晰；同一 `sessionId` 仍需保持模式归属唯一且禁止并发执行；历史重开后的工作上下文必须仅依赖持久化数据恢复；现有历史详情与流式主链路不能因本需求回退

## Clarifications

### Session 2026-04-19

- Q: 同一会话续聊的主真相源应是什么？ → A: 以 `ai_agent_message` 与 `ai_agent_message_event` 共同构成完整会话账本，`message_event` 不再只是展示数据
- Q: 是否采用 free-code-main 的微压缩机制？ → A: 不采用微压缩，优先恢复完整 transcript，只在预算不足时做确定性裁剪
- Q: 历史中哪些内容必须可被后续轮次感知？ → A: 用户问题、助手思考/推理块、工具调用、工具输出、稳定文件/产物引用、最终回答及其顺序关系
- Q: 哪些工具链输出属于必须保留的上下文？ → A: MCP、skilltool、搜索、文件读取/写入、命令执行及其他现有工具的稳定输出
- Q: 异常或未完成轮次是否进入可续聊工作上下文？ → A: `STREAMING / ERROR / FORCE_STOPPED` 轮次不进入可复用工作上下文
- Q: 历史思考块和工具返回应如何进入上下文？ → A: 已完成轮次的 `assistant thought` 默认按原始块保留进入上下文；同时所有工具的返回参数与输出都属于上下文数据来源，仅在预算不足时做确定性裁剪
- Q: 含敏感信息或超长内容的工具输出应如何处理？ → A: 超长工具输出默认不整段回灌；像 `deepsearch report` 这类长篇报告正文只保留文件地址或稳定引用
- Q: 对会改状态或产出超大内容的工具，续聊上下文默认保留到什么粒度？ → A: 默认保留 `tool_use`、关键入参、结构化结果摘要、变更结果或产物引用；原始大输出按稳定引用回取
- Q: 本期是否同时改造上下文压缩链路？ → A: 暂不修改上下文压缩，先只改 transcript 恢复、事件装配与续聊上下文复原
- Q: 本期 `ai_agent_session_memory` 的定位是什么？ → A: 继续作为已压缩历史的边界与摘要来源；工作上下文只对边界之后的已完成轮次从 `ai_agent_message + ai_agent_message_event` 恢复完整 transcript

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 同会话延续完整执行链 (Priority: P1)

作为在同一 `sessionId` 内持续推进任务的用户，我希望系统在下一轮请求中能够感知上一轮已经做过的搜索、文件读取、命令执行、MCP/skilltool 及其他工具调用的入参与结果，这样我只需要说“继续刚才的分析”或“基于上一次结果补充”，系统就能延续同一条工作链，而不是重新从零开始。

**Why this priority**: 这是当前痛点最集中的主场景。若系统只能记住“用户问题 + 最终回答”，深度研究与多步执行链会在第二轮直接失真，导致重复搜索、重复读文件和重复调用工具。

**Independent Test**: 在同一会话第一轮中执行搜索、MCP 调用、skilltool、文件读取或其他任意工具调用，再在第二轮用“继续”“补充”“基于上次结果对比”等指令追问，验证系统能够引用前一轮实际执行过的工具链、工具入参与输出，而不是只知道最终摘要。

**Acceptance Scenarios**:

1. **Given** 某轮对话已经执行过多次搜索、文件读取和工具调用并得到稳定输出，**When** 用户在下一轮要求“继续刚才的分析”，**Then** 系统应基于同一条已完成执行链继续工作，而不是仅依据前一轮最终回答重新起步
2. **Given** 某轮对话已经读取过特定文件、访问过特定 MCP 或 skilltool 输出，**When** 用户在下一轮要求补充或对比结果，**Then** 系统应知道这些历史上下文已存在，并在无须刷新时避免盲目重复获取
3. **Given** 某轮对话已经产生中间文件、报告或结构化产物，**When** 用户在下一轮要求“基于上次产物继续修改或扩展”，**Then** 系统应同时恢复该产物引用及其对应的工具执行链；对于报告类长篇正文，应优先通过稳定引用复用，而不是整段正文回灌

---

### User Story 2 - 历史重开仍可恢复完整上下文 (Priority: P2)

作为重新进入旧会话继续工作的用户，我希望系统不仅能展示历史消息，还能在真正续聊时恢复该会话里之前的思考过程、工具调用链、工具结果和稳定文件引用，而不是重开后又退化成一段没有工作记忆的新会话。

**Why this priority**: 历史重开是当前方案最容易暴露问题的地方。只做前端历史回放而不恢复执行上下文，会让用户看到“有历史”，但模型实际“失忆”。

**Independent Test**: 完成一段包含搜索、MCP、skilltool、文件操作或命令执行的历史会话，关闭并重新进入该会话后继续提问，验证系统能基于持久化账本恢复同一条上下文链继续执行。

**Acceptance Scenarios**:

1. **Given** 某个历史会话中已经保存了多轮消息和对应的事件账本，**When** 用户重新进入同一 `sessionId` 并继续提问，**Then** 系统应基于持久化账本恢复工作上下文，而不是只读取最后一条回答
2. **Given** 某个历史会话中包含 MCP 返回、skilltool 输出、文件读写结果或命令执行结果，**When** 用户在重开后基于这些结果继续追问，**Then** 系统应能够感知并复用这些历史输出
3. **Given** 某个历史会话已经生成稳定文件或产物引用，**When** 用户重新进入该会话继续工作，**Then** 这些文件或产物引用应重新进入可用上下文，而不是只停留在历史展示层

---

### User Story 3 - 当前可直接回放窗口内的长链路上下文不再退化为摘要残片 (Priority: P3)

作为执行复杂多步任务的用户，我希望当单轮或多轮历史中存在大量思考、工具调用和工具输出时，系统在当前仍可直接从 turn/event 账本读取的历史范围内，后续续聊仍优先保留完整执行链的关键结构，而不是把富上下文退化成只剩“问题 + 最终回答”的摘要残片。

**Why this priority**: 当前方案的核心缺陷不是“没有存数据”，而是“存了完整事件却不用”。只要复杂任务一结束，工具链和中间观察就被丢弃，后续轮次必然重复劳动。

**Independent Test**: 构造一个包含大量 `thought / tool_use / tool_result / artifact` 事件、且仍位于当前直接回放窗口内的长链路样本，再继续追问依赖其中某个中间结果的问题，验证系统能保留关键执行链顺序与结果，而不是只剩最终总结。

**Acceptance Scenarios**:

1. **Given** 某个当前仍可直接回放的历史样本中存在大量事件块和多次工具调用，**When** 系统为下一轮请求构建工作上下文，**Then** 它必须优先保留完整执行链中的关键节点，而不是退化成仅保留用户问题与最终回答
2. **Given** 某个历史样本中的部分旧轮次已经被现有摘要快照覆盖，**When** 系统在本期范围内构建下一轮请求上下文，**Then** 它应在仍可直接回放的范围内恢复完整事件链，而对已被快照覆盖的旧历史继续沿用现有压缩结果

### Edge Cases

- 历史轮次存在 `ai_agent_message_event` 缺失、旧格式或局部损坏时，系统应安全退化到 turn 级消息账本，而不是整个会话上下文重建失败
- 同一轮中多次调用同一工具且入参不同或顺序相关时，系统应保留逐次调用关系，避免把不同调用结果错误合并
- 某些工具输出体量极大或工具本身会产生状态变更时，系统应默认保留 `tool_use`、关键入参、结构化结果摘要以及变更结果或产物引用；原始大输出可以通过稳定引用回取，但不能让后续轮次完全不知道该工具曾执行过、使用了什么关键入参、执行了什么类型的工作、得到了什么结果
- 报告类长文本产物（如 `deepsearch report` 的正文）默认不作为续聊内联上下文回灌，应以文件地址或稳定引用形式参与后续工作
- 历史会话中的稳定文件或产物引用若已失效、被删除或不可访问，系统应显式标记不可复用状态，而不是静默丢失这段上下文
- 某些旧会话只有 turn 级 `query / response`，缺少新的事件粒度数据时，系统仍应能续聊，只是精度降级
- 若某些历史轮次已经被现有 `ai_agent_session_memory` 摘要快照覆盖，本期允许继续沿用快照结果，不要求回溯重建这些已压缩轮次的完整事件链
- `STREAMING / ERROR / FORCE_STOPPED` 轮次不得污染后续可复用上下文，避免把未完成链路误当成已完成事实
- 同一 `sessionId` 若尝试在 `REACT` 与 `PLAN_SOLVE` 之间切换，系统仍需拒绝复用原会话，避免不同执行协议的上下文相互污染
- 同一 `sessionId` 若上一轮仍在执行，则新请求必须被拒绝或等待，防止两个请求并发写入同一条历史链导致顺序失真

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 继续以 `sessionId` 作为 ReAct 与 PlanSolve 单会话上下文的唯一归属标识，并保证执行链路、持久化链路与恢复链路使用同一会话标识
- **FR-002**: 系统 MUST 在每次新请求开始前，对未被现有摘要快照覆盖的历史范围，从 `ai_agent_message` 与 `ai_agent_message_event` 共同重建该会话的工作上下文，而不是仅基于 `query / final response`
- **FR-003**: 系统 MUST 将 `ai_agent_message_event` 视为续聊上下文的一等输入，不再把它仅当作前端历史展示所需的附属数据
- **FR-004**: 当历史事件存在时，系统 MUST 在工作上下文中保留已完成轮次中的用户输入、助手思考/推理原始块、工具调用记录、工具输出、稳定文件或产物引用，以及最终回答
- **FR-005**: 系统 MUST 提供能够表达完整交互历史的上下文消息语义，至少覆盖 `user`、`assistant answer`、`assistant thought`、`tool_use`、`tool_result`、`artifact/file reference` 与 `turn completion`
- **FR-006**: 系统 MUST 保留同一轮内 `tool_use` 与对应 `tool_result` 的因果顺序和关联关系，包括同一工具的重复调用、不同入参调用以及跨步骤链式调用
- **FR-007**: 系统 MUST 让后续轮次能够感知此前已经执行过哪些工具操作，包括但不限于 MCP、skilltool、搜索、文件读写、命令执行及其他现有工具，以及这些操作使用了哪些关键入参与产生了哪些可复用结果
- **FR-008**: 系统 MUST 在续聊和历史重开时恢复稳定文件或产物引用，并将其重新作为可用上下文参与后续执行，而不是只保留一段文字摘要
- **FR-009**: 若某些历史轮次缺少事件级数据，系统 MUST 回退到 turn 级消息账本继续恢复，而不是导致整次上下文构建失败
- **FR-010**: `STREAMING / ERROR / FORCE_STOPPED` 轮次 MUST NOT 进入可复用工作上下文，除非未来另有明确的已完成安全判定机制
- **FR-011**: 系统 MUST 继续维持单个 `sessionId` 的模式归属唯一性；一旦会话已绑定 `REACT` 或 `PLAN_SOLVE`，后续切换模式时必须要求新建会话
- **FR-012**: 系统 MUST 禁止同一 `sessionId` 在上一轮尚未结束时并发发起新的续聊请求，并返回明确冲突提示
- **FR-013**: 系统 MUST 继续保留 `ai_agent_session_memory` 作为已压缩历史的边界与摘要来源；本期不重写 `ai_agent_session_memory`、`SessionMemoryCompactionService` 或其他已有压缩流程
- **FR-013A**: 工作上下文重建 MUST 以 `ai_agent_message + ai_agent_message_event` 作为未被现有压缩边界覆盖历史的主来源；对已经被摘要快照覆盖的历史，继续沿用现有快照结果
- **FR-013B**: 对报告类长篇正文、超长 `stdout/stderr`、大 `diff` 或其他超长原始输出，系统 MUST 在 transcript 装配侧优先使用稳定引用或结构化结果片段，而不要求改造现有压缩服务
- **FR-014**: 系统 MUST NOT 在当前可直接读取到更丰富事件账本的历史范围内，把已完成轮次退化为仅保留“用户问题 + 最终回答”的上下文形态
- **FR-015**: 系统 MUST 让历史重开后的新请求仅依赖数据库已持久化的账本恢复上下文，不依赖上一个进程残留的内存状态
- **FR-016**: 系统 MUST 保持现有会话详情与历史回放能力可用，并让展示层与续聊层共享同一套持久化 transcript 语义，避免“前端看得到、模型用不到”
- **FR-017**: 系统 MUST 定义必要的数据契约演进，包括事件 payload 归一化、上下文消息类型、关联标识或兼容旧会话所需的映射规则
- **FR-018**: 本期续聊机制 MUST 不新增微压缩、自动整理或新的摘要压缩主链路；现有压缩逻辑保持不变

### Key Entities *(include if feature involves data)*

- **Conversation Transcript Ledger**: 以同一 `sessionId` 为范围，由 turn 级消息账本与 event 级细节账本共同构成的完整会话记录
- **Transcript Context Block**: 从历史账本中恢复出的最小上下文单元，可表示用户消息、助手思考、工具调用、工具输出、文件/产物引用或最终回答
- **Tool Invocation Record**: 某次具体工具执行的结构化记录，包含工具身份、调用顺序、关联标识、关键输入语义与必要入参
- **Tool Output Record**: 与某次工具执行相对应的结果记录，至少包含结果类型、关键结果摘要、必要的变更结果或产物引用；对报告类长文本、超长命令输出或大 `diff` 可仅保留稳定引用而不内联全文
- **Session Artifact Reference**: 可在续聊时重新装配进上下文的稳定文件或产物引用，包含可访问状态与必要描述信息
- **Working Context Materialization**: 每次新请求开始前，由历史账本整形得到的可直接注入模型的工作上下文载荷

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 在验收样本中，100% 的同会话续聊场景都能继承前一轮中已执行的搜索、文件读取、命令执行、MCP 或 skilltool 结果，而无需用户重复描述这些历史输出
- **SC-002**: 在历史重开验收样本中，100% 的续聊请求都能基于持久化账本恢复之前的工具链、稳定文件引用和关键中间结果，而不是退化成只读取最后一条回答
- **SC-003**: 在未被现有压缩边界覆盖、且包含丰富事件账本的验收样本中，95% 以上的续聊上下文都来源于已持久化的完整事件链，而不是退化成仅保留“问题 + 最终回答”
- **SC-004**: 对包含至少 10 个历史工具事件的复杂样本，立即下一轮续聊中对等价信息获取动作的重复执行比例，相比当前 summary-only 行为降低至少 80%
- **SC-005**: 在包含超长工具输出但仍处于直接回放窗口内的验收样本中，100% 的续聊上下文都仍保留工具执行事实、关键结果、稳定文件引用，以及被保留范围内的 `tool_use / tool_result` 顺序关系

## Assumptions

- 本期范围仍聚焦于 `REACT / PLAN_SOLVE` 的单会话续聊，不把普通 `CHAT` 模式纳入同一批改造
- 当前 `ai_agent_message_event` 中已持久化的结构化 payload 足以作为主要上下文来源，缺口部分可通过兼容映射或 turn 级账本兜底
- 某些超大工具输出在续聊时不需要逐字重放，但至少需要保留其执行事实、输出类型、关键结果或稳定引用
- free-code-main 可作为“完整 transcript 续聊”的行为参考，但本期不引入其微压缩、后台整理或文件型 memory 机制
- 本期不调整现有 `ai_agent_session_memory` 摘要快照生成规则；它继续只负责已压缩历史的边界与摘要来源，已被快照覆盖的旧历史暂不回溯重建完整 transcript
- 历史会话里已保存的助手思考与工具链事件，在本产品语义下属于允许进入后续工作上下文的持久化数据
- 现有会话历史回放接口、事件归一化与文件恢复能力将被扩展复用，而不是推倒重做
