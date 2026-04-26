## Why

当前 `ai_agent_message_event.payload_json` 会直接内联 `ppt/html/markdown` 等文件类产物的完整内容，导致事件表行数据膨胀、职责混杂，历史回放与上下文恢复链路也被迫处理大块正文。随着对话产物越来越多，需要把事件层收敛为“轻量引用”，并把对话生成文件沉淀到独立表中，形成稳定的路径式读取模型。

## What Changes

- 调整 `ai_agent_message_event.payload_json` 的持久化语义，只保留文件路径、资源引用和必要渲染元数据，不再直接保存文件类内容全文。
- 新增专门的对话产物表，统一保存对话过程中生成的 `ppt/html/markdown` 等各种文件的元数据、存储路径、类型与关联关系。
- 统一后端事件写入与历史读取链路，改为先写文件记录，再在 event 中落路径引用，由前端基于引用路径读取并渲染内容。
- 为历史兼容场景定义回退策略，保证旧事件数据或缺失引用时不会中断会话详情与回放能力。

## Capabilities

### New Capabilities
- `conversation-artifact-reference-storage`: 为对话事件提供基于路径引用的文件产物存储能力，支持 event 仅持有文件引用、独立文件表维护对话生成文件、前端按引用路径读取并渲染内容。

### Modified Capabilities

## Impact

- MySQL 表结构：新增对话文件产物表，并明确 `ai_agent_message_event.payload_json` 的轻量引用语义。
- 后端持久化链路：影响 `AgentMessageEvent` 相关 Entity、DAO、Mapper XML、事件写入服务与流式落库组装逻辑。
- 历史读取链路：影响对话详情回放、`artifactRefs` 归一化、会话记忆恢复与压缩阶段的文件引用解析。
- 前端渲染链路：影响会话详情中 `ppt/html/markdown` 等文件类事件的读取方式，需要按路径引用拉取内容或预览数据。
- 运维与数据演进：需要补充增量 DDL、历史数据兼容策略，以及文件类事件的可观测性与异常兜底。
