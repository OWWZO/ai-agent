# Quickstart: 对齐 free-code 的请求前会话压缩验收

## 目标

本 Quickstart 用于本期实现完成后的验收，重点验证：

- 第二轮请求会在真正进入主执行前先做 compaction decision
- 结构化 session memory 采用 free-code 风格固定 sections
- `ai_agent_session_memory` 对同一 `session_id` 产生多版本快照，运行时只加载最新一条
- 压缩后仍保留最近原始窗口和工具链完整性
- compaction 失败时遵循“硬上限内降级继续，否则拒绝”

## 0. 验收前配置

为了更容易触发请求前 compaction，建议在本地 `application-dev.yml` 中临时调低阈值：

```yaml
autobots:
  autoagent:
    session-memory:
      enabled: true
      compaction-threshold-tokens: 1
      hard-limit-tokens: 8000
      recent-window-max-tokens: 2000
      recent-window-min-messages: 4
      max-consecutive-failures: 3
      summary-max-length: 4000
```

## 1. 启动服务

```powershell
cd D:\Java Code\ai-agent\ai-agent-station-study
mvn spring-boot:run -pl ai-agent-station-study-app
```

固定一个设备头，例如：`X-Device-Id: dev-compaction-001`

## 2. 创建足够长的第一轮历史

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-compaction-001" ^
  -d "{\"sessionId\":\"sess-freecode-compact-001\",\"requestId\":\"req-freecode-compact-001\",\"query\":\"先深度研究 2025 年 AI Agent 上下文压缩与 session memory 方案，要求调用搜索和文件结果生成详细 html 报告\",\"deepThink\":1,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8100/api/agent/message/send-stream
```

**预期**

- 第一轮正常完成，产生 `ai_agent_message` 和 `ai_agent_message_event`
- 此时不要求请求结束后立即生成新的 session memory snapshot

## 3. 第二轮请求触发 compaction preflight

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-compaction-001" ^
  -d "{\"sessionId\":\"sess-freecode-compact-001\",\"requestId\":\"req-freecode-compact-002\",\"query\":\"继续基于刚才的研究，只保留和请求前压缩最相关的实现差异，不要重新开始\",\"deepThink\":1,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8100/api/agent/message/send-stream
```

**预期**

- 后端日志显示本轮在主执行前完成了 compaction decision
- compaction 成功时，`summary_text` 中出现 free-code 风格 section 标题，而不是旧版流水账摘要
- 本轮请求继续成功执行，不要求用户重复描述上一轮结果

## 4. 验证多版本 snapshot 追加写入

```sql
SELECT id, session_id, boundary_sort_order, source_turn_count, last_compacted_at
FROM ai_agent_session_memory
WHERE session_id = 'sess-freecode-compact-001'
ORDER BY id DESC;
```

**预期**

- 同一 `session_id` 至少存在多条记录
- 最新记录的 `id` 最大
- 旧记录仍然保留，未被覆盖

## 5. 验证运行时只读取最新 snapshot

再次发起第三轮请求：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-compaction-001" ^
  -d "{\"sessionId\":\"sess-freecode-compact-001\",\"requestId\":\"req-freecode-compact-003\",\"query\":\"继续沿用上一轮压缩后的记忆，告诉我最近窗口里保留了哪些关键工具链\",\"deepThink\":1,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8100/api/agent/message/send-stream
```

**预期**

- 运行时使用最新 snapshot + 最近原始窗口
- 不会回退去读取旧快照版本作为主记忆
- 最近窗口中的工具调用/结果顺序仍然正确

## 6. 验证重开会话后的恢复行为

```bash
curl -H "X-Device-Id: dev-compaction-001" ^
  "http://127.0.0.1:8100/api/agent/conversation/detail?sessionId=sess-freecode-compact-001"
```

随后继续追问：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-compaction-001" ^
  -d "{\"sessionId\":\"sess-freecode-compact-001\",\"requestId\":\"req-freecode-compact-reopen-001\",\"query\":\"继续沿用最新会话记忆，把关键错误修正列出来\",\"deepThink\":1,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8100/api/agent/message/send-stream
```

**预期**

- 重开后仍使用最新 snapshot version
- `Current State`、`Errors & Corrections` 等 section 已被正确继承
- 不需要重新从旧 turn 全量重建全部上下文

## 7. 验证最近窗口以 token 预算保留，而不是固定 N 轮

构造一轮包含超长输出或长报告的请求，再继续追问：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-compaction-001" ^
  -d "{\"sessionId\":\"sess-freecode-window-001\",\"requestId\":\"req-freecode-window-001\",\"query\":\"生成一份很长的 AI Agent 调研报告，并附带多段命令输出和文件结果\",\"deepThink\":1,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8100/api/agent/message/send-stream
```

第二轮：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-compaction-001" ^
  -d "{\"sessionId\":\"sess-freecode-window-001\",\"requestId\":\"req-freecode-window-002\",\"query\":\"不要重复整个报告，只告诉我最近一次工具调用链的关键结果\",\"deepThink\":1,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8100/api/agent/message/send-stream
```

**预期**

- 最近窗口不是机械固定 N 轮，而是按 token 预算裁剪
- 超长正文被压到关键结果 + 稳定引用
- `tool_use / tool_result` 没有被拆断

## 8. 失败回退建议用自动化测试验证

手工很难稳定制造“压缩失败但上下文仍在硬上限内”与“压缩失败且仍超限”两条路径，建议用自动化测试覆盖：

- 模型调用失败时的 `DEGRADED_CONTINUE`
- 模型调用失败且超出硬上限时的 `REJECTED`
- 连续失败后的 circuit open
- 拒绝路径下不插入占位消息

## 9. 建议回归测试

```powershell
cd D:\Java Code\ai-agent\ai-agent-station-study
mvn test -pl ai-agent-station-study-app -DskipTests=false
```

重点关注：

- `ConversationHistoryPersistenceTest`
- `SessionMemoryCompactionServiceTest`
- `SessionWorkingMemoryAssemblerTest`
- `SessionMemoryReopenResumeTest`
- `AgentStreamPersistServiceSessionGuardTest`
- 本期新增的 request-entry compaction / snapshot versioning / circuit breaker 相关测试
