# Research: 对话历史最终态重构与一致性修复

## Decision 1: 保留三表分层，但彻底重定义职责

- **Decision**: 继续保留 `ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event` 三表，但职责明确为“会话摘要 / 单轮结果账本 / 最终态细节事件”。
- **Rationale**: 当前三表已经覆盖列表、单轮请求、历史细节三个层次，真正的问题不是表数量，而是事件表仍保留大量过程回放语义，且消息表和事件表存在职责重叠。
- **Alternatives considered**:
  - 把所有最终态细节塞回 `ai_agent_message`：实现看似更简单，但会把单轮账本再次膨胀成 rich JSON 容器。
  - 压缩成两张表：归一化更高，但要同时重写列表、详情、上下文和前端恢复链路，改动面过大。

## Decision 2: `ai_agent_message_event` 收敛为最终态细节表

- **Decision**: `ai_agent_message_event` 继续作为历史细节主表，但只保留最终态细节事件；一条记录只表达一个用户最终可见的细节项。
- **Rationale**: 这能直接解决“多个同类细节被折叠”“plan 最终态回退”“历史重开依赖过程事件二次拼装”三类问题，同时保持查询路径清晰。
- **Alternatives considered**:
  - 同时保留过程事件和最终事件：查询逻辑仍要判别哪类事件该展示，复杂度没有真正下降。
  - 只留一条大 JSON 快照：写入简单，但多条同类最终细节仍可能在读取时被错误合并。

## Decision 3: 在流结束时做最终态投影，而不是在读取时重放过程

- **Decision**: 在 `AgentStreamPersistServiceImpl` 中保留运行时过程缓冲，但只在消息结束时把“最终仍可见的内容”投影为最终细节事件，再统一持久化。
- **Rationale**: 用户不再要求历史逐步骤回放，继续原样落库并在读取时重建，只会让历史详情和最终界面结果更容易发生偏差。
- **Alternatives considered**:
  - 继续把原始过程事件全量落库：能回放，但与本次最终态目标冲突。
  - 只在读取时从 `response + metrics` 推导细节：对 plan 状态、多个同类工具细节和 workspace 产物都不够稳定。

## Decision 4: `ai_agent_message` 只保留单轮最终答案、状态、指标和摘要

- **Decision**: `ai_agent_message` 保留单轮 query、附件、最终答案 `response`、状态、指标、时间戳和软删除，不再承载最终细节明细。
- **Rationale**: 这既满足你“细节从 `ai_agent_message_event` 查”的澄清，也保留了 `CHAT` 上下文窗口与列表摘要需要的最小结果文本。
- **Alternatives considered**:
  - 把最终细节也写进 `ai_agent_message`：又会回到 rich JSON 与事件表双写。
  - 完全移除 `response`：会额外冲击 chat 上下文与已有摘要逻辑，收益不成比例。

## Decision 5: `ai_agent_conversation` 继续只做轻量摘要，并同步清理重复索引

- **Decision**: `ai_agent_conversation` 只保留归属、标题、模式、排序、预览、计数等轻量摘要字段；同时在三张表层面清理重复或无消费价值的索引。
- **Rationale**: 这最符合历史列表的职责，也能顺带降低不必要的索引维护成本。
- **Alternatives considered**:
  - 在会话表增加整段会话最终快照：读取更快，但会再次制造多份真相源。
  - 保留所有现有索引不动：最省事，但和这次“去冗余、可维护”的目标相违背。

## Decision 6: `artifactRefs` 是 payload 的唯一 canonical 文件引用表达

- **Decision**: 最终态细节事件中的文件/报告/工作区结果统一以 `payload_json.artifactRefs[]` 表达；`fileInfo` 只作为读侧兼容桥接，不再作为持久化原始字段依赖。
- **Rationale**: 当前后端已有 `ConversationEventPayloadNormalizer` 和前端 `mergeArtifactRefsIntoPayload` 能力，继续复用最稳，也能彻底解决历史工作区文件丢失路径的问题。
- **Alternatives considered**:
  - 继续让数据库 payload 混用 `artifactRefs` 和 `fileInfo`：写读两侧都会继续维护兜底分支。
  - 只存本地工作区路径：几天后回看历史会天然失效。

## Decision 7: 详情 API 延续 `turns[].events[]` 外壳，但语义改为最终细节

- **Decision**: 为控制 UI 改造范围，详情接口继续返回 `turns[].events[]`，但这些 `events` 只表示最终细节事件；`isFinal` 可在响应层作为常量兼容位输出。
- **Rationale**: 现有 `restoreTurn`、`combineData`、`handleTaskData` 和 `FilePreview` 仍可复用，重点改的是输入语义而不是重写整套前端渲染。
- **Alternatives considered**:
  - 立刻改成全新 `details[]` 契约：语义更干净，但前后端联动面更大。
  - 完全维持旧 `events[]` 语义：无法真正摆脱过程回放模型。

## Decision 8: 旧历史数据直接清理，不做双路径兼容

- **Decision**: 新模型上线前直接删除旧历史数据，不引入双读、回填或迁移脚本。
- **Rationale**: 这是用户明确澄清的边界，也能把改造复杂度严格控制在新模型正确性上。
- **Alternatives considered**:
  - 兼容旧数据的降级读取：需要额外保留一套旧语义映射，性价比低。
  - 双路径长期共存：会把 005 重新拖回“同时维护两套历史模型”的状态。
