# Research: ReAct / PlanSolve 会话上下文记忆

## Decision 1: 以 MySQL 作为会话记忆唯一真相源，不引入 Markdown 长期记忆文件

- **Decision**: 会话级短期记忆与“本需求内的长期可续聊记忆”统一落在 MySQL，继续围绕 `ai_agent_conversation / ai_agent_message / ai_agent_message_event` 扩展，不引入 `MEMORY.md` 或 Spring AI ChatMemory 插件。
- **Rationale**: 这是用户明确约束；同时项目已经具备完整的会话账本与历史详情持久化链路，直接复用数据库最符合现有架构与 DDD 边界。
- **Alternatives considered**:
  - 复用 Spring AI ChatMemory：与用户要求冲突，而且会形成第二套记忆真相源。
  - 采用 Markdown 记忆目录：更像 free-code 的跨会话 memory mechanics，但与本项目现有会话/权限/持久化体系不一致。

## Decision 2: 复用既有三表作为 transcript 账本，只新增一张“当前生效快照表”

- **Decision**: 继续把 `ai_agent_conversation` 当作会话主档，`ai_agent_message` 当作每轮账本，`ai_agent_message_event` 当作最终细节与稳定产物引用来源；另外新增 `ai_agent_session_memory` 作为单会话唯一生效的摘要快照。
- **Rationale**: 这与 free-code “消息数组 + transcript + compact summary”的职责划分最接近，只是把 transcript 从 JSONL 文件换成数据库三表，把 compact summary 换成单条快照行。
- **Alternatives considered**:
  - 只把摘要塞回 `metrics_json`：字段职责会再次混乱，不利于后续独立查询和更新。
  - 新建多版本快照历史表：复杂度更高，不符合“每个会话只保留当前生效摘要”的澄清结论。

## Decision 3: 用“请求级工作记忆重建”替代 free-code 的常驻 `mutableMessages`

- **Decision**: 每次新请求开始前，通过 `SessionWorkingMemoryAssembler` 从数据库重建一份请求级 `SessionWorkingMemory`，而不是在服务进程里长期维护一个跨请求的内存数组。
- **Rationale**: Java 服务天然是多实例、无状态入口；free-code 的 `mutableMessages` 更适合单进程 REPL。对当前项目，最稳定的映射方式是“数据库账本 + 进入请求时重建一份等价工作消息数组”。
- **Alternatives considered**:
  - 在 Spring Bean 中缓存会话消息数组：多实例和重启场景下都不可靠。
  - 每轮都读取全量历史：无法控制长会话上下文体积，也不能映射 free-code 的 compaction 思想。

## Decision 4: 工作记忆由“摘要 + 最近详细窗口 + 文件上下文”组成

- **Decision**: `SessionWorkingMemory` 由四部分构成：
  - 当前生效摘要快照
  - 结构化事实（目标、约束、已确认结论、待续状态）
  - 最近若干轮已完成的详细 user/assistant 消息
  - 可恢复的稳定文件/产物引用
- **Rationale**: 这正对应 free-code 的“summary + messagesToKeep + attachments”结构，同时避免把纯工具噪音整段塞回模型窗口。
- **Alternatives considered**:
  - 只用摘要：最近上下文细节容易丢。
  - 只用最近窗口：长会话核心结论容易遗忘。

## Decision 5: 摘要快照采用“单会话单行原地更新”，并显式记录压缩边界

- **Decision**: 新增 `ai_agent_session_memory`，每个 `sessionId` 仅保留一条当前生效快照，字段中显式保存 `boundary_sort_order` / `boundary_message_id` 一类边界信息，后续压缩时原地更新。
- **Rationale**: 这与 free-code 的 compact boundary 作用等价，但更适合数据库查询与幂等更新；也符合前面已完成的 clarify 结论。
- **Alternatives considered**:
  - 维护多版本摘要历史：对当前需求没有直接收益，反而增加读取复杂度。
  - 不记录边界，只按时间推断：很容易重复摘要已归档消息。

## Decision 6: 压缩策略映射 free-code 的“summary + boundary + recent window”，不保留工具噪音

- **Decision**: 压缩时只抽取用户目标、输出约束、关键事实、阶段结论、待办状态与稳定 artifact 引用；最近窗口保留完整 user/assistant 轮次；工具调用细节仅在历史详情表中保留，不进入工作记忆主载荷。
- **Rationale**: free-code 也不是机械回放全部工具输出，而是通过 compact summary 把高价值语义提炼出来。当前项目已有 `AgentMessageEvent` 作为展示账本，因此工作记忆没必要重复背负完整工具明细。
- **Alternatives considered**:
  - 把事件表内容全部转成 Message：上下文会被大量搜索结果、工具日志污染。
  - 只摘要文字不保留最近窗口：当前阶段性任务容易断层。

