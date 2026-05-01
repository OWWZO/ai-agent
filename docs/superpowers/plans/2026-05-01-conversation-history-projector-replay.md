# Conversation History Projector Replay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不新增独立展示事件表的前提下，补强现有执行事实账本，使同一套 `ReplayProjector` 同时支撑实时对话投影和 MySQL 历史恢复，并恢复前端退出后可重进查看完整会话细节的能力。

**Architecture:** `ai_agent_dialogue_run / ai_agent_llm_invocation / ai_agent_tool_invocation / ai_agent_artifact` 继续作为唯一事实源，所有“能稳定重算”的展示顺序和分组字段都不落库。后端只补齐不能稳定推断的最小语义，例如 run 的会话归属元信息、LLM 的 `semantic_kind`、工具最终结构化 `output_json`，并明确 `llmObservation` 只服务主智能体推理、`output_json` 只服务工具事实持久化。历史恢复阶段统一由 `ReplayProjector + ToolInvocationProjectorRegistry` 按 `tool_name + input_json + output_json + artifact` 生成前端现有消费的 `eventData` 结构；实时路径继续挂到当前 `/AutoAgent` 主 SSE 响应链，不再把前端事件字段直接写回 `output_json`；历史路径新增 `HistoryReplayPrinter` 和会话查询接口复用同一投影结果。

**Tech Stack:** Java 17, Spring Boot 3.4.3, MyBatis / Mapper XML, MySQL 8, React 19, TypeScript 5, Vitest

---

## 主路径校验结论

本计划的前置假设已验证为“**ExecutionLedger 写侧已经接入当前主 SSE 运行链路**”，因此 Task 1-4 可以直接挂到现有运行时，不需要先补一层新的执行入口。

### 已确认的真实链路

1. 当前主 SSE 入口是 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/AiAgentController.java` 的 `/AutoAgent`，请求会进入 `AgentDispatchDispatchService`，再路由到 `reactAgentExecuteStrategy` 或 `planSolveAgentExecuteStrategy`。
2. `run` 不是在 `BaseAgent.run()` 内创建，而是在 React 的 `RootNode` 和 PlanSolve 的 `Step1SopRecallAndPrepareNode` 中通过 `ExecutionLedgerRunSupport.initializeRun(...)` 创建。
3. `llm_invocation` 已接到真实运行链路，`LLM.ask(...)` / `LLM.askTool(...)` 内部会调用 `createLlmInvocation(...)` 与 `finishLlmInvocation(...)`。
4. `tool_invocation` 与 `artifact` 已接到真实运行链路，`BaseAgent.executeTools(...)` 会先 `createToolInvocations(...)`，再 `finishToolInvocation(...)`，最后 `recordArtifacts(...)`。
5. React 成功态会在 `SummaryResultNode` 结束 run，PlanSolve 成功态会在 `Step2PlanExecuteNode` 结束 run；异常态由 `ReactAgentExecuteStrategy / PlanSolveAgentExecuteStrategy` catch 后统一 `finishRun(...)`。

### 对实现方案的直接影响

1. Task 1 的字段补强应直接落在 `ExecutionLedgerRunSupport / LLM / BaseAgent` 这条真实写链上，不要改旧 `HandlerImpl` 试图“补挂”。
2. Task 3 的实时投影接入点应以当前 `/AutoAgent` 主 SSE 响应链为准；如果 `BaseAgentResponseHandler` 仍是外层 `eventData` 聚合核心，则让它委托 `ReplayProjector`，否则优先挂到 `SSEPrinter -> 响应聚合器` 边界。
3. 当前真正未闭环的是**读侧**，也就是 `ExecutionLedgerQueryService` 虽然已经存在，但尚未形成生产可用的历史回放查询出口；Task 4 负责补齐这一层。
4. `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/controller/ReactorController.java` 仍保留旧入口壳子，但 dispatch 代码未接通，本计划不以它为实现依据。

---

## 文件结构映射

### 新建文件

| 文件路径 | 职责 |
| --- | --- |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/LlmSemanticKind.java` | 明确区分 `TOOL_THOUGHT / PLAN_THOUGHT / FINAL_ANSWER / OTHER` |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ToolOutputJsonBuilder.java` | 把工具最终态统一收口成 tool-native `output_json` |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/replay/ReplayFactBundle.java` | 运行事实聚合对象 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/replay/ProjectedReplayEvent.java` | Projector 输出模型，直接贴合前端 `eventData` |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java` | 共享投影器 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/ToolInvocationProjector.java` | 单工具 projector 契约 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/ToolInvocationProjectorRegistry.java` | 按 `tool_name` 分发工具解析器 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/HistoryReplayPrinter.java` | 历史回放输出器，输出和实时一致的 payload |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ConversationHistoryReplayService.java` | 从 MySQL 事实重建单轮 / 单会话回放 |
| `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationHistoryController.java` | 会话列表和历史详情接口 |
| `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/resp/ConversationSessionRespVO.java` | 会话列表返回对象 |
| `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/resp/ConversationHistoryDetailRespVO.java` | 单会话历史详情返回对象 |
| `ui/src/utils/conversationHistory.ts` | 把历史接口返回的 replay frames 还原为前端 `ConversationHistory` |
| `ui/src/utils/conversationHistory.test.ts` | 前端历史恢复单测 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorTest.java` | 共享 Projector 回归测试 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java` | 历史接口测试 |

