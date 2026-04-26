## ADDED Requirements

### Requirement: Conversation turns SHALL persist generated files separately from uploaded files
系统必须为每轮对话单独持久化生成文件列表，并保持上传文件与生成文件语义分离：`files_json` 只表示用户上传文件，新增的消息级生成文件字段只表示该轮对话产出的文件。生成文件记录必须复用现有文件结构，至少包含稳定路径引用、文件类型、文件大小与资源标识。

#### Scenario: Turn contains both uploaded files and generated files
- **WHEN** 一轮对话既携带用户上传文件，又在执行过程中生成 `ppt/html/markdown/code` 等文件产物
- **THEN** 系统必须保留上传文件列表不变
- **THEN** 系统必须把本轮生成文件写入独立的消息级生成文件列表
- **THEN** 上传文件与生成文件在历史详情中不得混写到同一个字段

#### Scenario: Duplicate generated file references are indexed once per turn
- **WHEN** 同一轮对话的多个最终事件引用了同一个生成文件
- **THEN** 消息级生成文件列表中必须只保留一条对应文件记录
- **THEN** 去重后仍必须保留该文件可用的稳定路径引用与资源标识

### Requirement: File-producing events SHALL store reference-only render payloads
系统对文件类或产物类事件持久化 `payload_json` 时，必须只保存前端渲染所需的路径引用、资源标识和必要渲染元数据，不得再把文件全文或大块正文直接内联到 event 行中。

#### Scenario: File-producing final event is persisted
- **WHEN** 系统持久化 `html`、`markdown`、`ppt`、`code`、`file`、`browser` 或 `data_analysis` 类型的最终事件
- **THEN** `payload_json` 必须包含该事件用于渲染的引用信息和必要元数据
- **THEN** `payload_json` 不得直接保存对应文件的完整正文内容
- **THEN** `content_text` 必须保留可用于时间线展示的简短摘要、标题或说明

#### Scenario: Event replay renders from references instead of inline content
- **WHEN** 前端重新加载包含文件类事件的历史详情
- **THEN** 系统返回的 event payload 必须足以让前端基于路径引用渲染该事件
- **THEN** 前端不得依赖 event 行内联全文才能完成文件类事件展示

### Requirement: Conversation detail SHALL expose uploaded files and generated files as separate turn fields
系统在返回单轮对话详情时，必须同时暴露上传文件列表和生成文件列表两个独立字段，使调用方可以直接查询“本轮输入了什么文件”和“本轮产出了什么文件”，而无需扫描每个 event 的 payload。

#### Scenario: History detail returns separate file collections
- **WHEN** 客户端查询包含文件输入和文件产物的对话历史
- **THEN** 每个 turn 必须返回上传文件字段
- **THEN** 每个 turn 必须返回独立的生成文件字段
- **THEN** 调用方必须能够直接从 turn 数据读取本轮生成文件列表，而不需要遍历 event payload 提取

#### Scenario: Generated file list remains available even when event timeline is collapsed
- **WHEN** 客户端只查看某轮 turn 级概览而未展开事件时间线
- **THEN** 系统仍必须提供该轮完整的生成文件列表
- **THEN** 该能力不得依赖前端先解析 event 明细
