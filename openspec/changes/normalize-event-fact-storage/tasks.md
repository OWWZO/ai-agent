## 1. Schema And Mapping

- [ ] 1.1 在 `ai-agent-station-study-app/src/main/resources/db/schema.sql` 为 `ai_agent_message_event` 增加 `tool_use_id`、`tool_name`、`tool_arguments_json`、`reference_only`、`artifact_refs_json`、`structured_data_json` 列，并保留 `payload_json`
- [ ] 1.2 更新 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentMessageEvent.java`，明确新列字段与 `payload_json` 的扩展职责
- [ ] 1.3 更新 `ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_event_mapper.xml` 与相关 DAO，完成新列的查询、批量写入和结果映射

## 2. Write-Side Persistence

- [ ] 2.1 重构 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistServiceImpl.java` 的事实事件构造逻辑，优先填充标准列与 `structured_data_json`
- [ ] 2.2 在 `AgentStreamPersistServiceImpl` 收口 canonical `event_sub_type` 映射，移除 `thoughtType`、`snapshotType`、`sourceType/sourceSubType` 等 payload 主语义写入
- [ ] 2.3 在 `AgentStreamPersistServiceImpl` 增加最小扩展 payload 白名单构造入口，确保大多数事件落库时 `payload_json` 为 `NULL`
- [ ] 2.4 重构 `buildGeneratedFilesJson(...)` 与相关文件归档逻辑，只从 `artifact_refs_json` 派生 `generated_files_json`

## 3. Read-Side Projection

- [ ] 3.1 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support` 提炼共享的事件事实读取/投影辅助能力，统一消费标准列、`structured_data_json` 与 `artifact_refs_json`
- [ ] 3.2 重构 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java`，改为从后端事实模型投影历史详情 payload
- [ ] 3.3 重构 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionTranscriptBlockAssembler.java`，改为从后端事实模型恢复 transcript blocks 和 artifact 引用

## 4. Cleanup And Integration

- [ ] 4.1 清理 `ConversationReplayAssembler`、`SessionTranscriptBlockAssembler`、`AgentStreamPersistServiceImpl` 中不再需要的旧 payload fallback、前端快照字段解析和重复 subtype 兼容分支
- [ ] 4.2 校准 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/history`、相关 service/VO 装配链路，确保外部历史回显结构保持不变但内部改为事实投影
- [ ] 4.3 回填受影响的实现注释、样例数据或必要契约说明，明确 `response`、`artifact_refs_json`、`structured_data_json` 与 `payload_json` 的最终职责

## 5. Verification

- [ ] 5.1 扩展与调整 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryPersistenceTest.java`，覆盖“事件主语义入列”“payload 最小化”“生成文件仅从 `artifact_refs_json` 派生”的断言
- [ ] 5.2 扩展与调整历史详情与会话记忆相关测试，至少覆盖 `ConversationHistoryDetailApiTest.java`、`SessionTranscriptBlockAssemblerTest.java`、`SessionMemoryReopenResumeTest.java`
- [ ] 5.3 执行本次改造相关的后端定向回归测试，并记录需要手工确认的历史回显与续聊验收结果