### 修改文件

| 文件路径 | 修改内容 |
| --- | --- |
| `ai-agent-station-study-app/src/main/resources/db/schema.sql` | 为现有事实表补最小必要字段，不新增 `event` 表 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueRun.java` | 增加会话归属与前端恢复所需元信息 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/LlmInvocation.java` | 增加 `semanticKind` |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/DialogueRunStartRecord.java` | 透传 run 元信息 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/DialogueRunView.java` | 暴露会话列表所需字段 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/LlmInvocationStartRecord.java` | 透传 `semanticKind` |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/LlmInvocationView.java` | 暴露 `semanticKind` |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerRunSupport.java` | run 创建时写入 owner / outputStyle / role 信息 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/llm/LLM.java` | 在创建 LLM invocation 时显式写入 `semanticKind` |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java` | 工具完成时统一构建 `llmObservation + output_json`，禁止把前端事件字段落库 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/FileTool.java` | `get/upload` 都补齐结构化 output |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/CodeInterpreterTool.java` | 落可回放代码输出与产物引用 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ReportTool.java` | 落 HTML / Markdown / 文件结构化结果 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DataAnalysisTool.java` | 落 `data_analysis` 结构化结果 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DeepSearchTool.java` | 保持现有 JSON，并补统一 schemaVersion |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/MultiModalAgent.java` | 最终 markdown 统一落结构化结果 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ImageGenerationTool.java` | 落图片文件结构化结果 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/BaseAgentResponseHandler.java` | 改为委托共享 `ReplayProjector` |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerQueryService.java` | 增加按 owner / session 查询历史能力 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java` | 组装历史回放事实 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueRunLedgerDao.java` | 增加 owner/session 查询 |
| `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml` | 补 run 查询 SQL |
| `ai-agent-station-study-app/src/main/resources/mybatis/mapper/llm_invocation_ledger_mapper.xml` | 补 `semantic_kind` 字段映射 |
| `ui/src/services/agentConversation.ts` | 新增历史会话接口 |
| `ui/src/types/chat.ts` | 补历史接口消费字段 |
| `ui/src/pages/Home/index.tsx` | 恢复会话列表与历史详情加载 |

---

## Task 1: 补齐事实账本的最小必要字段

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/LlmSemanticKind.java`
- Modify: `ai-agent-station-study-app/src/main/resources/db/schema.sql`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueRun.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/LlmInvocation.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/DialogueRunStartRecord.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/DialogueRunView.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/LlmInvocationStartRecord.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/LlmInvocationView.java`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/llm_invocation_ledger_mapper.xml`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerRunSupport.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/llm/LLM.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java`

