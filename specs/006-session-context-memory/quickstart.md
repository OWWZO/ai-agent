# Quickstart: ReAct / PlanSolve 完整链路会话上下文复原验收

## 目标

本 Quickstart 用于本期实现完成后的验收，重点验证：

- 同一 `sessionId` 的续聊能恢复完整工具链，而不是只记得最终回答
- 历史重开后仍能恢复思考、工具结果、稳定文件引用
- 超长工具输出只保留关键结果与稳定引用，不整段回灌
- 已有摘要快照边界继续生效，但边界之后的 rich transcript 不再退化
- `mode_conflict`、`session_busy`、`ERROR / FORCE_STOPPED` 守卫不回归

## 明确不验收的范围

- 不验收新的上下文压缩策略
- 不验收新的 `ai_agent_session_memory` schema 或摘要格式
- 不验收 `CHAT` 模式续聊改造

## 当前实现落点（目标）

- working memory rebuild：`AgentSessionMemoryServiceImpl.rebuildWorkingMemory`
- transcript 装配：`SessionWorkingMemoryAssembler`
- event payload 规范化：`ConversationEventPayloadNormalizer`
- richer message 预装：`AgentStreamPersistServiceImpl.buildWorkingMemoryMessages`
- Agent 注入：`RootNode.convertMessages`、`Step1SopRecallAndPrepareNode.convertMessages`
- 历史详情回放：`ConversationReplayAssembler`

## 1. 启动服务

```powershell
cd D:\Java Code\ai-agent\ai-agent-station-study
mvn spring-boot:run -pl ai-agent-station-study-app
```

固定一个设备头，例如：`X-Device-Id: dev-memory-001`

## 2. 验证同会话工具链续聊

### REACT 样本

第一轮：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-memory-001" ^
  -d "{\"sessionId\":\"sess-react-ledger-001\",\"requestId\":\"req-react-ledger-001\",\"query\":\"先搜索 2025 年 Spring AI 与 MCP 的最新变化，整理重点并输出 html 报告\",\"deepThink\":0,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8100/api/agent/message/send-stream
```

第二轮：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-memory-001" ^
  -d "{\"sessionId\":\"sess-react-ledger-001\",\"requestId\":\"req-react-ledger-002\",\"query\":\"继续基于刚才的搜索结果，只保留和 MCP 工具接入最相关的三点差异\",\"deepThink\":0,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8100/api/agent/message/send-stream
```

### PLAN_SOLVE 对照样本

第一轮：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-memory-001" ^
  -d "{\"sessionId\":\"sess-plan-ledger-001\",\"requestId\":\"req-plan-ledger-001\",\"query\":\"先拆解一套 AI Agent 接入 MCP 的执行计划，并补充必要的研究步骤\",\"deepThink\":1,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8100/api/agent/message/send-stream
```

第二轮：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-memory-001" ^
  -d "{\"sessionId\":\"sess-plan-ledger-001\",\"requestId\":\"req-plan-ledger-002\",\"query\":\"继续沿用刚才计划，把已经搜索过的结论抽成验证清单，不要重新开始\",\"deepThink\":1,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8100/api/agent/message/send-stream
```

**预期**

- 第二轮明确体现“沿用上一次结果”，而不是重新从零开始
- 后端日志能看到 working memory rebuild
- 从数据库查看第二轮前的 `ai_agent_message_event` 时，相关 tool/result 事件已被读取并进入工作上下文

## 3. 验证历史重开后继续引用上一轮工具结果

先完成一个包含搜索、文件产物或 MCP 调用的历史会话，再重新进入：

```bash
curl -H "X-Device-Id: dev-memory-001" ^
  "http://127.0.0.1:8100/api/agent/conversation/detail?sessionId=sess-react-ledger-001"
```

随后继续追问：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-memory-001" ^
  -d "{\"sessionId\":\"sess-react-ledger-001\",\"requestId\":\"req-react-reopen-001\",\"query\":\"继续沿用上一轮生成的报告，把和 skilltool 相关的结论单独列出来\",\"deepThink\":0,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8100/api/agent/message/send-stream
```

**预期**

- 历史重开后不失忆
- 上一轮的工具结果、报告引用、稳定文件仍然可被感知
- 没有要求用户重复描述“上次搜过什么、读过什么文件”

## 4. 验证超长输出只保留引用

构造一轮会产生长报告或大输出的任务，例如：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-memory-001" ^
  -d "{\"sessionId\":\"sess-react-report-001\",\"requestId\":\"req-react-report-001\",\"query\":\"深度搜索 AI Agent 行业趋势并生成详细网页报告\",\"deepThink\":0,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8100/api/agent/message/send-stream
```

再继续追问：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-memory-001" ^
  -d "{\"sessionId\":\"sess-react-report-001\",\"requestId\":\"req-react-report-002\",\"query\":\"不要重复输出整份报告，只告诉我刚才报告里关于开源框架的关键结论\",\"deepThink\":0,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8100/api/agent/message/send-stream
```

**预期**

- 第二轮知道上一轮已经生成过报告
- 工作上下文中保留的是“报告存在 + 关键结果 + 稳定引用”，不是整段正文全文
- `sessionFiles` / `artifactRefs` 中仍能找到报告引用

## 5. 验证与既有摘要快照边界共存

为某个会话准备一个已有 snapshot 的样本，确认 `boundary_sort_order` 小于当前最新 turn：

```sql
SELECT session_id, boundary_sort_order, source_turn_count
FROM ai_agent_session_memory
WHERE session_id = 'sess-react-ledger-001';
```

继续对该会话发起新请求。

**预期**

- `boundary_sort_order` 之前的旧历史继续通过 `historyDialogue` 摘要体现
- `boundary_sort_order` 之后的已完成 turn 以 rich transcript 形式进入上下文
- 本期实现不会尝试把旧 snapshot 覆盖区间重新展开

## 6. 验证模式切换冲突

先创建一个 `REACT` 会话，再尝试以 `PLAN_SOLVE` 续聊：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-memory-001" ^
  -d "{\"sessionId\":\"sess-react-ledger-001\",\"requestId\":\"req-react-mode-switch\",\"query\":\"把上面的分析拆成执行计划\",\"deepThink\":1,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8100/api/agent/message/send-stream
```

**预期**

- 请求立即返回 `mode_conflict`
- 不新增新的占位 turn
- 不刷新 `ai_agent_session_memory`

## 7. 验证同会话并发冲突

保持一条流式请求未结束时，再次对同一 `sessionId` 发起请求。

**预期**

- 第二条请求返回 `session_busy`
- 不写入新的占位消息
- 不发起下游 `/AutoAgent` 调用

## 8. 验证 `ERROR / FORCE_STOPPED` 排除

对进行中的请求执行停止：

```bash
curl -X POST "http://127.0.0.1:8100/api/agent/message/stop?requestId=req-react-ledger-002"
```

随后继续同会话追问。

**预期**

- 失败/停止轮次仍能在历史详情中看到
- 但不会进入新的 working memory
- 后续续聊不会把该轮的半成品思考或工具中间态当成既成事实

## 9. 建议回归测试

```powershell
cd D:\Java Code\ai-agent\ai-agent-station-study
mvn test -pl ai-agent-station-study-app -DskipTests=false
```

重点关注：

- `SessionWorkingMemoryAssemblerTest`
- `SessionMemoryReopenResumeTest`
- `AgentStreamPersistServiceSessionGuardTest`
- `ConversationHistoryArtifactTest`
- 本期新增的 transcript block / richer preloaded messages 相关测试
