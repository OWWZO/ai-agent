## ADDED Requirements

### Requirement: Shared image generation execution SHALL normalize request defaults and upstream results
系统 MUST 为生图工作台与 `image_generation_tool` 提供统一的请求默认值处理和上游结果归一化能力，包括 `size`、`n`、`timeoutSeconds`、`model` 透传，以及图片文件元数据的统一封装。

#### Scenario: Shared kernel fills default request values
- **WHEN** 任一调用方未显式提供 `size` 或 `n`
- **THEN** 共享执行内核必须使用约定的默认值完成上游请求组装，并把实际值写回统一执行结果

#### Scenario: Shared kernel normalizes upstream file results
- **WHEN** 上游生图接口返回 `fileInfo` 文件列表和补充元数据
- **THEN** 共享执行内核必须输出统一的文件结果集合与批次级元数据，供 workspace 与 tool 共同消费

### Requirement: Shared image generation outputs SHALL be recorded as one batch plus per-file artifacts
每次成功的生图执行 SHALL 在共享账本中形成“一批次主记录 + 多个图片 artifact 明细”的持久化结构；批次记录 MUST 标识来源，artifact 明细 MUST 可被该批次稳定回查。

#### Scenario: Workspace batch is persisted without run context
- **WHEN** 工作台成功生成图片且当前链路不存在 `runId`
- **THEN** 系统必须仍然能够借助 `requestId`、`toolCallId` 和 artifact 明细完成批次持久化与后续读取

#### Scenario: Agent tool batch is persisted with agent source
- **WHEN** `image_generation_tool` 成功执行
- **THEN** 系统必须把该批次写入共享账本，并把来源标记为 `agent` 以区别于工作台批次

### Requirement: image_generation_tool SHALL expose the same batch metadata model as the workspace pipeline
`image_generation_tool` 的 structured output MUST 与共享执行结果保持一致，至少包含 `size`、`batchCount`、`sourceImageCount`、`maskImageCount`、`usedFallback` 和输出文件引用列表。

#### Scenario: Tool returns rich structured output after successful generation
- **WHEN** `image_generation_tool` 成功生成图片
- **THEN** 返回的 structured output 必须包含统一的批次元数据和文件引用，并继续触发最终文件展示所需的 file 事件

#### Scenario: Tool failure does not produce successful batch metadata
- **WHEN** `image_generation_tool` 执行失败
- **THEN** 系统不得伪造成功批次元数据，也不得把失败结果记为成功共享批次

### Requirement: Legacy workspace image generation records SHALL be removed from the active path
系统 MUST 停止使用 `ai_agent_image_generation_record` 及其相关实体、DAO、Mapper 作为工作台历史或结果持久化来源。

#### Scenario: Workspace history resolves from shared ledger only
- **WHEN** 工作台查询历史或生成完成后读取批次结果
- **THEN** 系统必须只访问共享 tool output / artifact 账本，而不能再访问遗留 record 表

#### Scenario: Legacy record artifacts are cleaned from the codebase
- **WHEN** 本次变更完成并进入回归验证
- **THEN** 与 `ai_agent_image_generation_record` 相关的实体、DAO、Mapper 和 schema 定义必须从主链路中删除