- [ ] **Step 1: 先写失败测试，锁定“不可猜语义必须落库”**

```java
@Test
public void shouldPersistRunOwnerMetaAndLlmSemanticKind() {
    ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
    Long runId = ctx.recorder.createRun(DialogueRunStartRecord.builder()
            .runUid("req-history-001")
            .requestId("req-history-001")
            .sessionId("session-history-001")
            .ownerErp("zhangsan")
            .outputStyle("chat")
            .deepThink(false)
            .roleAgentId("agent-role-001")
            .roleAgentName("行业研究员")
            .entryAgent(ExecutionLedgerConstants.ENTRY_AGENT_REACT)
            .queryText("本周项目风险")
            .build());

    Long llmId = ctx.recorder.createLlmInvocation(LlmInvocationStartRecord.builder()
            .runId(runId)
            .requestId("req-history-001")
            .invocationSeq(1)
            .agentName("react")
            .stepNo(1)
            .callKind(ExecutionLedgerConstants.CALL_KIND_ASK)
            .semanticKind(LlmSemanticKind.FINAL_ANSWER.name())
            .streaming(false)
            .modelName("test-model")
            .build());

    Assert.assertNotNull(llmId);
    Assert.assertEquals("zhangsan", ctx.store.runs.get(runId).getOwnerErp());
    Assert.assertEquals("chat", ctx.store.runs.get(runId).getOutputStyle());
    Assert.assertEquals("FINAL_ANSWER", ctx.store.llmInvocations.get(llmId).getSemanticKind());
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ExecutionLedgerQueryServiceTest
```

Expected: 编译失败或断言失败，提示 `ownerErp / outputStyle / semanticKind` 字段不存在。

- [ ] **Step 3: 修改表结构与实体，只补最小必需字段**

```sql
ALTER TABLE ai_agent_dialogue_run
    ADD COLUMN owner_erp VARCHAR(64) NULL COMMENT '会话归属人',
    ADD COLUMN output_style VARCHAR(32) NULL COMMENT 'chat/html/docs/table/dataAgent',
    ADD COLUMN deep_think TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否深度模式',
    ADD COLUMN role_agent_id VARCHAR(64) NULL COMMENT '聊天模式选中的角色ID',
    ADD COLUMN role_agent_name VARCHAR(128) NULL COMMENT '聊天模式选中的角色名称';

ALTER TABLE ai_agent_llm_invocation
    ADD COLUMN semantic_kind VARCHAR(32) NOT NULL DEFAULT 'OTHER' COMMENT 'TOOL_THOUGHT/PLAN_THOUGHT/FINAL_ANSWER/OTHER';
```

```java
public enum LlmSemanticKind {
    TOOL_THOUGHT,
    PLAN_THOUGHT,
    FINAL_ANSWER,
    OTHER
}
```

- [ ] **Step 4: 在 run / llm 写入链路透传这些字段**

```java
Long runId = recorder.createRun(DialogueRunStartRecord.builder()
        .runUid(request.getRequestId())
        .requestId(request.getRequestId())
        .sessionId(request.getSessionId())
        .ownerErp(request.getErp())
        .outputStyle(request.getOutputStyle())
        .deepThink(Boolean.TRUE.equals(agentContext.getDeepThink()))
        .roleAgentId(request.getAiAgentId())
        .roleAgentName(agentContext.getRoleName())
        .entryAgent(entryAgent)
        .queryText(request.getQuery())
        .build());
```

