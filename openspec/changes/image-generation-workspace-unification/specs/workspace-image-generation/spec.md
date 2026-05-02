## ADDED Requirements

### Requirement: Workspace image generation SHALL use the shared execution pipeline
生图工作台的生成请求 SHALL 通过统一的生图执行内核完成请求归一化、上游调用和结果归一化，工作台服务本身 MUST NOT 再维护独立的生图调用实现。

#### Scenario: Workspace submits a text-to-image request
- **WHEN** 工作台提交仅包含 `prompt`、`mode`、`size`、`n` 的文生图请求
- **THEN** 系统必须通过共享执行内核调用上游生图接口，并返回统一归一化后的批次结果

#### Scenario: Workspace submits an image edit request
- **WHEN** 工作台提交包含参考图或遮罩图的图生图请求
- **THEN** 系统必须复用同一执行内核处理这些输入，而不是走与文生图不同的独立调用链路

### Requirement: Workspace image generation history SHALL be persisted and queried from the shared ledger
工作台生图成功结果 SHALL 写入共享的 `ai_agent_tool_output_image_generation` 批次账本和 `ai_agent_artifact` 图片明细账本；工作台历史 MUST 从该共享账本读取，而不是从遗留 record 表读取。

#### Scenario: Successful workspace generation creates a shared batch
- **WHEN** 工作台一次生成请求成功返回一张或多张图片
- **THEN** 系统必须写入一条 `request_source = 'workspace'` 的批次主记录，并为每张输出图片写入对应 artifact 明细

#### Scenario: Failed workspace generation does not create history
- **WHEN** 工作台生成请求失败，或上游没有返回可识别的图片文件结果
- **THEN** 系统不得写入成功批次历史，也不得在后续历史查询中展示该请求

### Requirement: Workspace history SHALL not depend on device-scoped headers
工作台历史查询 SHALL 直接按共享账本中的 `workspace` 批次返回结果，接口 MUST NOT 依赖 `X-Device-Id` 作为必填条件。

#### Scenario: History is queried without device header
- **WHEN** 客户端在未携带 `X-Device-Id` 的情况下请求工作台历史
- **THEN** 系统必须正常返回历史分页结果，而不是因为缺少设备头而拒绝请求

#### Scenario: History excludes non-workspace batches
- **WHEN** 共享账本中同时存在 `workspace` 和 `agent` 来源的生图批次
- **THEN** 工作台历史接口只能返回 `request_source = 'workspace'` 的批次，不能混入普通对话工具结果

### Requirement: Workspace responses SHALL remain compatible with current UI consumption
工作台生成响应与历史响应 SHALL 继续提供前端当前所需的请求标识、批次摘要和图片文件列表，避免此次后端收敛要求前端同步改造协议。

#### Scenario: Workspace generation returns normalized result payload
- **WHEN** 工作台生成请求成功
- **THEN** 返回结果必须包含可用于前端继续展示的 `requestId`、摘要信息和图片文件列表

#### Scenario: Workspace history returns batch-oriented payload
- **WHEN** 前端分页查询工作台历史
- **THEN** 返回结果必须保持按批次组织的列表结构，使当前工作台历史展示逻辑无需依赖新的字段协议
