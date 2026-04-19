# Quickstart: ReAct / PlanSolve 会话上下文记忆验收

## 目标

本 Quickstart 用于未来实现完成后的验收，重点验证：

- 同一 `sessionId` 连续追问是否记住上下文
- 长会话是否自动压缩为“摘要 + 最近窗口”
- 历史重开后是否恢复上下文和文件
- 同会话模式切换是否被拒绝
- 同会话并发续聊是否被拒绝
- `ERROR / FORCE_STOPPED` 是否被排除出后续记忆
- `REACT` 与 `PLAN_SOLVE` 是否都能通过上述三类核心场景

## 当前实现落点

- 工作记忆装配入口：`AgentSessionMemoryServiceImpl.rebuildWorkingMemory`
- 摘要压缩入口：`AgentSessionMemoryServiceImpl.refreshSessionMemory`
- 请求前注入入口：`AgentStreamPersistServiceImpl.sendAndPersist`
- Prompt 注入：`historyDialogue -> {{history_dialogue}}`
- 最近窗口预装：`AgentRequest.messages -> BaseAgent.memory.messages`
- 历史文件恢复：`AgentRequest.sessionFiles -> AgentContext.productFiles`

## 当前配置键

位于 `application-dev.yml` / `application-test.yml`：

- `reactor.session-memory-enabled`
- `reactor.session-memory-compaction-threshold-tokens`
- `reactor.session-memory-recent-window-turns`
- `reactor.session-memory-summary-max-length`

## 1. 启动依赖

```powershell
cd D:\Java Code\ai-agent\ai-agent-station-study
mvn spring-boot:run -pl ai-agent-station-study-app
```

准备一个固定设备头，例如 `X-Device-Id: dev-memory-001`。

## 2. 创建并验证同会话续聊

以下先以 `REACT` 为例，`PLAN_SOLVE` 需要再补 1 组结构相同的对照样本。

第一轮，创建一个 `REACT` 会话并给出明确约束：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-memory-001" ^
  -d "{\"sessionId\":\"sess-react-001\",\"requestId\":\"req-react-001\",\"query\":\"后续所有输出都用中文表格，先分析 2025 年 AI Agent 开发趋势\",\"deepThink\":0,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8080/api/agent/message/send-stream
```

第二轮，复用同一 `sessionId`，不再重复约束：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-memory-001" ^
  -d "{\"sessionId\":\"sess-react-001\",\"requestId\":\"req-react-002\",\"query\":\"继续补充开源框架对比，只保留最重要的三类\",\"deepThink\":0,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8080/api/agent/message/send-stream
```

`PLAN_SOLVE` 对照样本：

第一轮，创建一个 `PLAN_SOLVE` 会话并给出明确约束：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-memory-001" ^
  -d "{\"sessionId\":\"sess-plan-001\",\"requestId\":\"req-plan-001\",\"query\":\"后续所有输出都用中文表格，先把 AI Agent 落地项目拆成执行计划\",\"deepThink\":1,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8080/api/agent/message/send-stream
```

第二轮，复用同一 `sessionId`，不再重复约束：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-memory-001" ^
  -d "{\"sessionId\":\"sess-plan-001\",\"requestId\":\"req-plan-002\",\"query\":\"继续细化第 2 和第 3 步，只保留最关键的执行动作\",\"deepThink\":1,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8080/api/agent/message/send-stream
```

**预期**

- 第二轮仍然遵守“中文表格”约束
- 后端日志能看到 `重建会话工作记忆 sessionId=...`
- 数据库中第二轮不会退化成“完全新对话”

## 3. 验证长会话自动压缩

建议在测试环境把压缩阈值调低，连续产生多轮 `REACT` 或 `PLAN_SOLVE` 请求，直到触发压缩。两种模式至少各验证 1 组长会话样本。

建议补一组 `PLAN_SOLVE` 长会话样本，例如连续追问：

```text
sess-plan-compact-001
1. 先把项目拆成 5 个阶段
2. 展开第 1 阶段的任务
3. 展开第 2 阶段的风险
4. 继续细化第 3 阶段
5. 把前面结论改成中文表格
6. 再补充依赖关系与回滚策略
```

