## Context

当前会话记忆与流式持久化链路已经完成过多轮演进，但仍残留明显的双轨模型问题：

- 旧的 `AgentStreamPersistServiceImpl` 同时处理会话守卫、HTTP 请求构建、SSE 逐行消费、事件投影、消息落库、异常兜底，类体量和职责都已失控。
- `SessionTurnMemory` 同时维护 `blocks` 与 `userMessage` / `assistantMessage` / `finalAnswer` 两套 transcript 表示，导致 working memory 组装和压缩逻辑存在大量 fallback。
- `ai_agent_session_memory` 仍保留 `facts_json`、`boundary_message_id` 等旧兼容字段，但当前业务已经明确允许清理历史数据，不需要继续承担兼容负担。
- `ConversationEventPayloadNormalizer`、`SessionArtifactRestoreSupport` 等支持类中存在多层 `resultMap`、字段别名和 legacy `fileInfo` / `fileList` 解析，使事件事实模型不再清晰。

这次改造的约束已经明确：

- 不改 Agent 执行引擎核心循环，不改 SSE 协议和前端交互格式。
- 旧数据可以直接删除，不做双写、不做回填、不保留运行时兼容层。
- 重构后每个核心类应保持职责单一，并控制在可独立测试的体量内。

## Goals / Non-Goals

**Goals:**

- 删除会话记忆与事件恢复链路中的全部 legacy transcript / facts / payload 兼容逻辑。
- 让 `SessionTurnMemory.blocks` 成为唯一 transcript 表示，统一 working memory、压缩结果和续聊恢复链路。
- 让 `ai_agent_session_memory` 只保留当前真实使用的边界与摘要字段，删除 `facts_json`、`boundary_message_id`。
- 将旧的 `AgentStreamPersistServiceImpl` 拆分并重命名为 `AgentStreamPersistCoordinator`，同时引入流执行、事件投影、统一持久化三个可单测组件，主服务只保留协调职责。
- 保持 `generated_files_json` 作为写时聚合的只读缓存，而不是读时兜底事实源。

**Non-Goals:**

- 不调整 Planning / ReAct / Executor 主循环行为。
- 不改变对前端输出的 SSE 事件协议与历史展示格式。
- 不在本次引入新的表、外部依赖或新的缓存层。
- 不做旧历史数据迁移或线上兼容读。

## Decisions

### 1. 将 `SessionTurnMemory` 收敛为 blocks-only transcript

`SessionTurnMemory` 删除 `userMessage`、`assistantMessage`、`finalAnswer` 三个旧版扁平字段，仅保留：

- `messageId`
- `requestId`
- `sortOrder`
- `blocks`
- `artifactRefs`

原因：

- `blocks` 已经能完整表达用户输入、助手思考、工具调用、工具结果、最终回答和产物引用，不需要再维护第二套扁平表示。
- 旧字段继续存在会迫使 `SessionWorkingMemoryAssembler` 与 `SessionMemoryCompactionService` 保留 fallback，直接破坏“唯一事实模型”的目标。

备选方案：

- 保留旧字段但停止写入。拒绝原因：读取端仍然需要兼容空值与脏值，复杂度并不会真正下降。

### 2. 简化 `ai_agent_session_memory` 边界模型，仅保留 `boundary_sort_order`

会话记忆压缩边界只保留稳定、可排序的 `boundary_sort_order`。删除：

- `facts_json`
- `boundary_message_id`

原因：

- `facts_json` 本质是旧兼容投影，当前已不再是工作记忆输入。
- `boundary_message_id` 是自增主键，不如 `sort_order` 适合作为压缩边界标识；现有业务筛选也实际依赖 `boundary_sort_order`。

备选方案：

- 保留字段但不再使用。拒绝原因：会继续污染实体、DAO、Mapper、建表脚本与测试样例，无法达到表结构收敛目标。

### 3. 将旧的 `AgentStreamPersistServiceImpl` 重构为 `AgentStreamPersistCoordinator` 与三段式持久化流水线

拆分后的职责如下：

- `StreamExecutor`：负责 `AgentRequest` 到 HTTP/SSE 流的技术执行，包括请求构建、异步调用、逐行读取和回调分发。
- `EventProjector`：负责把标准 `AgentResponse` 投影为 `OrderedEvent` 列表，集中承载 `resolveXxx` / `buildXxx` / `extractXxx` 语义转换。
- `PersistCoordinator`：负责流结束后的统一落库，调用 message、event、conversation 相关持久化能力写入最终账本。

`AgentStreamPersistCoordinator` 保留：

- 会话解析与守卫检查
- working memory 注入
- 流式过程中对前端转发的总协调
- 对三类子组件的编排