## Decision 7: 稳定文件引用要恢复成 `AgentContext.productFiles`，而不是只写进摘要文本

- **Decision**: 会话文件恢复优先复用 `ai_agent_message_event.payload_json` 中已经归一化的 `artifactRefs[]`，将稳定引用转回 `AgentContext.productFiles` 的 `File` 结构；同时在 `history_dialogue` 中保留必要的文字摘要。
- **Rationale**: 当前 `PlanningAgent`、`ReactImplAgent`、`ExecutorAgent` 的 prompt 组装和工具调用都直接依赖 `context.getProductFiles()`，如果只恢复文字摘要，文件工具与报表工具无法真正感知历史产物。
- **Alternatives considered**:
  - 只依赖 `files_json`：只能覆盖当前轮上传文件，无法覆盖历史产物文件。
  - 只在 prompt 里描述文件：无法支撑现有工具链按文件对象工作。

## Decision 8: `ERROR / FORCE_STOPPED` 不进入会话记忆，且并发守卫复用现有消息状态

- **Decision**: 只有 `COMPLETED` 轮次才有资格进入摘要、事实提炼与最近窗口；同一 `sessionId` 的并发冲突通过检查该会话是否存在 `STREAMING` 消息完成。
- **Rationale**: 用户已经明确要求 `ERROR / FORCE_STOPPED` 不污染后续续聊。复用 `ai_agent_message.status` 做并发守卫无需再建额外锁表。
- **Alternatives considered**:
  - 让 `FORCE_STOPPED` 进入摘要：会把半成品中间态带入后续推理。
  - 单独引入分布式锁：对当前单会话写保护而言过重。

## Decision 9: 单会话模式归属锁定到 `ai_agent_conversation.agent_type`

- **Decision**: 同一 `sessionId` 一旦创建为 `REACT` 或 `PLAN_SOLVE`，后续请求必须保持一致；切换模式时直接拒绝沿用旧会话。
- **Rationale**: 两种模式的提示词职责、执行协议和记忆语义不同，混用会导致摘要和最近窗口都变脏。
- **Alternatives considered**:
  - 同会话自由切模式：实现成本高，而且会让记忆内容无法稳定解释。
  - 自动迁移旧会话到新模式：容易在用户无感知情况下破坏历史一致性。

## Decision 10: 会话记忆既要注入 prompt，也要预装到 Agent `Memory.messages`

- **Decision**:
  - 摘要文本和结构化事实通过现有 `{{history_dialogue}}` 占位符注入提示词
  - 最近详细窗口通过预装 `PlanningAgent / ExecutorAgent / ReactImplAgent` 的 `Memory.messages` 实现
- **Rationale**: free-code 的核心不只是 system prompt 里有 summary，还包括真正参与推理的消息数组。当前项目的 Agent 已经有 `Memory.messages`，只是现在只在单次请求内生效，预装后就能最大化复用现有实现。
- **Alternatives considered**:
  - 只替换 `{{history_dialogue}}`：更简单，但无法达到 free-code 那种“最近详细轮次继续参与推理”的效果。
  - 只预装 Message 不替换 prompt：会浪费现有 prompt 模板已经预留好的 `history_dialogue` 位。

## Decision 11: 明确修复 `sessionId` 传播错误，再做记忆装配

- **Decision**: 必须先修复 `RootNode` 与 `Step1SopRecallAndPrepareNode` 中 `AgentContext.sessionId` 被错误设置为 `requestId` 的问题，再把会话记忆相关逻辑挂到 `AgentContext` 与 Agent 执行链。
- **Rationale**: 如果继续混用 `requestId`，会导致同会话续聊找不到同一份历史，也无法让产物工具用会话级 ID 持续工作。
- **Alternatives considered**:
  - 先做记忆查询、后面再修 bug：会让后续排查变得困难，而且很可能读到空历史。

## Decision 12: `BaseAgent.memory` 保留“单次执行 scratchpad”职责，不承担跨请求持久化

- **Decision**: `BaseAgent.memory` 继续只承担当前一次 Agent 执行期间的工作消息数组；跨请求会话记忆由数据库重建并在入口阶段注入，不把 `Memory` 本身变成持久化对象。
- **Rationale**: 这样既能复用现有 Agent 代码，又不会破坏 BaseAgent 的单一职责。
- **Alternatives considered**:
  - 直接把 `Memory` 序列化进数据库：实现耦合度高，而且容易把工具噪音原样落库。
  - 重写整个 Agent 记忆模型：收益不够，偏离“尽可能复用 free-code 思路但适配本项目”的目标。
