## Why

当前会话记忆与流式持久化链路同时背负了新旧两套数据表示和大量兼容分支，旧的 `AgentStreamPersistServiceImpl`、`SessionWorkingMemoryAssembler`、`SessionMemoryCompactionService`、`ConversationEventPayloadNormalizer` 等类混入了过多职责与 fallback 逻辑，导致代码难以维护、难以测试，也让会话记忆压缩边界和事件事实模型变得不再清晰。现阶段旧数据可以直接清理，因此需要一次性删除兼容层，把会话记忆、事件投影和落库链路收敛为单一且稳定的后端事实模型。

## What Changes

- 删除 `SessionTurnMemory` 中旧版扁平 transcript 字段，只保留 `blocks` 作为唯一上下文表示。
- 删除 `ai_agent_session_memory.facts_json`、`ai_agent_session_memory.boundary_message_id` 等已无业务价值的兼容字段，并同步收敛相关实体、DAO、Mapper 与压缩结果模型。
- 将旧的 `AgentStreamPersistServiceImpl` 拆分并重命名为 `AgentStreamPersistCoordinator`，同时引入流执行、事件投影、持久化协调三个职责单一的组件。
- 清理会话记忆装配、压缩、历史回放、事件标准化、产物恢复中的 legacy fallback、字段别名兼容和多层 `resultMap` 嵌套解析。
- 保留 `ai_agent_message.generated_files_json` 作为事件聚合后的只读缓存，但不再作为历史读取或 transcript 恢复的兜底来源。
- **BREAKING**：彻底移除旧会话记忆和旧事件 payload 兼容逻辑；发布后仅支持新的 blocks-only transcript 与标准字段结构，旧历史数据需要在上线前清理。

## Capabilities

### New Capabilities
- `session-memory-persistence`: 定义会话记忆压缩、上下文恢复、事件标准化与流式持久化的唯一事实模型，约束只保留 blocks transcript、标准字段和可测试的职责边界。

### Modified Capabilities

## Impact

- 影响后端主链路中的会话记忆压缩、working memory 装配、历史回放、事件投影与消息持久化逻辑。
- 影响 `ai_agent_session_memory` 与相关 Java 实体/DAO/Mapper/建表脚本，需要同步删除兼容字段并更新查询写入映射。
- 影响 `AgentStreamPersistCoordinator` 及其周边支持类的职责分配、单元测试组织方式和回归测试样例。
- 不改变 Agent 执行引擎主循环、不改变 SSE 流式协议、不改变前端交互格式；本次调整聚焦于后端内部模型简化与代码重构。