检查数据库：

```sql
SELECT session_id, boundary_sort_order, source_turn_count, update_time
FROM ai_agent_session_memory
WHERE session_id = 'sess-react-001';
```

```sql
SELECT request_id, sort_order, status
FROM ai_agent_message
WHERE conversation_id = (
  SELECT id FROM ai_agent_conversation WHERE session_id = 'sess-react-001'
)
ORDER BY sort_order;
```

**预期**

- `ai_agent_session_memory` 出现且仅出现一条记录
- `boundary_sort_order` 随压缩推进单调增加
- 后端日志能看到 `生成会话压缩快照 sessionId=...`
- 压缩后进入模型上下文的记忆载荷（以 `estimatedTokens` 或等价字符数估算）不高于未压缩全量历史的 40%
- 新一轮续聊时进入模型的记忆不再是全量历史

## 4. 验证历史重开续聊

先通过历史详情接口确认旧会话存在，再继续发送新问题：

```bash
curl -H "X-Device-Id: dev-memory-001" ^
  "http://127.0.0.1:8080/api/agent/conversation/detail?sessionId=sess-react-001"
```

然后继续发起同会话续聊请求。

`PLAN_SOLVE` 历史重开对照样本：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-memory-001" ^
  -d "{\"sessionId\":\"sess-plan-001\",\"requestId\":\"req-plan-reopen-001\",\"query\":\"继续沿用前面的执行计划，把验证阶段补充完整\",\"deepThink\":1,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8080/api/agent/message/send-stream
```

**预期**

- 后续续聊能继承摘要与最近窗口
- 如果历史里产生过稳定文件/报告，后续 prompt 和工具上下文能再次感知这些文件

## 5. 验证模式切换冲突

先用 `REACT` 创建会话，再尝试用同一 `sessionId` 发 `PLAN_SOLVE`：

```bash
curl -N ^
  -H "Content-Type: application/json" ^
  -H "X-Device-Id: dev-memory-001" ^
  -d "{\"sessionId\":\"sess-react-001\",\"requestId\":\"req-react-mode-switch\",\"query\":\"把上面的分析拆成执行计划\",\"deepThink\":1,\"outputStyle\":\"html\"}" ^
  http://127.0.0.1:8080/api/agent/message/send-stream
```

**预期**

- 请求被立即拒绝
- 不新增可记忆轮次
- 返回信息明确提示“请新建会话”

反向对照样本：先用 `PLAN_SOLVE` 创建 `sess-plan-001`，再用同一 `sessionId` 发 `REACT` 请求，也应被拒绝。

## 6. 验证同会话并发冲突

保持第一条流式请求不结束时，再次发起同 `sessionId` 请求。

建议同时补一组 `PLAN_SOLVE` 并发样本，确认计划链路也会返回 `session_busy`。

**预期**

- 第二条请求被拒绝
- 不写入新的占位消息，不更新摘要快照
- 返回信息明确提示“当前轮次仍在执行，请等待完成或先停止”

## 7. 验证 `ERROR / FORCE_STOPPED` 排除

制造一轮失败或手动停止：

```bash
curl -X POST "http://127.0.0.1:8080/api/agent/message/stop?requestId=req-react-002"
```

`PLAN_SOLVE` 对照样本：

```bash
curl -X POST "http://127.0.0.1:8080/api/agent/message/stop?requestId=req-plan-002"
```

再继续同会话追问。

**预期**

- 失败轮次仍能在历史详情里看到
- 但后续续聊不会把该轮中间态带入新的工作记忆
- `ai_agent_session_memory` 的边界与事实不会吸收这轮数据

## 8. 建议测试命令

实现完成后，建议至少跑以下回归：

```powershell
cd D:\Java Code\ai-agent\ai-agent-station-study
mvn test -pl ai-agent-station-study-app
```

重点关注：

- 现有历史回放相关测试是否仍然通过
- 新增的会话记忆重建、压缩、并发守卫、模式守卫测试是否通过