```java
Long invocationId = context.getExecutionRecorder().createLlmInvocation(LlmInvocationStartRecord.builder()
        .runId(context.getAgentRunState().getRunId())
        .requestId(context.getRequestId())
        .invocationSeq(invocationSeq)
        .agentName(context.getAgentRunState().getCurrentAgentName())
        .stepNo(context.getAgentRunState().getCurrentStepNo())
        .callKind(callKind)
        .semanticKind(semanticKind.name())
        .streaming(stream)
        .modelName(model)
        .startedAt(startedAt)
        .build());
```

- [ ] **Step 5: 重新运行聚焦测试并提交**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ExecutionLedgerQueryServiceTest
```

Expected: PASS

```bash
git add ai-agent-station-study-app/src/main/resources/db/schema.sql
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/LlmSemanticKind.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueRun.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/LlmInvocation.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/DialogueRunStartRecord.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/DialogueRunView.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/LlmInvocationStartRecord.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/LlmInvocationView.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerRunSupport.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/llm/LLM.java
git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml
git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/llm_invocation_ledger_mapper.xml
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java
git commit -m "feat: persist replay-critical run and llm semantics"
```

---

## Task 2: 让每个重点工具都落可回放的结构化最终态

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ToolReplayPayloadBuilder.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/FileTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/CodeInterpreterTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ReportTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DataAnalysisTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DeepSearchTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/MultiModalAgent.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ImageGenerationTool.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java`

- [ ] **Step 1: 先写失败测试，锁定 `file_tool(get)` 和富展示工具的落库缺口**

```java
@Test
public void shouldPersistStructuredOutputForReplayableTools() {
    ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
    ToolInvocation invocation = ctx.store.toolInvocations.values().stream()
            .filter(item -> "file_tool".equals(item.getToolName()))
            .findFirst()
            .orElseThrow();

    Assert.assertNotNull(invocation.getOutputJson());
    Assert.assertTrue(invocation.getOutputJson().contains("\"renderType\":\"file\""));
    Assert.assertTrue(invocation.getOutputJson().contains("\"command\":\"get\""));
}
```

- [ ] **Step 2: 运行集成测试，确认当前 `output_json` 不完整**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest
```

Expected: FAIL，`file_tool/get` 或 `report_tool` 的 `output_json` 为空或缺少文件引用。

- [ ] **Step 3: 新建统一 builder，禁止工具各自手搓前端 JSON**

```java
public final class ToolReplayPayloadBuilder {

    public static String buildFilePayload(String command, List<FileInfo> fileInfo, String contentPreview) {
        return JSON.toJSONString(Map.of(
                "schemaVersion", 1,
                "tool", "file_tool",
                "renderType", "file",
                "payload", Map.of(
                        "command", command,
                        "fileInfo", fileInfo,
                        "contentPreview", StringUtils.defaultString(contentPreview)
                )
        ));
    }

    public static String buildMarkdownPayload(String tool, String markdown) {
        return JSON.toJSONString(Map.of(
                "schemaVersion", 1,
                "tool", tool,
                "renderType", "markdown",
                "payload", Map.of("data", StringUtils.defaultString(markdown))
        ));
    }
}
```

- [ ] **Step 4: 在工具完成时统一回填 `output_json`，不要保存运行时顺序字段**

```java
String outputJson = ToolReplayPayloadBuilder.buildFilePayload(
        "get",
        List.of(FileInfo.builder()
                .fileName(fileRequest.getFileName())
                .downloadUrl(fileResponse.getOssUrl())
                .previewUrl(fileResponse.getDomainUrl())
                .fileSize(fileResponse.getFileSize())
                .build()),
        fileContent
);
return ToolExecutionOutcome.success(resultText, resultText, outputJson);
```

```java
private boolean isStructuredToolOutput(String toolName, String outputJson) {
    return StringUtils.isNotBlank(outputJson);
}
```

- [ ] **Step 5: 重新跑工具账本测试并提交**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest
```

