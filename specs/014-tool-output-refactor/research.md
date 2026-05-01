# Research: 工具输出独立表重构

## Decision 1: 主账本只保留调用事实，结构化结果完全拆到新表

- **Decision**: 删除 `ai_agent_tool_invocation.output_json`，rich tool 的结构化结果全部迁移到 8 张 `ai_agent_tool_output_*` 表。
- **Rationale**: 当前 `output_json` 已经同时承担排障正文、history replay 原始数据和 rich tool 业务结果三种职责，导致 `BaseAgent`、查询服务与 projector 都要解析一份技术性 JSON。spec 明确要求本次是纯重构而不是兼容迁移，主账本应退回“调用事实账本”角色。
- **Alternatives considered**:
  - 保留 `output_json` 只做冗余副本：违反“主账本不保留结构化结果”的目标，也会让 dual-read 风险继续存在
  - 用单一大宽表承接所有 rich tool 输出：字段稀疏严重，难以表达不同工具的业务差异和索引需求

## Decision 2: 运行时直接传递 `ToolStructuredOutput`，不再经过 JSON builder / converter

- **Decision**: `ToolResultPayload` 删除 `outputJson`，新增 `ToolStructuredOutput structuredOutput`；`BaseAgent -> ToolInvocationFinishRecord -> ToolOutputWriter` 全链路直接传 typed output。
- **Rationale**: 当前 rich tool 先组装业务对象，再序列化成 `output_json`，消费端再反序列化回来，形成双模型和隐藏字段丢失风险。直接传 typed output 能保持语义单一，也满足“不要写转换器和兼容代码”的要求。
- **Alternatives considered**:
  - 继续让 rich tool 返回 `Map<String, Object>`：比 JSON 字符串好一点，但仍缺少明确边界和编译期约束
  - 在 writer 里把 `output_json` 再转 PO：这本质仍是 converter，违背本次硬约束

## Decision 3: 8 张输出表统一公共键，但 direct tool call 读取采用“新表内固定扇出”

- **Decision**: 每张输出表都保留 `tool_invocation_id` 与 `(request_id, tool_call_id)`；agent 主链路按 `toolInvocationId` 精准读取，direct tool call 通过 `requestId + toolCallId` 在 8 张新表内固定扇出查询并返回唯一命中。
- **Rationale**: `tool_invocation_id` 能直接支撑 replay/detail 的主链路；`request_id + tool_call_id` 能覆盖没有主账本关联的 direct tool call。固定 8 表扇出仍属于新体系内部读取，不是兼容旧链路，而且避免为 direct call 再造一张路由表或在每张表补 `tool_name` 冗余列。
- **Alternatives considered**:
  - direct lookup 依赖调用方额外传 `toolName`：虽然实现更直接，但把“稳定检索”责任转嫁给调用方，不如 `requestId + toolCallId` 自洽
  - 新增统一路由表：会让本次重构再引入第 9 张基础表，复杂度和一致性成本更高

## Decision 4: `deep_search` 保留 `stages_json`，且只保存已实际完成阶段

- **Decision**: `ai_agent_tool_output_deep_search` 采用 `query + answer_summary + stages_json`；`stages_json` 只写入已实际完成且有内容的阶段，不为未发生阶段补占位。
- **Rationale**: 当前 `DeepSearchToolInvocationProjector` 必须恢复 `extend / search / report` 阶段事件；如果只保留最终摘要，history replay 会丢语义。阶段结构又包含 `queries / results / docs` 多层嵌套，拆 stage 子表收益小、复杂度高。只保留已完成阶段可以避免伪造事实，终态由 `status` 补充表达。
- **Alternatives considered**:
  - 拆 stage 子表：读取和写入都更复杂，对现有 projector 没有明显收益
  - 失败/中断场景不保留阶段：会直接损失 replay 价值，违背 spec

## Decision 5: 文件引用统一归一化为 `file_refs_json`，无文件时写空数组

- **Decision**: 所有带文件结果的 rich tool 都在输出表中落 `file_refs_json`；writer 把“无文件产出”统一规范为 `[]`，reader 再与 `ArtifactView` 的稳定链接做合并。
- **Rationale**: 当前 rich tool 大都返回 `fileInfo` 数组，但 URL 稳定性来自 `ai_agent_artifact`。输出表需要保存一份可独立检索的文件引用事实，尤其给 direct tool call 使用；空数组比 `null` 更能表达“该工具支持文件，但本次未产出”。
- **Alternatives considered**:
  - 只依赖 `ai_agent_artifact`：direct tool call 没有主账本链时会丢失独立读取能力
  - 允许 `file_refs_json = null`：会把“无文件”和“字段没写”混成一个语义

## Decision 6: 终态输出表采用 terminal-only 语义，重复写入 first-write-wins

- **Decision**: 8 张输出表只记录终态行，状态只允许 `SUCCESS / FAILED / TIMEOUT`；同一调用的首次终态写入生效，后续重复终态写入忽略并记录冲突日志。
- **Rationale**: 输出表承担的是“最终业务结果快照”，不是执行时间线。时间线、started/finished/duration 仍由主账本负责。first-write-wins 与本次 clarify 结果一致，能避免“后来的失败覆盖已成功结果”或多线程重复落账造成的不可预期覆盖。
- **Alternatives considered**:
  - 输出表也保留 RUNNING / 多次 update：会把主账本与输出表都变成生命周期账本，职责重复
  - last-write-wins：重复回调或重试容易覆盖更可信的首个终态

## Decision 7: 失败场景允许最小化 typed output，但 replay/detail 必须可解释

- **Decision**: rich tool 在失败/超时场景下也必须写一条输出表终态记录；若没有完整业务内容，允许 typed output 只保留最小字段，解释性由 `status + error_msg + llmObservation fallback` 共同保证。
- **Rationale**: spec 要求失败场景下主账本与结构化输出记录终态一致，同时 history replay 仍可解释。如果强求失败时也有完整业务字段，会导致各 tool 编造无意义结构；把最小 typed output 与主账本错误信息结合，更符合事实。
- **Alternatives considered**:
  - 失败时不写输出表：会破坏“一次终态执行只对应一条结构化输出记录”的约束
  - 失败时强行写完整业务字段：多数工具拿不到，最终只能制造伪数据
