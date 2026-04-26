## ADDED Requirements

### Requirement: Conversation turns SHALL persist canonical turn facts
系统必须把单轮对话的 turn 级事实稳定持久化到 `ai_agent_message`，并保持 turn 账本与 event 账本职责分离。`query`、`files_json`、`generated_files_json`、`response`、`status`、`metrics_json`、时间字段必须继续作为 turn 级字段维护，其中 `response` 必须是该轮最终助手回答的唯一真相源，不得再从 event 明细中拼接或回推最终回答。

#### Scenario: Completed turn persists final answer in message ledger
- **WHEN** 一轮对话正常完成并产出最终助手回答
- **THEN** 系统必须把最终回答写入 `ai_agent_message.response`
- **THEN** 系统必须把上传文件与本轮生成文件分别写入 `files_json` 和 `generated_files_json`
- **THEN** 系统不得要求调用方扫描 `ai_agent_message_event` 才能获得该轮最终回答

#### Scenario: Turn without final answer still preserves turn facts
- **WHEN** 一轮对话以异常中断、强制停止或无最终回答的状态结束
- **THEN** 系统必须保留该轮 `query`、文件信息、状态和时间信息
- **THEN** 系统必须允许 `ai_agent_message.response` 为空
- **THEN** 历史读取时不得因为 `response` 为空而回退到 event payload 中猜测最终回答

### Requirement: Event ledger SHALL persist canonical event semantics in columns
系统必须把 `ai_agent_message_event` 作为单轮内部的事实账本，并把事件主语义优先持久化到标准列中。每条事件必须至少具备 `event_type`、`event_sub_type`、`display_area`、`title`、`content_text`、`status` 以及在适用时的 `task_id`、`task_order`、`tool_use_id`、`tool_name`、`tool_arguments_json`、`reference_only`、`artifact_refs_json`、`structured_data_json`。`event_sub_type` 必须承担细分语义，不得再依赖 payload 内部 subtype 字段。

#### Scenario: Assistant thought is persisted with semantic subtype
- **WHEN** 系统持久化计划思考或工具思考事件
- **THEN** 事件必须使用 `event_type=assistant_thought`
- **THEN** 事件必须用 `event_sub_type=plan` 或 `event_sub_type=tool` 表达细分语义
- **THEN** 工具思考事件如存在工具调用信息，必须把工具标识与参数写入专用列而不是仅写入 payload

#### Scenario: Tool result is persisted with canonical subtype and result columns
- **WHEN** 系统持久化 `deep_search`、`browser`、`knowledge`、`markdown`、`html`、`ppt`、`code`、`file` 或同类工具结果事件
- **THEN** 事件必须使用 `event_type=tool_result`
- **THEN** 事件必须把结果语义写入 canonical `event_sub_type`，例如 `deep_search.search`、`deep_search.report`、`browser.result`、`knowledge.answer`、`markdown.report`
- **THEN** 工具结果关联的工具调用标识、引用标记、产物引用和结构化结果对象必须写入对应专用列

### Requirement: Event payload SHALL remain an extension-only minimal payload
系统必须把 `payload_json` 约束为扩展字段容器，而不是主事实承载区。凡是能够稳定列化到标准列或 `structured_data_json` 的字段，都不得继续写入 `payload_json`。对于没有额外扩展字段的事件，系统必须允许并优先写入 `payload_json = NULL`。

#### Scenario: Standard semantic event persists with empty payload
- **WHEN** 系统持久化一条所有必要信息都已进入标准列和 `structured_data_json` 的事件
- **THEN** 系统必须允许该事件的 `payload_json` 为空
- **THEN** 历史回显与会话记忆恢复必须仍然能够仅基于标准列完成事实重建

#### Scenario: Forbidden standard fields are not duplicated into payload
- **WHEN** 系统持久化 `assistant_thought`、`plan_snapshot`、`tool_use`、`tool_result` 或 `artifact_reference` 事件
- **THEN** `payload_json` 不得再包含 `blockType`、`thoughtType`、`snapshotType`、`sourceType`、`sourceSubType`
- **THEN** `payload_json` 不得再包含 `summary`、`messageId`、`taskId`、`taskOrder`、`toolUseId`、`toolName`、`toolArguments`、`artifactRefs`
- **THEN** 文件类事件的 `payload_json` 不得内联 HTML、Markdown、PPT 或其他大块正文内容

### Requirement: Artifact references SHALL be persisted independently from payload and indexed per turn
系统必须把事件产物引用作为标准化事实独立存入 `artifact_refs_json`，并基于该列派生 `ai_agent_message.generated_files_json`。turn 级生成文件索引必须只表达“本轮生成或更新的文件”，不得通过解析 payload 反推出文件列表，也不得与用户上传文件字段混写。

#### Scenario: Duplicate event references produce one generated file entry
- **WHEN** 同一轮对话的多个 `tool_result` 或 `artifact_reference` 事件引用了同一个生成文件
- **THEN** 系统必须在相关事件的 `artifact_refs_json` 中保留标准化引用
- **THEN** 系统必须在 `generated_files_json` 中只保留一条去重后的文件记录
- **THEN** 去重后的文件记录必须仍包含稳定资源标识和可用的预览或下载元数据

#### Scenario: Uploaded files are not indexed as generated files
- **WHEN** 一轮对话同时存在用户上传文件引用和本轮生成文件引用
- **THEN** 用户上传文件必须继续只出现在 `files_json`
- **THEN** `generated_files_json` 必须只包含本轮生成或更新的文件
- **THEN** 系统不得依赖 event payload 判断某个文件是上传文件还是生成文件

### Requirement: History replay and session memory SHALL project from backend fact storage
系统在返回历史详情或重建会话记忆时，必须以 `ai_agent_message`、`ai_agent_message_event` 的事实字段为输入，重新投影出与实时对话链路兼容的前端渲染数据，而不是把数据库里的 payload 快照原样回放。前端展示效果可以保持不变，但回显数据必须来自后端事实模型的统一投影。

#### Scenario: Conversation history is rebuilt from fact ledger
- **WHEN** 客户端重新打开一个已完成会话并查询历史详情
- **THEN** 后端必须根据 turn 字段、事件标准列、`structured_data_json` 与 `artifact_refs_json` 组装返回数据
- **THEN** 后端返回的历史事件 payload 必须足以复用当前实时渲染链路
- **THEN** 历史详情生成过程不得依赖数据库中保存的前端快照字段

#### Scenario: Session memory rebuild uses fact blocks instead of replay snapshots
- **WHEN** 后端为后续续聊重建 session transcript blocks
- **THEN** 系统必须从 turn 账本和 event 账本恢复 `USER_INPUT`、`ASSISTANT_THOUGHT`、`TOOL_USE`、`TOOL_RESULT`、`ARTIFACT_REFERENCE`、`ASSISTANT_ANSWER` 等上下文块
- **THEN** 恢复逻辑必须优先消费标准列与结构化列
- **THEN** 会话记忆恢复不得要求先读取或拼接前端生成的历史快照内容
