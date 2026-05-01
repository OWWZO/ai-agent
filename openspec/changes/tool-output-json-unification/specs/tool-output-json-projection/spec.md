## ADDED Requirements

### Requirement: Tool invocations SHALL persist tool-native output JSON for every execution
系统必须为每次工具调用持久化一份独立的 `output_json`，用于表达该工具的最终原生结果事实。`output_json` 必须是可解析 JSON，并且对于新写入记录必须包含可版本化的根级版本标识；纯文本与失败结果必须落统一 fallback 结构，rich tool 则必须落稳定的工具专属结构。

#### Scenario: Plain-text tool persists fallback JSON
- **WHEN** 某个只返回纯文本结果的工具完成执行，且没有额外的结构化结果
- **THEN** 系统必须为该次调用写入包含 `schemaVersion`、`resultType=plain_text` 和文本数据的 `output_json`
- **THEN** 该次调用不得因为工具未手动构造结构化结果而留下空 `output_json`

#### Scenario: Failed tool persists error fallback JSON
- **WHEN** 某个工具执行失败、超时或返回空结果，无法产出成功态结构化结果
- **THEN** 系统必须写入包含 `schemaVersion`、`resultType=error` 和错误说明的 `output_json`
- **THEN** 失败结果不得只存在于日志或异常文本而缺失独立 `output_json`

#### Scenario: Rich tool persists stable native JSON
- **WHEN** `deep_search`、`file_tool`、`code_interpreter`、`report_tool`、`data_analysis`、`multimodalagent_tool`、`image_generation_tool` 或 `script_runner_tool` 完成执行
- **THEN** 系统必须写入该工具稳定的原生结果 JSON shape
- **THEN** 该 shape 必须包含 `schemaVersion`，并保留该工具后续 replay 所需的核心事实字段

### Requirement: Tool-native output JSON SHALL remain separate from LLM observation and frontend replay fields
系统必须保持 `output_json`、`llmObservation` 与前端 replay 展示字段三者分离。`llmObservation` 只表示主智能体继续推理实际看到的 observation；`output_json` 只表示工具事实；前端排序、卡片类型和事件包装字段不得直接写入 `output_json`。

#### Scenario: Tool returns both observation and structured result
- **WHEN** 某次工具调用同时产生供主智能体消费的 `llmObservation` 和供历史/分析使用的结构化结果
- **THEN** 系统必须分别持久化 `llmObservation` 与 `output_json`
- **THEN** 系统不得用 `output_json` 取代 `llmObservation`，也不得把 `llmObservation` 的最终文本再次编码成前端事件字段写入 `output_json`

#### Scenario: Frontend-only fields are excluded from output JSON
- **WHEN** 系统为任意工具调用构建新的 `output_json`
- **THEN** `output_json` 根节点及其稳定业务字段中不得直接包含 `taskId`、`taskOrder`、`messageOrder`、`renderKind`、`eventData` 或等价的前端展示顺序字段
- **THEN** 这些展示字段如有需要，必须由 replay 投影层在读取阶段重新计算或组装

### Requirement: Replay SHALL project tool events from tool-native output JSON by tool name
历史恢复与分析链路必须基于 `tool_name + input_json + output_json + artifact` 解析工具结果，并按工具名称分发到对应 projector。系统不得再要求 `output_json` 自身长成前端 `eventData` 结构，也不得仅依赖纯文本猜测工具展示类型。

#### Scenario: File tool replay uses native JSON and artifacts
- **WHEN** 历史 replay 读取到一次 `file_tool` 调用，且该次调用在 `output_json` 中记录了逻辑 `fileInfo`，同时 artifact 账本中存在稳定文件引用
- **THEN** 系统必须通过 `file_tool` 专属 projector 合并这些事实并投影出文件结果事件
- **THEN** projector 必须优先使用 artifact 账本补齐稳定资源引用，而不是把 `output_json` 直接当作前端事件返回

#### Scenario: Deep search replay expands staged native JSON
- **WHEN** 历史 replay 读取到一次 `deep_search` 调用，且 `output_json` 中包含阶段化 `stages` 结果
- **THEN** 系统必须通过 `deep_search` 专属 projector 按阶段投影出前端现有需要的结果事件
- **THEN** 系统不得要求 `deep_search` 在持久化时直接保存现成的前端阶段事件数组

#### Scenario: Unknown tool falls back to default projector
- **WHEN** 历史 replay 遇到没有专属 projector 的工具调用，但该调用仍然持久化了 fallback 或可解析的 `output_json`
- **THEN** 系统必须通过默认 projector 生成至少一条可展示的降级结果事件
- **THEN** 系统不得因为缺少专属 projector 而完全丢失该次工具调用的历史结果

### Requirement: Artifact-heavy tools SHALL avoid duplicating large content in output JSON
对于文件、图片、代码执行产物等 artifact-heavy 工具，系统必须把稳定资源引用继续落到 artifact 账本，并限制 `output_json` 只保留必要的逻辑元信息、摘要或小型 preview。系统不得把超长文件正文或大体积产物重复塞入 `output_json` 作为默认行为。

#### Scenario: File read stores artifact-first output JSON
- **WHEN** `file_tool/get` 读取的内容较长，且该结果已经通过 artifact 或现有文件服务保存了稳定引用
- **THEN** 系统必须在 `output_json` 中明确该次结果采用 artifact-first 或等价的内容存储模式
- **THEN** 系统默认不得把完整长文本正文重复写入 `output_json`

#### Scenario: Generated files keep logical file info without replacing artifacts
- **WHEN** `code_interpreter`、`report_tool`、`image_generation_tool` 或 `script_runner_tool` 生成了用户可见文件
- **THEN** 系统可以在 `output_json` 中保留逻辑 `fileInfo`、摘要或结果说明
- **THEN** 最终稳定的下载、预览和资源追踪能力仍必须依赖 artifact 账本，而不是只依赖 `output_json`