原因：

- 当前类的复杂度主要来自“技术执行 + 领域投影 + 持久化”耦合在一起，拆开后每段都能独立测试和演进。
- 这三段的边界稳定，且与本次“不改核心 Agent 引擎”的约束不冲突。

备选方案：

- 只做私有方法下沉，不新增组件。拒绝原因：职责仍然挂在同一个实现类上，测试和依赖隔离收益有限。

### 4. 事件恢复与标准化仅接受标准结构，不再兼容 legacy payload

以下类统一删除兼容逻辑：

- `ConversationEventPayloadNormalizer`：删除多层 `resultMap`、legacy `fileInfo` / `fileList` 与嵌套对象兜底解析，只处理标准单层结构。
- `SessionArtifactRestoreSupport`：删除 `fileName|name`、`ossUrl|downloadUrl|url` 等别名兼容，只接受标准字段。
- `SessionTranscriptBlockAssembler`：只根据标准化事件与 `artifactRefs` 构建 transcript blocks，不再从 `generated_files_json` 做事实兜底。

原因：

- 本次已经明确“旧数据可删”，继续保留兼容入口只会让标准结构失去约束力。
- 历史恢复、工作记忆恢复和事件投影必须基于同一套标准字段，否则维护成本会再次反弹。

备选方案：

- 保留一层宽松解析以防外部脏数据。拒绝原因：`AgentResponse` 是内部协议，事件落库结构也完全由本服务控制，没有必要为不会再存在的旧结构付出长期复杂度。

### 5. 保留 `generated_files_json`，但降级为写时缓存而非读时事实源

`ai_agent_message.generated_files_json` 继续保留，其职责定义为：

- 在流结束时基于已确定的事件账本聚合出 turn 级文件摘要
- 为历史列表、快速查询和轻量展示提供读优化

明确禁止：

- 用它兜底恢复 transcript blocks
- 用它替代 `artifactRefs` 或事件标准字段
- 在读取端和事件账本独立维护两套事实

原因：

- turn 级文件索引仍有查询价值，但不能反向污染事件事实模型。

### 6. 压缩失败熔断器保留在 `AgentSessionMemoryServiceImpl`

压缩失败后的 guardrail 降级机制继续保留在 `AgentSessionMemoryServiceImpl`，本次不下沉到 `SessionMemoryCompactionService`。

原因：

- 熔断器属于“服务级降级策略”，而不是压缩算法本体。
- 保留现有位置可以避免本次重构把故障处理和模型简化耦合在一起。

## Risks / Trade-offs

- [一次性删除兼容字段后，遗漏引用会导致编译或运行失败] → 通过编译期清理、Mapper 校验和定向回归测试一次性收口全部调用点。
- [`EventProjector` 在首版拆分后仍可能接近上限体量] → 在实现阶段按事件域继续抽出局部私有 helper，确保主类保持在 300-500 行内。
- [删除宽松 payload 解析后，任何非标准写入都会更早暴露] → 这是预期结果；通过单测和回归测试固定标准结构，而不是继续吞掉脏数据。
- [`generated_files_json` 与事件账本存在重复信息] → 接受这份只读缓存冗余，但限定为单向派生，禁止双向写入。
- [发布期间若仍存在旧历史数据，续聊恢复会失败] → 发布前清空旧数据，并将这次上线视为一次性切换，而不是灰度兼容发布。

## Migration Plan

1. 更新 `ai_agent_session_memory` 建表脚本和迁移脚本，删除 `facts_json`、`boundary_message_id`。
2. 同步更新 `AgentSessionMemory` 实体、DAO、Mapper、压缩结果与相关测试样例，移除对应字段。
3. 精简 `SessionTurnMemory`、`SessionWorkingMemoryAssembler`、`SessionMemoryCompactionService`，删除 transcript/facts fallback。
4. 新增 `StreamExecutor`、`EventProjector`、`PersistCoordinator`，并将旧的 `AgentStreamPersistServiceImpl` 重构并改名为 `AgentStreamPersistCoordinator`。
5. 重构历史回放、事件标准化和产物恢复支持类，仅保留标准字段路径。
6. 执行后端定向回归测试；发布前清空旧会话记忆与旧历史数据。

回滚策略：

- 应用代码如需回滚，必须同时清理新版本写入的会话记忆和历史数据，再回退到旧版本。
- 本次不提供新旧结构并行兼容，因此不支持“带着新数据回滚到旧代码”。

## Open Questions

- 当前无阻塞性开放问题。实现阶段只需要按编译结果和测试覆盖清理残余引用即可。