Expected: PASS

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ToolReplayPayloadBuilder.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/FileTool.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/CodeInterpreterTool.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ReportTool.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DataAnalysisTool.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DeepSearchTool.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/MultiModalAgent.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ImageGenerationTool.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java
git commit -m "feat: persist structured replay payloads for rich tools"
```

---

## Task 3: 抽出共享 ReplayProjector，替换当前主 SSE 链路里的硬编码映射

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/replay/ReplayFactBundle.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/replay/ProjectedReplayEvent.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/BaseAgentResponseHandler.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/ReactAgentResponseHandler.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/PlanSolveAgentResponseHandler.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorTest.java`

> 接入原则：以当前 `/AutoAgent -> dispatch -> executeStrategy -> RootNode/Step1 -> LLM/BaseAgent -> SSE 响应聚合` 作为实时主链。`BaseAgentResponseHandler` 如果仍在这条链上承担 `eventData` 组装，就委托 `ReplayProjector`；如果只是旁路兼容代码，则保持最小改动，把共享投影挂到真实主链即可。

- [ ] **Step 1: 先写失败测试，锁定“同一 messageType 在实时和历史必须投成同一 eventData 结构”**

```java
@Test
public void shouldProjectToolThoughtAndDeepSearchWithStableTaskOrdering() {
    ReplayProjector projector = new ReplayProjector();
    EventResult state = new EventResult();

    ProjectedReplayEvent thought = projector.projectRealtime(state, AgentResponse.builder()
            .messageId("msg-thought")
            .messageType("tool_thought")
            .toolThought("先搜资料")
            .isFinal(true)
            .build());

    ProjectedReplayEvent deepSearch = projector.projectRealtime(state, AgentResponse.builder()
            .messageId("msg-search")
            .messageType("deep_search")
            .resultMap(Map.of("messageType", "search", "isFinal", true))
            .build());

    Assert.assertEquals("task", thought.getMessageType());
    Assert.assertEquals("tool_thought", thought.getResultMap().get("messageType"));
    Assert.assertEquals(thought.getTaskId(), deepSearch.getTaskId());
    Assert.assertTrue((Integer) deepSearch.getMessageOrder() >= 1);
}
```

- [ ] **Step 2: 运行测试并确认当前只能走 `BaseAgentResponseHandler` 里的硬编码 switch**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ReplayProjectorTest
```

Expected: 编译失败，说明 `ReplayProjector` 还不存在。

- [ ] **Step 3: 新建共享 projector，保留 `EventResult` 的顺序语义，但不把顺序落库**

```java
public class ReplayProjector {

    public ProjectedReplayEvent projectRealtime(EventResult state, AgentResponse response) {
        if ("plan_thought".equals(response.getMessageType())) {
            return ProjectedReplayEvent.planThought(response.getMessageId(), response.getPlanThought(), response.getIsFinal());
        }
        if ("task".equals(response.getMessageType())) {
            return ProjectedReplayEvent.task(state.renewTaskId(), 1, 1, response);
        }
        return ProjectedReplayEvent.toolEvent(
                state.getTaskId(),
                state.getTaskOrder().getAndIncrement(),
                state.getAndIncrOrder(state.getTaskId() + ":" + response.getMessageType()),
                response
        );
    }

    public List<ProjectedReplayEvent> projectHistory(ReplayFactBundle bundle) {
        EventResult state = new EventResult();
        return bundle.toAgentResponses().stream()
                .map(item -> projectRealtime(state, item))
                .toList();
    }
}
```

- [ ] **Step 4: 让实时 handler 只负责组装 `GptProcessResult`，不再自己决定业务语义**

```java
protected GptProcessResult buildIncrResult(AgentRequest request, EventResult eventResult, AgentResponse agentResponse) {
    ProjectedReplayEvent projected = replayProjector.projectRealtime(eventResult, agentResponse);
    GptProcessResult result = new GptProcessResult();
    result.setReqId(request.getRequestId());
    result.setFinished(Boolean.TRUE.equals(agentResponse.getFinish()));
    result.setStatus(result.isFinished() ? SUCCESS : RUNNING);
    result.setResultMap(Map.of(
            "agentType", resolveAgentType(agentResponse),
            "multiAgent", Map.of(),
            "eventData", projected
    ));
    return result;
}
```

- [ ] **Step 5: 跑 Projector 与聊天聚焦测试并提交**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ReplayProjectorTest
```

