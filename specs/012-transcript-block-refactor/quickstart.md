# Quickstart: TranscriptBlock 会话记忆重写

## 1. 前置准备

- 确认当前分支为 `012-transcript-block-refactor`
- 准备一个可清理历史数据的开发库
- 本次切换不兼容旧表和旧数据，执行前先备份需要保留的开发数据

## 2. 推荐执行顺序

1. 完成后端重写
2. 完成历史详情接口与 UI 适配
3. 删除旧表、旧 Mapper、旧服务和旧 history-only UI 恢复逻辑
4. 跑测试与手工验收

## 3. 后端验证命令

```powershell
chcp 65001
mvn test -pl ai-agent-station-study-domain -am -DskipTests=false
mvn test -pl ai-agent-station-study-app -DskipTests=false
```

## 4. 前端验证命令

```powershell
chcp 65001
cd ui
npm run lint
npm run build
```

## 5. 启动应用

```powershell
chcp 65001
mvn -pl ai-agent-station-study-app spring-boot:run
```

## 6. 手工验收流程

### 场景 A: 单轮写入新事实链

1. 创建一条 `REACT` 或 `PLAN_SOLVE` 会话
2. 发送一条会触发工具调用和文件产出的消息
3. 验证数据库中出现：
   - 1 条 `ai_agent_turn`
   - 多条 `ai_agent_transcript_block`
   - 多条 `ai_agent_display_event`
4. 验证旧表 `ai_agent_message`、`ai_agent_message_event` 不再新增数据
5. 验证新库结构中只保留 `ai_agent_turn / ai_agent_transcript_block / ai_agent_display_event / ai_agent_session_memory`

### 场景 B: 同会话续聊

1. 在同一 `sessionId` 下继续提问“继续刚才任务”
2. 确认请求开始前执行了会话记忆准备
3. 确认续聊能够引用上一轮工具结果和产物引用

### 场景 C: 请求前压缩

1. 构造超过压缩阈值的长会话
2. 发起下一轮请求
3. 确认先生成新的 `ai_agent_session_memory` 版本，再执行当前请求
4. 确认读取的是最新有效 snapshot

### 场景 D: 压缩失败

1. 人为制造压缩失败
2. 发起下一轮请求
3. 确认当前请求继续执行
4. 确认没有写入半成品 snapshot，也没有更新错误边界

### 场景 E: 历史重开

1. 刷新页面或重新进入会话
2. 打开历史详情
3. 确认 UI 直接展示 turn + display events
4. 确认不再依赖旧的历史 payload 修补逻辑

## 7. 数据库抽查 SQL

```sql
SELECT id, request_id, sort_order, status
FROM ai_agent_turn
WHERE conversation_id = ?
ORDER BY sort_order;

SELECT turn_id, seq_no, block_type, role, tool_name
FROM ai_agent_transcript_block
WHERE turn_id = ?
ORDER BY seq_no;

SELECT turn_id, seq_no, display_type, title, status
FROM ai_agent_display_event
WHERE turn_id = ?
ORDER BY seq_no;

SELECT id, session_id, boundary_sort_order, source_turn_count
FROM ai_agent_session_memory
WHERE session_id = ?
ORDER BY id DESC;
```

## 8. 旧链路删除抽查

实现完成后，以下搜索结果应只剩测试、文档或迁移说明，不应再出现在主运行链路：

```powershell
chcp 65001
rg "AgentMessageEvent|ConversationReplayAssembler|ConversationEventPayloadNormalizer|SessionTranscriptBlockAssembler|SessionWorkingMemoryAssembler|ai_agent_message" ai-agent-station-study-domain ai-agent-station-study-trigger ui ai-agent-station-study-app/src/main
```
