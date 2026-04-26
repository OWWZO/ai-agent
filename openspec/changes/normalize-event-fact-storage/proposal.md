## Why

当前对话历史持久化混入了过多面向前端回放的字段，`ai_agent_message_event.payload_json` 同时承担了事实存储、展示补丁和回放上下文三种职责，导致数据冗余、语义不清，也让后端难以把 MySQL 作为稳定的会话事实源。现在需要把持久化模型收敛为“后端事实优先”，让数据库只保存后端真实产出的消息、事件、文件引用和必要渲染元数据，前端历史回显则复用实时渲染链路，而不是依赖一套额外的前端回放快照。

## What Changes

- 重新定义 `ai_agent_message` 与 `ai_agent_message_event` 的持久化边界，使 `ai_agent_message.response` 保留最终回答真相源，事件表只保存后端事件事实与最小必要扩展字段。
- 将可标准化的事件语义提升为独立列，包括事件类型、子类型、工具标识、资源引用、文件引用与必要状态字段，减少 `payload_json` 对主语义的承载。
- 收敛 `payload_json` 为扩展字段容器，仅保留无法稳定列化、但历史回显必须恢复的差异数据；理想情况下大部分事件记录的 `payload_json` 允许为空。
- 统一事件子类型语义，使用 `event_sub_type` 表达 `assistant_thought`、`plan_snapshot`、`tool_use`、`tool_result`、`artifact_reference` 等事件的细分语义，移除 `thoughtType`、`snapshotType` 等嵌套 subtype 冗余。
- **BREAKING**：废弃当前面向前端回放的旧事件 payload 结构，不再保证旧历史数据兼容；历史清理后仅支持新事实模型写入与读取。

## Capabilities

### New Capabilities
- `event-fact-storage`: 定义基于后端事实的消息与事件持久化模型，约束列化字段、最小 payload 规则、生成文件引用归档与历史回显读取契约。

### Modified Capabilities

## Impact

- 影响后端会话历史持久化与读取链路，包括 `ai_agent_message`、`ai_agent_message_event` 的表结构、DAO、Mapper XML、领域装配与事件写入逻辑。
- 影响历史回显装配逻辑，需要从数据库事实模型重建与实时链路一致的前端渲染数据，而不是依赖旧的前端回放快照。
- 影响消息事件协议与生成文件归档方式，需要明确哪些字段入列、哪些字段进入 `payload_json`，并同步调整相关测试与示例数据。
