# Research: 对话细节统一 UI 与最终态历史重构

## Decision 1: 历史与进行中共享同一份 canonical detail contract

- **Decision**: 历史详情接口继续返回 `turns[].events[]` 外层结构，但每个事件的 `payload` 必须直接对齐前端进行中链路已消费的 `MESSAGE.EventData` 语义，即统一使用 `messageType`、`messageId`、`taskId`、`taskOrder`、`resultMap` 这组核心字段。
- **Rationale**: 当前进行中对话已经通过 `combineData`、`handleTaskData`、`buildReplayTaskData` 驱动左侧细节区与右侧工作区。把历史数据直接收敛到同一 contract，才能满足“入口和处理逻辑一致，只是数据来源不同”。
- **Alternatives considered**:
  - 历史单独走一层 normalization，再进入共享渲染器：短期可行，但会保留历史专用处理链路。
  - 只共享最终渲染组件，不共享输入 contract：未来最容易再次分叉。

## Decision 2: 保留三表结构，但收敛为摘要账本 + 最终细节快照

- **Decision**: 继续保留 `ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event` 三层结构，但只让它们承担会话摘要、单轮账本、最终可见细节快照三种职责，不再为实时回放保留冗余字段和语义。
- **Rationale**: 现有列表、详情、作用域校验、排序和删除逻辑已经围绕三层结构搭建完成，问题在于事件表承担了过多“过程回放”含义，而不是结构本身需要推倒重来。
- **Alternatives considered**:
  - 把所有最终细节塞回 `ai_agent_message.response/metrics_json`：会重新形成大 JSON 容器，不利于顺序恢复和多块明细。
  - 新建第四张“history_snapshot”表：语义可控，但会增加迁移和维护成本。

## Decision 3: 每个最终可见细节块独立持久化为一条明细记录

- **Decision**: `ai_agent_message_event` 中一条记录只对应一个对话结束时仍可见的细节块；稳定 `block identity` 由 `payload.messageId` 承担，`seq_no` 表示最终展示顺序，`status` 表示终态，`payload_json` 保存最终快照。
- **Rationale**: 当前历史错乱的根因之一就是多条 `deep_search` 被合并进同一条记录，读取时又无法无损拆回。逐块持久化后，历史读取不再需要猜测或拆分。
- **Alternatives considered**:
  - 同类细节块合并存一条记录，读取时再拆：实现最省事，但已经证明会丢失多条搜索结果。
  - 只保存一份大快照：顺序、分组、跨区域关联和局部点击都不稳定。

## Decision 4: 所有已结束终态都保留“最后可见界面”

- **Decision**: `completed`、`error`、`force_stop` 三类终态都要持久化该轮结束瞬间最后仍可见的细节块，并将终态信息同步写入 turn 与 event。
- **Rationale**: 用户回看历史时，不会接受只有成功完成的会话才使用统一 UI，错误结束或手动停止也必须回到最后看到的那一版界面。
- **Alternatives considered**:
  - 只有 `completed` 才做完整历史：会继续保留终态分叉。
  - `error/force_stop` 只保留摘要：无法满足“同一套 UI 基线”。

## Decision 5: 在流结束时投影最终块，而不是读取时二次推导

- **Decision**: 继续在运行期缓冲 SSE 增量，但只在消息终态到达时把当前界面仍可见的块投影成最终快照，并一次性覆盖写入 `ai_agent_message_event`。
- **Rationale**: 哪些块最终留在界面上，只有在流结束时才能准确判断。若把责任留到历史读取阶段，就会再次引入“根据少量摘要猜最终界面”的问题。
- **Alternatives considered**:
  - 读取历史时从摘要事件再拼装：当前 bug 就来源于此。
  - 全量保存每个流式片段：会重新落回实时回放模型。

## Decision 6: 历史仍复用当前进行中 UI，而不是新建历史专用界面

- **Decision**: 统一方案优先修改后端持久化语义和历史详情返回形状，让历史数据去适配 `ChatView`、`Dialogue`、`ActionView`、`FilePreview` 这条现有进行中渲染链，而不是新增第三套历史组件树。
- **Rationale**: 当前进行中 UI 已经具备计划、思考、工具、搜索、工作区联动能力；本次的核心问题是历史数据喂不准，而不是这些组件本身不能用。
- **Alternatives considered**:
  - 新建 history-only renderer：短期可控，但会永久制造双轨 UI。
  - 大改进行中 UI 去适配旧历史：违背“以当前进行中界面作为唯一基线”。

## Decision 7: `artifactRefs[]` 继续作为跨区域产物的 canonical 引用

- **Decision**: 文件、HTML、Markdown、报告等最终产物统一以 `payload.artifactRefs[]` 表达；右侧工作区预览和左侧时间线点击都围绕这份引用工作，缺失态通过 `missing/missingReason` 明示。
- **Rationale**: 项目里已经存在 `ConversationEventPayloadNormalizer` 和前端 `historyArtifacts` 兼容工具，沿用 `artifactRefs[]` 最容易稳定支撑“重开历史仍可预览或可解释失败”。
- **Alternatives considered**:
  - 继续混用 `fileInfo/fileList/artifactRefs`：读写两边都会继续堆兼容分支。
  - 只保存临时路径：历史几天后重开天然不稳定。

## Decision 8: 历史详情读取改为批量加载 event，避免会话放大后出现 N+1

- **Decision**: `AgentConversationServiceImpl` 在详情装配阶段应支持按 messageId 集合批量查询 `ai_agent_message_event`，再按 `message_id + seq_no` 分组排序回填，而不是继续逐 message 单查事件。
- **Rationale**: 统一 UI 后每轮会保留更多最终细节块，继续逐轮单查事件会让详情接口随着轮次数增加出现额外数据库往返。
- **Alternatives considered**:
  - 保持当前逐 message 查询：实现最少，但会随着事件量上升放大响应抖动。
  - 把 event 再塞回 message 大字段：可以省查询，但失去清晰的数据职责。

## Decision 9: 旧错误历史数据不做兼容，切换前直接清理

- **Decision**: 本次不为旧错误持久化模型做迁移、双读或回填逻辑；上线切换前允许直接清空旧历史数据。
- **Rationale**: 用户已经明确接受删除旧数据，把复杂度集中到新模型正确性上收益最高。
- **Alternatives considered**:
  - 双路径兼容：会让历史链路长期背负两套语义。
  - 回填迁移：旧数据本身已经缺块或错态，迁移结果不可信。