Expected: PASS

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/replay/ReplayFactBundle.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/replay/ProjectedReplayEvent.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/BaseAgentResponseHandler.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/ReactAgentResponseHandler.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/PlanSolveAgentResponseHandler.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorTest.java
git commit -m "refactor: centralize realtime event projection"
```

---

## Task 4: 新增历史回放服务和会话查询接口

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/HistoryReplayPrinter.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ConversationHistoryReplayService.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerQueryService.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueRunLedgerDao.java`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml`
- Create: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationHistoryController.java`
- Create: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/resp/ConversationSessionRespVO.java`
- Create: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/resp/ConversationHistoryDetailRespVO.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java`

- [ ] **Step 1: 先写失败测试，锁定“同一 session 能查出多轮 run，并返回 replay frames”**

```java
@Test
public void shouldReturnSessionListAndReplayFrames() {
    ConversationHistoryDetailRespVO detail = controller.detail("session-history-001");
    Assert.assertEquals("session-history-001", detail.getSessionId());
    Assert.assertEquals(2, detail.getRuns().size());
    Assert.assertFalse(detail.getRuns().get(0).getReplayFrames().isEmpty());
    Assert.assertNotNull(detail.getRuns().get(0).getReplayFrames().get(0).getResultMap().get("eventData"));
}
```

- [ ] **Step 2: 运行测试并确认接口尚不存在**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ConversationHistoryControllerTest
```

Expected: 编译失败，缺少 controller / resp / history service。

- [ ] **Step 3: 扩展 DAO 查询，按 owner + session 聚合会话，不新增会话表**

```xml
<select id="queryRecentSessionHeadsByOwner" resultType="org.wwz.ai.domain.agent.reactor.model.ledger.DialogueRunView">
    SELECT *
    FROM ai_agent_dialogue_run
    WHERE deleted = 0
      AND owner_erp = #{ownerErp}
    ORDER BY update_time DESC, id DESC
</select>

<select id="queryBySessionIdOrderByCreateTime" resultMap="DialogueRunMap">
    SELECT *
    FROM ai_agent_dialogue_run
    WHERE deleted = 0
      AND session_id = #{sessionId}
    ORDER BY create_time ASC, id ASC
</select>
```

- [ ] **Step 4: 新建历史服务，按 run 读取事实并复用 `ReplayProjector`**

```java
public List<GptProcessResult> replayRun(String requestId) {
    ExecutionRunDetail detail = executionLedgerQueryService.queryRunDetail(requestId);
    ReplayFactBundle bundle = ReplayFactBundle.from(detail);
    List<ProjectedReplayEvent> events = replayProjector.projectHistory(bundle);
    return historyReplayPrinter.print(events, detail.getRun());
}
```

```java
@GetMapping("/api/agent/conversation/sessions/{sessionId}")
public Response<ConversationHistoryDetailRespVO> detail(@PathVariable String sessionId) {
    return Response.<ConversationHistoryDetailRespVO>builder()
            .code(ResponseCode.SUCCESS.getCode())
            .info("ok")
            .data(conversationHistoryReplayService.querySessionDetail(currentUserErp(), sessionId))
            .build();
}
```

- [ ] **Step 5: 跑接口测试并提交**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ConversationHistoryControllerTest,ExecutionLedgerQueryServiceTest
```

