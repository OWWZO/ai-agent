## ADDED Requirements

### Requirement: Tool invocation ledger SHALL persist canonical LLM observations
系统必须为每次工具调用持久化一份专门供主智能体消费的 observation，并将其写入 `ai_agent_tool_invocation.llm_oberserve`（代码层对应 `llmObservation`）。该字段必须表示最终写入主智能体 `tool` message 的内容，而不是工具原始返回值或泛化文本输出。

#### Scenario: ReAct/Executor tool result is persisted after final observation shaping
- **WHEN** ReAct 或 Executor agent 完成一次工具调用，并在写回主智能体前对工具结果执行截断或追加文件摘要
- **THEN** 系统必须将同一份最终 observation 同时写入主智能体 `tool` message 和 `llm_oberserve`

#### Scenario: Planning single-tool execution follows the same observation contract
- **WHEN** Planning agent 通过单工具执行路径调用工具并把结果回传给主智能体
- **THEN** 系统必须使用与批量工具执行相同的 observation 生成规则，并将最终 observation 写入 `llm_oberserve`

### Requirement: Structured tool output SHALL remain separate from LLM observation
系统必须继续保留工具最终结构化结果的独立持久化能力，并将其写入 `ai_agent_tool_invocation.output_json`。`output_json` 不得替代 `llm_oberserve`，除非某工具明确将其结构化字符串本身定义为主智能体 observation。

#### Scenario: Tool returns structured result and LLM observation simultaneously
- **WHEN** 某工具既产生结构化结果，又需要向主智能体回传单独的 observation
- **THEN** 系统必须分别持久化 `output_json` 与 `llm_oberserve`

#### Scenario: Tool has no structured result
- **WHEN** 某工具只产生纯文本 observation 而没有结构化结果
- **THEN** 系统必须写入 `llm_oberserve`，并允许 `output_json` 为空

### Requirement: deep_search SHALL return compact retrieval observations to the main agent
`deep_search` 必须将“前端展示用的完整结构化结果”和“主智能体消费的 observation”分离。主智能体 observation 必须是精简后的检索摘要，至少包含查询拆解、命中文档标题、来源链接和内容摘要，使主智能体知道搜索了哪些来源以及获得了哪些关键信息；系统不得继续把完整阶段型 `output_json` 直接作为主智能体 tool result 回传。

#### Scenario: deep_search returns search evidence summary
- **WHEN** `deep_search` 成功完成检索并拿到 `searchResult.docs`
- **THEN** 系统必须为主智能体生成包含子查询、`title`、`link` 和内容摘要的 observation
- **THEN** 系统必须继续保留完整阶段型结构化结果到 `output_json`

#### Scenario: deep_search observation excludes raw staged payload
- **WHEN** `deep_search` 生成主智能体 observation
- **THEN** 该 observation 不得直接包含完整 `extend/search/report` 阶段原始 payload
- **THEN** 该 observation 必须是经裁剪和归纳后的紧凑结果

#### Scenario: deep_search timeout or failure falls back to textual observation
- **WHEN** `deep_search` 超时或失败，无法形成完整检索摘要
- **THEN** 系统必须将错误或超时说明作为 `llm_oberserve` 写入主智能体 observation
- **THEN** 系统不得伪造成功态的结构化检索摘要
