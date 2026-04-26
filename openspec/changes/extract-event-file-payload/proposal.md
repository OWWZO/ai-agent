## Why

当前 `ai_agent_message_event.payload_json` 会直接内联 `ppt/html/markdown` 等文件类产物的完整内容，导致事件表行数据膨胀、职责混杂，历史回放与上下文恢复链路也被迫处理大块正文。随着对话产物越来越多，需要把事件层收敛为“轻量引用”，并把每轮对话产生的文件稳定归档到 `ai_agent_message` 侧，形成可直接查询的文件索引模型。

## What Changes

- 调整 `ai_agent_message_event.payload_json` 的持久化语义，只保留文件路径、资源引用和必要渲染元数据，不再直接保存文件类内容全文。
- 保留 `ai_agent_message.files_json` 仅表示用户上传文件，并新增独立字段维护单次对话请求产生的文件列表，记录 `ppt/html/markdown` 等文件的路径、类型和必要展示信息，便于直接查询某轮对话生成了哪些文件。
- 统一后端事件写入与历史读取链路，改为先汇总本轮生成文件并写入 `ai_agent_message`，再在 event 中落路径引用，由前端基于引用路径读取并渲染内容。
- 旧历史数据不纳入兼容范围，上线前直接清理，由新数据模型重新开始承载对话文件索引与事件渲染。

## Capabilities

### New Capabilities
- `conversation-message-file-index`: 为对话消息提供独立的生成文件索引能力，保留 `files_json` 表示上传文件，新增消息字段维护本轮生成文件集合，event 仅持有路径引用，前端按引用路径读取并渲染内容。

### Modified Capabilities

## Impact

- MySQL 表结构：调整 `ai_agent_message_event.payload_json` 的轻量引用语义，并在 `ai_agent_message` 新增独立字段存储本轮生成文件索引，保留 `files_json` 的上传文件语义不变。
- 后端持久化链路：影响 `AgentMessage` / `AgentMessageEvent` 相关 Entity、DAO、Mapper XML、事件写入服务与流式落库组装逻辑。
- 历史读取链路：影响对话详情回放、`artifactRefs` 归一化、会话记忆恢复与压缩阶段的文件引用解析。
- 前端渲染链路：影响会话详情中 `ppt/html/markdown` 等文件类事件的读取方式，需要按路径引用拉取内容或预览数据。
- 运维与数据演进：需要补充增量 DDL、历史数据清理方案，以及文件类事件的可观测性与异常兜底。