Expected: PASS

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/HistoryReplayPrinter.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ConversationHistoryReplayService.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerQueryService.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueRunLedgerDao.java
git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml
git add ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationHistoryController.java
git add ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/resp/ConversationSessionRespVO.java
git add ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/resp/ConversationHistoryDetailRespVO.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java
git commit -m "feat: add session history replay endpoints"
```

---

## Task 5: 恢复前端历史会话加载，但继续复用现有 `chat.ts` 渲染链

**Files:**
- Modify: `ui/src/services/agentConversation.ts`
- Modify: `ui/src/types/chat.ts`
- Create: `ui/src/utils/conversationHistory.ts`
- Create: `ui/src/utils/conversationHistory.test.ts`
- Modify: `ui/src/pages/Home/index.tsx`

- [ ] **Step 1: 先写前端失败测试，锁定“history frames -> ConversationHistory”恢复行为**

```ts
import { describe, expect, it } from "vitest";
import { hydrateConversationFromReplayFrames } from "@/utils/conversationHistory";

describe("hydrateConversationFromReplayFrames", () => {
  it("rebuilds chat list from replay eventData", () => {
    const conversation = hydrateConversationFromReplayFrames({
      sessionId: "session-history-001",
      outputStyle: "chat",
      deepThink: false,
      runs: [{
        requestId: "req-001",
        replayFrames: [{
          resultMap: {
            eventData: {
              taskId: "task-1",
              taskOrder: 1,
              messageOrder: 1,
              messageType: "task",
              resultMap: { messageType: "tool_thought", toolThought: "先搜资料", isFinal: true }
            }
          }
        }]
      }]
    });

    expect(conversation.chatList).toHaveLength(1);
    expect(conversation.chatList[0].multiAgent.tasks[0][0].toolThought).toBe("先搜资料");
  });
});
```

- [ ] **Step 2: 运行测试并确认恢复工具尚不存在**

Run:

```bash
cd ui && npm run test -- conversationHistory.test.ts
```

Expected: FAIL，缺少 `hydrateConversationFromReplayFrames`。

- [ ] **Step 3: 新增前端历史接口与恢复 helper，继续复用 `combineData`**

```ts
export const conversationHistoryApi = {
  listSessions: () => api.get<ConversationSessionItem[]>("/api/agent/conversation/sessions"),
  getSessionDetail: (sessionId: string) =>
    api.get<ConversationHistoryDetail>(`/api/agent/conversation/sessions/${sessionId}`),
};
```

```ts
export function hydrateConversationFromReplayFrames(detail: ConversationHistoryDetail): CHAT.ConversationHistory {
  let currentChat = { multiAgent: { tasks: [] } } as any;
  const chatList: CHAT.ChatItem[] = [];

  detail.runs.forEach((run) => {
    currentChat = { multiAgent: { tasks: [] } } as any;
    run.replayFrames.forEach((frame) => {
      const eventData = frame?.resultMap?.eventData;
      if (!eventData) {
        return;
      }
      currentChat = combineData(eventData, currentChat);
      if (eventData?.resultMap?.messageType === "result") {
        currentChat.conclusion = buildTaskFromEventData(eventData) as any;
      }
    });
    chatList.push({ ...currentChat });
  });

  return {
    id: `conversation-${detail.sessionId}`,
    sessionId: detail.sessionId,
    title: detail.title,
    productType: detail.outputStyle,
    deepThink: detail.deepThink,
    role: detail.role || null,
    createdAt: detail.createdAt,
    updatedAt: detail.updatedAt,
    chatTitle: detail.title,
    chatList,
    dataChatList: [],
  };
}
```

- [ ] **Step 4: 在 Home 页面恢复会话列表和详情切换，但不改 `ChatView` 主渲染协议**

```ts
const [conversationList, setConversationList] = useState<CHAT.ConversationHistory[]>([]);

