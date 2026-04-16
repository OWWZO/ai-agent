# Research: 对话历史最终态重构与一致性修复

## Decision 1: “最终态”定义为最终界面可见细节块，而不是最小摘要

- **Decision**: 历史持久化与历史重开都以“对话结束时界面最终仍可见的细节块”为准，保留思考过程、计划、任务分组、工具调用、搜索/总结卡片、最终答案和工作区结果，而不是压缩成少量摘要事件。
- **Rationale**: 用户明确要求“几天后回来仍看到结束时那份完整对话细节”，因此历史真相源必须对应最终界面态，而不是过程回放或最小摘要替代视图。
- **Alternatives considered**:
  - 只保留“搜索完成 / 总结完成 / 最终回复”摘要：读取简单，但直接违背用户目标。
  - 完整保存所有流式增量：信息最全，但会重新回到实时回放模型，超出本次范围。

## Decision 2: `ai_agent_message_event` 继续保留，但语义收敛为最终界面细节表

- **Decision**: 保留 `ai_agent_message_event` 作为历史细节主表，一条记录对应一个最终界面可见细节单元。
- **Rationale**: 现有列表/turn/event 分层已被项目和前端链路广泛使用，问题在于事件表语义错误，而不是三表结构本身不可用。
- **Alternatives considered**:
  - 把所有细节塞回 `ai_agent_message`：会让单轮账本再次膨胀成 rich JSON 容器。
  - 改成单条大快照：写入简单，但多条同类细节和跨区域关系难以稳定表达。

## Decision 3: 结构化最终细节模型正式覆盖 `PLAN_SOLVE` 和 `REACT`

- **Decision**: `PLAN_SOLVE` 与 `REACT` 共用同一套结构化最终细节历史模型；普通 `CHAT` 继续保持轻量历史。
- **Rationale**: 这两类模式都存在思考、计划、工具调用、搜索/总结、工作区等结构化界面细节，长期维护两套结构化历史语义风险更高；普通聊天则没有必要被强制迁移到复杂模型。
- **Alternatives considered**:
  - 只覆盖 `PLAN_SOLVE`：会让 `REACT` 长期滞留在不同语义下，增加分叉。
  - 所有模式统一结构化：实现范围过大，且对普通聊天收益有限。

## Decision 4: 流结束时投影最终界面细节块，而不是读取时二次拼装

- **Decision**: 在 `AgentStreamPersistServiceImpl` 中保留运行时增量缓冲，但在消息完成时统一投影成最终界面细节块集合，再一次性写入 `ai_agent_message_event`。
- **Rationale**: 只有在流结束时，系统才能确定哪些内容最终仍留在界面上；若继续在读取时从摘要或过程片段临时拼装，历史结果仍会和真实结束态偏离。
- **Alternatives considered**:
  - 继续全量落库再在读取时筛选：读取规则复杂且容易回退。
  - 只依赖最终 `response` 和 `metrics` 推导：无法重建思考面板、工具调用和任务分组。

## Decision 5: 复用现有前端消息类型和恢复链路，避免重做整套历史 UI

- **Decision**: 详情接口继续返回 `turns[].events[]`，并尽量复用现有前端消费语义，使 `restoreTurn`、`combineData`、`buildReplayTaskData`、`Dialogue`、`ChatView` 可以在调整输入语义后继续工作。
- **Rationale**: 当前 UI 已经具备按 `messageType / resultMap` 还原思考、任务和工具调用的能力，真正需要修正的是事件内容来源，而不是推翻整套渲染链路。
- **Alternatives considered**:
  - 新建全新 `details[]` 契约和全新历史渲染器：语义更纯，但联动范围大、返工高。
  - 完全不动前端契约：无法表达这次新的最终界面细节要求。

## Decision 6: `plan_thought`、`tool_thought`、`task_summary`、`result` 允许作为最终细节保留

- **Decision**: 这些类型不再被一刀切视为“过程回放垃圾数据”；只要它们在对话结束时仍然是界面可见块，就必须以最终文本或最终状态写入历史事件。
- **Rationale**: 用户明确要求思考过程和工具调用细节在历史中完整可见；因此判断标准应是“结束时是否可见”，而不是“类型名是否像过程事件”。
- **Alternatives considered**:
  - 永远排除 `plan_thought/tool_thought`：会直接丢失用户最关注的细节。
  - 保留所有该类型的增量：又会回到回放模式。

## Decision 7: 同一细节跨对话区/工作区时只保留一份 canonical 记录

- **Decision**: 当同一最终细节既在对话区可见，又关联工作区预览时，只保留一份 canonical 事件记录，并通过 `displayArea` 和 payload 中的展示关系/产物引用恢复多区域关系。
- **Rationale**: 单一真相源可以避免对话区文本与工作区预览漂移，也能减少重复写入与重复删除问题。
- **Alternatives considered**:
  - 对话区和工作区各存一份：实现直觉，但后续一致性最差。
  - 只保留工作区记录：会让时间线丢失上下文。

## Decision 8: `artifactRefs[]` 继续作为 canonical 产物引用表达

- **Decision**: 文件、HTML、Markdown、报告等最终产物统一以 `payload.artifactRefs[]` 表达；如旧组件需要 `fileInfo`，只在响应层或前端兼容层派生。
- **Rationale**: 这与现有 `ConversationEventPayloadNormalizer` 和前端 `mergeArtifactRefsIntoPayload` 能力一致，最容易稳定支持历史预览和缺失态。
- **Alternatives considered**:
  - 数据库继续混用 `artifactRefs` 和 `fileInfo`：写读两侧都要维护兜底分支。
  - 只保存本地临时路径：历史重开天然不稳定。

## Decision 9: 旧历史数据直接清理，不做迁移兼容

- **Decision**: 上线切换时允许直接删除旧历史数据，不设计双读、迁移或回填。
- **Rationale**: 用户已明确接受删除旧数据；把精力集中在新模型正确性和前端最终界面一致性上收益最高。
- **Alternatives considered**:
  - 双路径兼容：会让 005 长期背负两套历史语义。
  - 回填迁移：对旧错误模型的映射成本高，且结果不稳定。
