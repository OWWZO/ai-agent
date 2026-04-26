## 1. 表结构与模型调整

- [x] 1.1 在 `ai-agent-station-study-app/src/main/resources/db/schema.sql` 为 `ai_agent_message` 新增 `generated_files_json` 字段，并更新相关注释，明确 `files_json` 仅表示上传文件
- [x] 1.2 调整 `AgentMessage`、`IAgentMessageDao`、`ai_agent_message_mapper.xml` 的字段映射与更新语句，支持读写 `generatedFilesJson`
- [x] 1.3 调整历史详情相关模型与响应对象，包括 `ConversationTurnDetail`、`ConversationTurnRespVO` 以及相关前端类型，显式拆分 `files` 与 `generatedFiles`

## 2. 后端写入链路改造

- [x] 2.1 扩展 `IAgentMessageService` 与 `AgentMessageServiceImpl` 的完成、异常、强停写入接口，使其在单轮结束时同步持久化 `generatedFilesJson`
- [x] 2.2 在 `AgentStreamPersistServiceImpl.persistTurnAndEvents(...)` 中基于 `finalOrderedEvents` 汇总生成文件，复用现有文件结构完成去重后写回 `ai_agent_message.generated_files_json`
- [x] 2.3 收敛文件类事件的 `payload_json` 组装逻辑，调整 `ConversationEventPayloadNormalizer`、`AgentMessageEventServiceImpl` 或相关事件投影路径，只保留路径引用、资源标识和必要渲染元数据

## 3. 读取链路与前端联动

- [x] 3.1 调整 `ConversationReplayAssembler`、`AgentConversationController` 及相关 VO 组装逻辑，让历史详情直接返回上传文件与生成文件两个独立字段
- [x] 3.2 调整 `SessionArtifactRestoreSupport`、会话记忆恢复与 transcript 相关聚合逻辑，按新模型聚合 `files_json`、`generated_files_json` 与当前事件 `artifactRefs`
- [x] 3.3 调整 `ui/src/services/agentConversation.ts`、相关 TS 类型和历史展示组件，接入 turn 级 `generatedFiles`，同时保持 event 时间线继续按 `artifactRefs` 做文件渲染

## 4. 上线准备与验证

- [x] 4.1 制定并落地旧历史数据清理步骤，覆盖旧会话、消息、事件以及依赖旧文件聚合结构的会话记忆数据
- [x] 4.2 为消息级生成文件索引、文件类 event payload 精简、历史详情字段拆分补充后端测试或 fixture
- [x] 4.3 运行后端与前端相关构建/测试，并用至少一轮包含 `html`、`markdown`、`ppt` 或 `code` 产物的对话验证：消息级生成文件可查询、event 引用可渲染、上传文件语义未被破坏