useEffect(() => {
  conversationHistoryApi.listSessions().then(async (sessions) => {
    const [first] = sessions || [];
    if (!first) {
      return;
    }
    const detail = await conversationHistoryApi.getSessionDetail(first.sessionId);
    const conversation = hydrateConversationFromReplayFrames(detail);
    setConversationList([conversation]);
    setCurrentConversation(conversation);
  });
}, []);
```

- [ ] **Step 5: 跑前端测试与构建并提交**

Run:

```bash
cd ui && npm run test -- conversationHistory.test.ts chat.test.ts
cd ui && npm run build
```

Expected: PASS

```bash
git add ui/src/services/agentConversation.ts
git add ui/src/types/chat.ts
git add ui/src/utils/conversationHistory.ts
git add ui/src/utils/conversationHistory.test.ts
git add ui/src/pages/Home/index.tsx
git commit -m "feat: restore persisted conversation history in frontend"
```

---

## Task 6: 做完整回归，确认实时与历史投影完全同构

**Files:**
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java`
- Modify: `ui/src/utils/chat.test.ts`

- [ ] **Step 1: 增加“实时帧 vs 历史帧同构”测试**

```java
@Test
public void shouldProduceSameEventShapeForRealtimeAndHistoryReplay() {
    ProjectedReplayEvent realtime = replayProjector.projectRealtime(new EventResult(), response);
    List<ProjectedReplayEvent> history = replayProjector.projectHistory(ReplayFactBundle.from(detail));

    Assert.assertEquals(realtime.getMessageType(), history.get(0).getMessageType());
    Assert.assertEquals(realtime.getResultMap().get("messageType"), history.get(0).getResultMap().get("messageType"));
}
```

- [ ] **Step 2: 运行后端聚焦测试**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ExecutionLedgerQueryServiceTest,ReplayProjectorTest,ConversationHistoryControllerTest,ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest
```

Expected: PASS

- [ ] **Step 3: 运行前端聚焦测试**

Run:

```bash
cd ui && npm run test -- chat.test.ts conversationHistory.test.ts
```

Expected: PASS

- [ ] **Step 4: 跑最终构建校验**

Run:

```bash
mvn -pl ai-agent-station-study-app -am -DskipTests compile
cd ui && npm run build
```

Expected: 后端编译通过，前端构建通过。

- [ ] **Step 5: 最终提交**

```bash
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java
git add ui/src/utils/chat.test.ts
git commit -m "test: verify realtime and history replay stay isomorphic"
```

---

## 自我审查

### Spec 覆盖检查

| 需求 | 对应任务 |
| --- | --- |
| 不新增独立 `event` 表 | Task 1 |
| run/llm/tool/artifact 继续做唯一事实源 | Task 1, Task 2 |
| 只存不能实时重算的字段 | Task 1, Task 2 |
| `tool_invocation` 补齐可回放结构化结果 | Task 2 |
| 实时和历史走同一套投影 | Task 3, Task 4 |
| 历史恢复输出前端现有 `eventData` 形状 | Task 3, Task 4, Task 5 |
| 前端退出后能重新看到完整会话 | Task 4, Task 5 |

### Placeholder 扫描

- 没有 `TODO / TBD / later`
- 没有“自行处理边界情况”这种空话
- 每个任务都列出了具体文件、命令和核心代码骨架

### 类型一致性检查

- run 元信息统一使用 `ownerErp / outputStyle / deepThink / roleAgentId / roleAgentName`
- LLM 语义统一使用 `semanticKind`
- 工具结构化结果统一通过 `ToolReplayPayloadBuilder` 产出
- 历史输出统一继续复用前端现有 `eventData` 协议

### 风险提醒

1. 当前前端主聊天历史列表已经被移除，Task 5 不是“补一个接口”就结束，`Home` 页面状态结构需要一起恢复。
2. Task 3 必须先确认当前 `/AutoAgent` 主链上的实时聚合挂点。如果 `BaseAgentResponseHandler` 和 `EventResult` 仍在主链上，就做同构测试后替换；如果不在主链上，就不要把它们误当成唯一接入点。
3. `file_tool(get)` 是当前最明确的事实缺口，Task 2 必须优先补，不然历史回放一定缺文件引用。
4. run 的 owner 归属如果不能稳定从登录态拿到，就要在入口显式传 ERP；否则会话列表无法按人查询。
