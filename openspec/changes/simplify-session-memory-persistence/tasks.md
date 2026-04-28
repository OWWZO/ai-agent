## 1. Schema And Model Cleanup

- [x] 1.1 更新 `ai-agent-station-study-app/src/main/resources/db/schema.sql` 与新增迁移脚本，删除 `ai_agent_session_memory.facts_json`、`ai_agent_session_memory.boundary_message_id`
- [x] 1.2 更新 `AgentSessionMemory` 实体、DAO、Mapper XML 与相关查询写入映射，移除已删除字段
- [x] 1.3 更新 `SessionTurnMemory` 与压缩结果模型，删除旧版扁平 transcript 字段和 `factsJson`

## 2. Session Memory Simplification

- [x] 2.1 重构 `SessionWorkingMemoryAssembler`，删除 `buildRecentTurns` 中的 transcript fallback 和 `parseFacts`
- [x] 2.2 重构 `SessionMemoryCompactionService`，删除 `toTurnMemories` fallback、`parseFacts` 及相关兼容分支
- [x] 2.3 校准 `AgentSessionMemoryServiceImpl` 的压缩结果写入与熔断衔接，确保只消费新的 snapshot 字段和 blocks-only turn 数据

## 3. Stream Persistence Decomposition

- [x] 3.1 新增 `StreamExecutor`，承接 HTTP 请求构建、SSE 流读取与逐行回调分发
- [x] 3.2 新增 `EventProjector`，集中承接 `AgentResponse -> OrderedEvent` 的事件投影逻辑并移除 legacy payload 兼容解析
- [x] 3.3 新增 `PersistCoordinator`，统一处理消息、事件、会话状态与 `generated_files_json` 的最终落库
- [x] 3.4 将旧的 `AgentStreamPersistServiceImpl` 重构并改名为 `AgentStreamPersistCoordinator`，只保留会话守卫、working memory 注入、前端转发编排和子组件协同

## 4. Read-Side Cleanup

- [x] 4.1 重构 `ConversationEventPayloadNormalizer`，删除多层 `resultMap`、legacy `fileInfo` / `fileList` 和其他旧结构兼容逻辑
- [x] 4.2 重构 `SessionArtifactRestoreSupport`，删除字段别名兼容并统一使用标准文件字段名
- [x] 4.3 重构 `SessionTranscriptBlockAssembler`，仅基于标准化事件和 `artifactRefs` 构建 transcript blocks，不再使用 `generated_files_json` 兜底
- [x] 4.4 清理历史回放与续聊恢复链路中的剩余 legacy transcript / facts / payload fallback 引用，确保读取端只依赖新的事实模型

## 5. Verification

- [x] 5.1 补齐会话记忆装配、压缩和恢复相关单元测试，覆盖 blocks-only turn、无 `facts_json` 快照和边界按 `boundary_sort_order` 恢复
- [x] 5.2 补齐流式持久化拆分相关测试，分别验证 `StreamExecutor`、`EventProjector`、`PersistCoordinator` 与总协调服务的职责边界
- [x] 5.3 执行受影响模块的后端定向回归测试，确认历史回放、续聊恢复、事件落库与生成文件缓存行为保持预期
