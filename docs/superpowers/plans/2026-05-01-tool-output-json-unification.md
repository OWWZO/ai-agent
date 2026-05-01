# Tool-Native Output JSON Projection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让所有工具调用都稳定落库 `output_json`，其中只存“工具原生结果事实”，不直接存前端事件；历史恢复与分析阶段再由投影层按 `tool_name + output_json + input_json + artifact` 解析出前端渲染参数。

**Architecture:** 复用现有 `ToolResultPayload` 三段语义：`toolResult` 代表工具原始文本结果，`llmObservation` 代表回给主智能体继续推理的 observation，`outputJson` 代表工具原生结果 JSON。后端新增统一 `ToolOutputJsonBuilder` 作为落库 builder，只负责把工具结果收口成“工具事实 JSON”；投影阶段新增 `ToolInvocationProjector` 注册表，每个工具一套解析逻辑，负责把工具事实翻译成 `eventData`。`output_json` 不存 `taskId / taskOrder / messageOrder / renderKind / replaySeq` 这类展示态字段。

**Tech Stack:** Java 17, Spring Boot 3.4.3, MyBatis / Mapper XML, MySQL 8 JSON, JUnit 4

---

## 前置依赖

1. 本计划只解决“`output_json` 如何稳定落工具原生结果，以及 projector 如何按工具解析”的问题。
2. 共享历史投影骨架，例如 `ReplayFactBundle / ProjectedReplayEvent / ReplayProjector`，以 [docs/superpowers/plans/2026-05-01-conversation-history-projector-replay.md](/D:/Java%20Code/ai-agent/ai-agent-station-study/docs/superpowers/plans/2026-05-01-conversation-history-projector-replay.md:1) 为主计划来源。
3. 如果执行本计划时共享投影骨架还没落地，那么 Task 4 里的 `ReplayProjector` / `ReplayProjectorTest` 应先按那份计划创建最小骨架，再接入 `ToolInvocationProjectorRegistry`，不要在这里再发明第二套 replay 模型。

---

## 核心设计结论

### 字段职责

1. `ai_agent_tool_invocation.input_json`
   存工具调用入参事实。
2. `ai_agent_tool_invocation.llm_oberserve`
   存主智能体真正看到并写入记忆的 observation。
3. `ai_agent_tool_invocation.output_json`
   存工具最终输出的原生结果 JSON，不直接长成前端事件。
4. `ai_agent_artifact`
   存文件/图片等稳定产物引用。

### 投影规则

1. Projector 先按 `tool_name` 分发到对应工具解析器。
2. 工具解析器读取 `input_json + output_json + artifact`。
3. 工具解析器输出前端所需参数，例如 `command / fileInfo / searchResult / answer / refs`。
4. 外层 `ReplayProjector` 再负责补 `taskId / taskOrder / messageOrder / visible` 这类展示顺序语义。

### 结果 JSON 原则

1. `output_json` 只表达工具结果事实，不表达前端事件顺序与卡片结构。
2. `output_json` 允许工具之间 shape 不同，但每个工具自己的 shape 必须稳定、可版本化。
3. 对于纯文本工具和 MCP 工具，允许走通用 fallback JSON。
4. 文件类大文本默认不重复塞入 `output_json`，优先只存 `artifact` 引用与必要元信息；只有明确需要且内容足够小，才允许附带短 preview。

---

## 文件结构映射

### 新建文件

| 文件路径 | 职责 |
| --- | --- |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ToolOutputJsonBuilder.java` | 统一构建工具原生结果 JSON |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/ToolInvocationProjector.java` | 单工具投影接口 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/ToolInvocationProjectorRegistry.java` | 按 `tool_name` 分发解析器 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/DefaultToolInvocationProjector.java` | 纯文本 / 通用错误结果投影 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/DeepSearchToolInvocationProjector.java` | deep_search 原生 JSON 投影 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/FileToolInvocationProjector.java` | file_tool 原生 JSON 投影 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/CodeInterpreterToolInvocationProjector.java` | code_interpreter 原生 JSON 投影 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/ReportToolInvocationProjector.java` | report_tool 原生 JSON 投影 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/DataAnalysisToolInvocationProjector.java` | data_analysis 原生 JSON 投影 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/MultiModalToolInvocationProjector.java` | multimodalagent_tool 原生 JSON 投影 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/ImageGenerationToolInvocationProjector.java` | image_generation_tool 原生 JSON 投影 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/ScriptRunnerToolInvocationProjector.java` | script_runner_tool 原生 JSON 投影 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolOutputJsonBuilderTest.java` | 工具结果 JSON shape 测试 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolInvocationProjectorTest.java` | 各工具解析器投影测试 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolOutputJsonRuntimeTest.java` | 运行时落库语义测试 |

### 修改文件

| 文件路径 | 修改内容 |
| --- | --- |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java` | 保证所有工具调用都有 `output_json`，并明确 `llmObservation` 只用于主智能体 observation |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/ToolResultPayload.java` | 增加 `structured(...)` 快捷工厂 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/FileTool.java` | 显式返回 file_tool 原生结果 JSON |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/CodeInterpreterTool.java` | 显式返回 code_interpreter 原生结果 JSON |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ReportTool.java` | 显式返回 report_tool 原生结果 JSON |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DataAnalysisTool.java` | 显式返回 data_analysis 原生结果 JSON |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DeepSearchTool.java` | 显式返回 deep_search 原生结果 JSON |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DeepSearchStructuredResultBuilder.java` | 输出稳定版本化的 deep_search JSON |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/MultiModalAgent.java` | 显式返回 multimodalagent_tool 原生结果 JSON |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ImageGenerationTool.java` | 显式返回 image_generation_tool 原生结果 JSON |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/ScriptRunnerTool.java` | 显式返回 script_runner_tool 原生结果 JSON |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java` | 若已存在则改为委托 `ToolInvocationProjectorRegistry`；若尚未落地则按共享投影计划先创建骨架 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolLlmObservationRuntimeTest.java` | 追加 `llm_oberserve` / `output_json` 双字段断言 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/DeepSearchLlmObservationTest.java` | 追加 deep_search 原生结果 JSON 断言 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java` | 追加 react 路径 tool-native `output_json` 断言 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java` | 追加 plan-solve 路径 tool-native `output_json` 断言 |
| `docs/superpowers/plans/2026-05-01-conversation-history-projector-replay.md` | 把工具结果部分更新为“tool-native output_json + per-tool parser”模型 |

---

## Tool-Native Output JSON Contract

### 通用 fallback JSON

纯文本工具、MCP 工具、skill 工具、失败结果允许走通用结构：

```json
{
  "schemaVersion": 1,
  "resultType": "plain_text",
  "data": {
    "text": "第1行\n第2行"
  }
}
```

```json
{
  "schemaVersion": 1,
  "resultType": "error",
  "data": {
    "message": "Tool missing_tool Error.",
    "errorMsg": "Tool returned null"
  }
}
```

### rich tool 原生 JSON

示例 1：`deep_search`

```json
{
  "schemaVersion": 1,
  "query": "本周项目风险",
  "stages": [
    {
      "stage": "extend",
      "queries": ["项目排期风险", "资源风险"]
    },
    {
      "stage": "search",
      "results": [
        {
          "query": "项目排期风险",
          "docs": [
            {
              "title": "风险日报",
              "link": "https://example.com/risk",
              "content": "..."
            }
          ]
        }
      ]
    },
    {
      "stage": "report",
      "answer": "本周主要风险有..."
    }
  ]
}
```

示例 2：`file_tool/get`

```json
{
  "schemaVersion": 1,
  "command": "get",
  "contentStorageMode": "artifact_only",
  "fileInfo": [
    {
      "fileName": "风险日报.md",
      "ossUrl": "https://oss.example/risk.md",
      "domainUrl": "https://cdn.example/risk.md",
      "fileSize": 1234
    }
  ]
}
```

示例 3：`code_interpreter`

```json
{
  "schemaVersion": 1,
  "codeOutput": "执行完成",
  "stdout": "hello",
  "stderr": "",
  "fileInfo": [
    {
      "fileName": "chart.png",
      "ossUrl": "https://oss.example/chart.png",
      "domainUrl": "https://cdn.example/chart.png",
      "fileSize": 2048
    }
  ]
}
```

约束：

1. 每个 rich tool 的 `output_json` root 可不同，但必须有 `schemaVersion`。
2. `output_json` 不直接存 `renderKind / taskId / taskOrder / messageOrder / eventData`。
3. 如果工具生成文件，`output_json` 可存逻辑 `fileInfo`，但最终稳定 URL 以 `artifact` 账本为准。
4. `file_tool/get` 不把“超长文件全文”重复塞进 `output_json`；默认只存 `contentStorageMode=artifact_only + fileInfo`，如确实需要小预览，也必须由统一 builder 控制上限，而不是工具类各自随意截断。

---

## Task 1: 定义 tool-native JSON builder 与 projector 契约

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ToolOutputJsonBuilder.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/ToolInvocationProjector.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/ToolInvocationProjectorRegistry.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolOutputJsonBuilderTest.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolInvocationProjectorTest.java`

- [ ] **Step 1: 先写失败测试，锁定“output_json 是工具原生结果，不是前端事件”**

```java
@Test
public void shouldBuildPlainTextOutputJsonWithoutFrontendFields() throws Exception {
    String json = ToolOutputJsonBuilder.buildPlainTextResult("第1行\n第2行");
    JsonNode root = new ObjectMapper().readTree(json);

    Assert.assertEquals(1, root.get("schemaVersion").asInt());
    Assert.assertEquals("plain_text", root.get("resultType").asText());
    Assert.assertEquals("第1行\n第2行", root.get("data").get("text").asText());
    Assert.assertNull(root.get("taskId"));
    Assert.assertNull(root.get("renderKind"));
}

@Test
public void shouldProjectFileToolJsonToEventData() {
    ToolInvocationView invocation = ToolInvocationView.builder()
            .toolName("file_tool")
            .inputJson("{\"command\":\"get\",\"fileName\":\"风险日报.md\"}")
            .outputJson("""
                    {"schemaVersion":1,"command":"get","contentStorageMode":"artifact_only","fileInfo":[{"fileName":"风险日报.md"}]}
                    """)
            .build();

    ToolInvocationProjectorRegistry registry = new ToolInvocationProjectorRegistry(
            List.of(new FileToolInvocationProjector(), new DefaultToolInvocationProjector()),
            new DefaultToolInvocationProjector()
    );
    List<ProjectedReplayEvent> events = registry.project(invocation, List.of(), new EventResult());

    Assert.assertEquals(1, events.size());
    Assert.assertEquals("file", events.get(0).getMessageType());
    Assert.assertEquals("读取文件", events.get(0).getResultMap().get("command"));
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ToolOutputJsonBuilderTest,ToolInvocationProjectorTest
```

Expected: FAIL，提示 `ToolOutputJsonBuilder`、`ToolInvocationProjectorRegistry` 不存在。

- [ ] **Step 3: 实现 builder，只负责工具结果 JSON，不负责前端字段**

```java
public final class ToolOutputJsonBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolOutputJsonBuilder() {
    }

    public static String buildPlainTextResult(String text) {
        return writeJson(Map.of(
                "schemaVersion", 1,
                "resultType", "plain_text",
                "data", Map.of("text", StringUtils.defaultString(text))
        ));
    }

    public static String buildErrorResult(String message, String errorMsg) {
        return writeJson(Map.of(
                "schemaVersion", 1,
                "resultType", "error",
                "data", Map.of(
                        "message", StringUtils.defaultString(message),
                        "errorMsg", StringUtils.defaultString(errorMsg)
                )
        ));
    }

    public static String buildToolNativeResult(Object data) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("schemaVersion", 1);
        wrapper.putAll(MAPPER.convertValue(data, new TypeReference<LinkedHashMap<String, Object>>() {}));
        return writeJson(wrapper);
    }
}
```

- [ ] **Step 4: 实现 projector 接口与注册表骨架**

```java
public interface ToolInvocationProjector {

    boolean supports(String toolName);

    List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                       List<ArtifactView> artifacts,
                                       EventResult state);
}
```

```java
@Service
public class ToolInvocationProjectorRegistry {

    private final List<ToolInvocationProjector> projectors;
    private final ToolInvocationProjector defaultProjector;

    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        for (ToolInvocationProjector projector : projectors) {
            if (projector.supports(invocation.getToolName())) {
                return projector.project(invocation, artifacts, state);
            }
        }
        return defaultProjector.project(invocation, artifacts, state);
    }
}
```

- [ ] **Step 5: 跑契约测试**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ToolOutputJsonBuilderTest,ToolInvocationProjectorTest
```

Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ToolOutputJsonBuilder.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/ToolInvocationProjector.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/ToolInvocationProjectorRegistry.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolOutputJsonBuilderTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolInvocationProjectorTest.java
git commit -m "feat: define tool native output json and projector contracts"
```

---

## Task 2: 让 BaseAgent 明确 `llm_oberserve` 与 `output_json` 的分工，并保证所有工具都有 output_json

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/ToolResultPayload.java`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolOutputJsonRuntimeTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolLlmObservationRuntimeTest.java`

- [ ] **Step 1: 先写失败测试，锁定“双字段语义”**

```java
@Test
public void shouldPersistToolOutputJsonAndLlmObservationSeparately() {
    ExecutionLedgerFixtureFactory.LedgerTestContext ledger = ExecutionLedgerFixtureFactory.newLedgerTestContext();
    AgentContext context = ExecutionLedgerFixtureFactory.newAgentContext("req-tool-json-runtime-001", "session-tool-json-runtime-001", ledger.recorder);
    ExecutionLedgerFixtureFactory.activateRun(context, ledger.recorder, ExecutionLedgerConstants.ENTRY_AGENT_REACT);
    ExecutionLedgerFixtureFactory.createLlmInvocation(
            context,
            ledger.recorder,
            "react",
            1,
            ExecutionLedgerConstants.CALL_KIND_ASK_TOOL
    );

    TestPlainTextAgent agent = new TestPlainTextAgent(context, "read_tool", "第1行\n第2行");
    String observation = agent.executeTool(ExecutionLedgerFixtureFactory.newToolCall(
            "tool-json-runtime-call-001",
            "read_tool",
            Map.of("path", "/tmp/demo.txt")
    ));

    ToolInvocationView invocation = ledger.queryService.queryRunDetail(context.getAgentRunState().getRunId()).getToolInvocations().get(0);
    Assert.assertEquals(observation, invocation.getLlmObservation());
    Assert.assertNotNull(invocation.getOutputJson());
    Assert.assertTrue(invocation.getOutputJson().contains("\"resultType\":\"plain_text\""));
    Assert.assertFalse(invocation.getOutputJson().contains("\"taskId\""));
}

@Test
public void shouldPersistErrorOutputJsonForFailedTool() {
    ExecutionLedgerFixtureFactory.LedgerTestContext ledger = ExecutionLedgerFixtureFactory.newLedgerTestContext();
    AgentContext context = ExecutionLedgerFixtureFactory.newAgentContext("req-tool-json-runtime-002", "session-tool-json-runtime-002", ledger.recorder);
    ExecutionLedgerFixtureFactory.activateRun(context, ledger.recorder, ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE);
    ExecutionLedgerFixtureFactory.createLlmInvocation(
            context,
            ledger.recorder,
            "executor",
            1,
            ExecutionLedgerConstants.CALL_KIND_ASK_TOOL
    );

    TestNullAgent agent = new TestNullAgent(context, "missing_tool");
    agent.executeTool(ExecutionLedgerFixtureFactory.newToolCall(
            "tool-json-runtime-call-002",
            "missing_tool",
            Map.of("query", "风险")
    ));

    ToolInvocationView invocation = ledger.queryService.queryRunDetail(context.getAgentRunState().getRunId()).getToolInvocations().get(0);
    Assert.assertTrue(invocation.getOutputJson().contains("\"resultType\":\"error\""));
}
```

- [ ] **Step 2: 运行运行时测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ToolOutputJsonRuntimeTest,ToolLlmObservationRuntimeTest
```

Expected: FAIL，当前普通文本结果和失败结果仍可能没有 `output_json`。

- [ ] **Step 3: 改 BaseAgent，普通文本 / 对象 / 失败路径统一补 tool-native output_json**

```java
private ToolResultPayload normalizeToolResultPayload(Object rawResult, ObjectMapper mapper) {
    if (rawResult instanceof ToolResultPayload payload) {
        String toolResult = StringUtils.defaultString(payload.getToolResult());
        String llmObservation = StringUtils.defaultIfBlank(payload.getLlmObservation(), toolResult);
        String outputJson = StringUtils.defaultIfBlank(
                payload.getOutputJson(),
                ToolOutputJsonBuilder.buildPlainTextResult(toolResult)
        );
        return ToolResultPayload.builder()
                .toolResult(toolResult)
                .llmObservation(llmObservation)
                .outputJson(outputJson)
                .build();
    }
    if (rawResult instanceof String textResult) {
        return ToolResultPayload.builder()
                .toolResult(textResult)
                .llmObservation(textResult)
                .outputJson(ToolOutputJsonBuilder.buildPlainTextResult(textResult))
                .build();
    }
    return ToolResultPayload.builder()
            .toolResult(writeAsJson(rawResult, mapper))
            .llmObservation(writeAsJson(rawResult, mapper))
            .outputJson(ToolOutputJsonBuilder.buildToolNativeResult(rawResult))
            .build();
}
```

```java
private ToolExecutionOutcome buildFailureOutcome(String message, String errorMsg) {
    return ToolExecutionOutcome.failure(
            message,
            message,
            ToolOutputJsonBuilder.buildErrorResult(message, errorMsg),
            errorMsg
    );
}
```

- [ ] **Step 4: 给 ToolResultPayload 增加快捷工厂**

```java
public static ToolResultPayload structured(String toolResult, String llmObservation, String outputJson) {
    return ToolResultPayload.builder()
            .toolResult(toolResult)
            .llmObservation(llmObservation)
            .outputJson(outputJson)
            .build();
}
```

- [ ] **Step 5: 跑 BaseAgent 运行时测试**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ToolOutputJsonRuntimeTest,ToolLlmObservationRuntimeTest
```

Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/ToolResultPayload.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolOutputJsonRuntimeTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolLlmObservationRuntimeTest.java
git commit -m "feat: guarantee tool native output json for all outcomes"
```

---

## Task 3: 改造 rich local tools，显式返回各自稳定的原生结果 JSON

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/FileTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/CodeInterpreterTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ReportTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DataAnalysisTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DeepSearchTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DeepSearchStructuredResultBuilder.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/MultiModalAgent.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ImageGenerationTool.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/ScriptRunnerTool.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/DeepSearchLlmObservationTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/MultiModalAgentToolTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java`

- [ ] **Step 1: 先写失败测试，锁定 deep_search / file_tool / script_runner_tool 的原生 JSON shape**

```java
@Test
public void shouldPersistDeepSearchNativeJson() {
    ToolInvocationView invocation = runDeepSearchAndLoadInvocation();
    Assert.assertTrue(invocation.getOutputJson().contains("\"schemaVersion\":1"));
    Assert.assertTrue(invocation.getOutputJson().contains("\"stages\""));
    Assert.assertFalse(invocation.getOutputJson().contains("\"renderKind\""));
}

@Test
public void shouldPersistFileToolNativeJson() {
    ToolInvocationView invocation = runFileToolGetAndLoadInvocation();
    Assert.assertTrue(invocation.getOutputJson().contains("\"command\":\"get\""));
    Assert.assertTrue(invocation.getOutputJson().contains("\"fileInfo\""));
    Assert.assertFalse(invocation.getOutputJson().contains("\"taskId\""));
}

@Test
public void shouldPersistScriptRunnerNativeJson() {
    ToolInvocationView invocation = runScriptRunnerAndLoadInvocation();
    Assert.assertTrue(invocation.getOutputJson().contains("\"scriptName\""));
    Assert.assertTrue(invocation.getOutputJson().contains("\"stdout\""));
}
```

- [ ] **Step 2: 运行 rich tool 测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=DeepSearchLlmObservationTest,MultiModalAgentToolTest,ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest
```

Expected: FAIL，当前 rich tool 的 `output_json` 为空、只是字符串 JSON，或 shape 不稳定。

- [ ] **Step 3: FileTool / CodeInterpreter / Report / DataAnalysis 返回原生结果 JSON**

```java
Map<String, Object> outputData = new LinkedHashMap<>();
outputData.put("command", "get");
outputData.put("contentStorageMode", "artifact_only");
outputData.put("fileInfo", List.of(Map.of(
        "fileName", fileRequest.getFileName(),
        "ossUrl", fileResponse.getOssUrl(),
        "domainUrl", fileResponse.getDomainUrl(),
        "fileSize", fileResponse.getFileSize()
)));
return ToolResultPayload.structured(
        "文件内容 " + fileContent,
        "文件内容 " + fileContent,
        ToolOutputJsonBuilder.buildToolNativeResult(outputData)
);
```

```java
Map<String, Object> outputData = new LinkedHashMap<>();
outputData.put("codeOutput", StringUtils.defaultString(codeResponse.getCodeOutput()));
outputData.put("stdout", StringUtils.defaultString(codeResponse.getCodeOutput()));
outputData.put("stderr", "");
outputData.put("fileInfo", safeFileInfo(codeResponse.getFileInfo()));
return ToolResultPayload.structured(output, output, ToolOutputJsonBuilder.buildToolNativeResult(outputData));
```

- [ ] **Step 4: DeepSearch / MultiModal / ImageGeneration / ScriptRunner 返回原生结果 JSON**

```java
String deepSearchJson = resultBuilder.buildJson(resultRef.get());
return ToolResultPayload.structured(
        resultRef.get(),
        resultRef.get(),
        deepSearchJson
);
```

```java
Map<String, Object> outputData = new LinkedHashMap<>();
outputData.put("markdown", markdownContent);
outputData.put("summary", markdownContent.substring(0, Math.min(markdownContent.length(), 120)));
return ToolResultPayload.structured(markdownContent, markdownContent, ToolOutputJsonBuilder.buildToolNativeResult(outputData));
```

```java
Map<String, Object> outputData = new LinkedHashMap<>();
outputData.put("prompt", requestPayload.getPrompt());
outputData.put("summary", StringUtils.defaultIfBlank(summary, "image_generation_tool 执行完成"));
outputData.put("fileInfo", response.getFileInfo());
return ToolResultPayload.structured(summary, summary, ToolOutputJsonBuilder.buildToolNativeResult(outputData));
```

```java
Map<String, Object> outputData = new LinkedHashMap<>();
outputData.put("skillName", response.getSkillName());
outputData.put("scriptName", response.getScriptName());
outputData.put("runtime", response.getRuntime());
outputData.put("success", Boolean.TRUE.equals(response.getSuccess()));
outputData.put("exitCode", response.getExitCode());
outputData.put("stdout", StringUtils.defaultString(response.getStdout()));
outputData.put("stderr", StringUtils.defaultString(response.getStderr()));
outputData.put("summary", StringUtils.defaultString(response.getSummary()));
outputData.put("fileInfo", response.getFileInfo());
return ToolResultPayload.structured(displayText, displayText, ToolOutputJsonBuilder.buildToolNativeResult(outputData));
```

- [ ] **Step 5: 跑 rich tool 回归**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=DeepSearchLlmObservationTest,MultiModalAgentToolTest,ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest
```

Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/FileTool.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/CodeInterpreterTool.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ReportTool.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DataAnalysisTool.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DeepSearchTool.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/DeepSearchStructuredResultBuilder.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/MultiModalAgent.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ImageGenerationTool.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/skill/ScriptRunnerTool.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/DeepSearchLlmObservationTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/MultiModalAgentToolTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java
git commit -m "feat: persist tool native output json for rich tools"
```

---

## Task 4: 实现 per-tool projector 解析器，并让历史投影按工具解析 output_json

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/DefaultToolInvocationProjector.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/DeepSearchToolInvocationProjector.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/FileToolInvocationProjector.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/CodeInterpreterToolInvocationProjector.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/ReportToolInvocationProjector.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/DataAnalysisToolInvocationProjector.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/MultiModalToolInvocationProjector.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/ImageGenerationToolInvocationProjector.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector/impl/ScriptRunnerToolInvocationProjector.java`
- Modify or Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolInvocationProjectorTest.java`
- Modify or Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorTest.java`

- [ ] **Step 1: 先写失败测试，锁定“投影器按 tool_name 解析原生 JSON”**

```java
@Test
public void shouldProjectDeepSearchStagesFromNativeJson() {
    ToolInvocationView invocation = ToolInvocationView.builder()
            .toolName("deep_search")
            .inputJson("{\"query\":\"本周项目风险\"}")
            .outputJson("""
                    {
                      "schemaVersion": 1,
                      "query": "本周项目风险",
                      "stages": [
                        {"stage":"extend","queries":["项目排期风险"]},
                        {"stage":"search","results":[{"query":"项目排期风险","docs":[{"title":"风险日报","link":"https://example.com/risk"}]}]},
                        {"stage":"report","answer":"本周主要风险有..."}
                      ]
                    }
                    """)
            .build();

    List<ProjectedReplayEvent> events = registry.project(invocation, List.of(), new EventResult());
    Assert.assertEquals(3, events.size());
    Assert.assertEquals("deep_search", events.get(0).getMessageType());
    Assert.assertEquals("extend", events.get(0).getResultMap().get("messageType"));
    Assert.assertEquals("report", events.get(2).getResultMap().get("messageType"));
}

@Test
public void shouldProjectPlainTextFallbackViaDefaultProjector() {
    ToolInvocationView invocation = ToolInvocationView.builder()
            .toolName("read_tool")
            .outputJson("""
                    {"schemaVersion":1,"resultType":"plain_text","data":{"text":"hello"}}
                    """)
            .build();

    List<ProjectedReplayEvent> events = registry.project(invocation, List.of(), new EventResult());
    Assert.assertEquals(1, events.size());
    Assert.assertEquals("tool_result", events.get(0).getMessageType());
}
```

- [ ] **Step 2: 运行 projector 测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ToolInvocationProjectorTest,ReplayProjectorTest
```

Expected: FAIL，当前 `ReplayProjector` 还没有按 `tool_name + output_json` 分发解析。

- [ ] **Step 3: 实现 default / file / deep_search 解析器**

```java
@Component
public class DefaultToolInvocationProjector implements ToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return false;
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        JsonNode root = readJson(invocation.getOutputJson());
        String text = root.path("data").path("text").asText("");
        if ("error".equals(root.path("resultType").asText())) {
            text = root.path("data").path("message").asText(text);
        }
        return List.of(ProjectedReplayEvent.toolResult(state.getTaskId(), text));
    }
}
```

```java
@Component
public class FileToolInvocationProjector implements ToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return "file_tool".equals(toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        JsonNode root = readJson(invocation.getOutputJson());
        String command = root.path("command").asText();
        List<Map<String, Object>> fileInfo = mergeArtifactFileInfo(root.path("fileInfo"), artifacts);
        return List.of(ProjectedReplayEvent.fileResult(
                state.getTaskId(),
                "get".equals(command) ? "读取文件" : "写入文件",
                fileInfo
        ));
    }
}
```

```java
@Component
public class DeepSearchToolInvocationProjector implements ToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return "deep_search".equals(toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        JsonNode root = readJson(invocation.getOutputJson());
        List<ProjectedReplayEvent> events = new ArrayList<>();
        for (JsonNode stage : root.path("stages")) {
            events.add(projectStage(invocation, stage, artifacts, state));
        }
        return events;
    }
}
```

- [ ] **Step 4: 实现其余 rich tool 解析器，并让 ReplayProjector 委托注册表**

```java
public List<ProjectedReplayEvent> projectHistory(ReplayFactBundle bundle) {
    EventResult state = new EventResult();
    List<ProjectedReplayEvent> events = new ArrayList<>();
    Map<Long, List<ArtifactView>> artifactMap = bundle.getArtifacts().stream()
            .collect(Collectors.groupingBy(ArtifactView::getToolInvocationId, LinkedHashMap::new, Collectors.toList()));
    for (ToolInvocationView invocation : bundle.getToolInvocations()) {
        List<ArtifactView> artifacts = artifactMap.getOrDefault(invocation.getId(), List.of());
        events.addAll(toolInvocationProjectorRegistry.project(invocation, artifacts, state));
    }
    return events;
}
```

- [ ] **Step 5: 跑 parser + projector 回归**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ToolInvocationProjectorTest,ReplayProjectorTest
```

Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/projector
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ToolInvocationProjectorTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorTest.java
git commit -m "feat: project tool native output json via per-tool parsers"
```

---

## Task 5: 对齐历史回放计划与全链路回归

**Files:**
- Modify: `docs/superpowers/plans/2026-05-01-conversation-history-projector-replay.md`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java`

- [ ] **Step 1: 更新历史回放计划，明确“tool-native output_json + per-tool parser”是唯一工具恢复路径**

```markdown
- `output_json` 记录工具原生结果 JSON，不直接承载前端事件字段。
- 历史恢复时，`ReplayProjector` 必须按 `tool_name + output_json + input_json + artifact` 委托到工具解析器。
- `llm_oberserve` 只承载主智能体 observation，不作为前端工具渲染参数来源。
```

- [ ] **Step 2: 追加端到端断言，锁定每条 tool invocation 都有 tool-native output_json**

```java
@Test
public void shouldExposeToolNativeOutputJsonForEveryToolInvocation() {
    ExecutionRunDetail detail = runTypicalReactFlow();
    for (ToolInvocationView invocation : detail.getToolInvocations()) {
        Assert.assertNotNull(invocation.getOutputJson());
        Assert.assertFalse(invocation.getOutputJson().isBlank());
        Assert.assertTrue(invocation.getOutputJson().contains("\"schemaVersion\":1"));
        Assert.assertFalse(invocation.getOutputJson().contains("\"taskId\""));
    }
}
```

- [ ] **Step 3: 运行完整聚焦回归**

Run:

```bash
mvn test -pl ai-agent-station-study-app -Dtest=ToolOutputJsonBuilderTest,ToolInvocationProjectorTest,ToolOutputJsonRuntimeTest,ToolLlmObservationRuntimeTest,DeepSearchLlmObservationTest,ReplayProjectorTest,ExecutionLedgerQueryServiceTest,ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest,MultiModalAgentToolTest
```

Expected: PASS

- [ ] **Step 4: 提交**

```bash
git add docs/superpowers/plans/2026-05-01-conversation-history-projector-replay.md
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java
git commit -m "test: lock tool native output json replay contract"
```

---

## 覆盖矩阵

| 工具类别 | output_json 形态 | projector 解析方式 |
| --- | --- | --- |
| `file_tool` | 工具原生 JSON | `FileToolInvocationProjector` |
| `code_interpreter` | 工具原生 JSON | `CodeInterpreterToolInvocationProjector` |
| `report_tool` | 工具原生 JSON | `ReportToolInvocationProjector` |
| `data_analysis` | 工具原生 JSON | `DataAnalysisToolInvocationProjector` |
| `deep_search` | 工具原生 JSON | `DeepSearchToolInvocationProjector` |
| `multimodalagent_tool` | 工具原生 JSON | `MultiModalToolInvocationProjector` |
| `image_generation_tool` | 工具原生 JSON | `ImageGenerationToolInvocationProjector` |
| `script_runner_tool` | 工具原生 JSON | `ScriptRunnerToolInvocationProjector` |
| `skill_tool / read_tool / list_directory_tool / glob_tool / grep_tool` | 通用 plain_text JSON | `DefaultToolInvocationProjector` |
| MCP 工具 | 通用 plain_text JSON | `DefaultToolInvocationProjector` |
| 任意失败工具 | 通用 error JSON | `DefaultToolInvocationProjector` |

### 类型一致性检查

- `llm_oberserve` 永远只存主智能体 observation
- `output_json` 永远只存工具结果 JSON
- `ReplayProjector` 不直接 switch `renderKind`，而是先按 `tool_name` 分发 projector
- `input_json` 不并入 `output_json`，保持账本职责清晰
- `artifact` 继续作为文件稳定引用事实表，不被 `output_json` 替代

### 风险提醒

1. 这个方案比“统一 canonical render payload”更灵活，但也意味着 projector 侧会有更多工具专属逻辑，必须靠测试锁住。
2. `deep_search` 这类流式工具的实时链路仍可继续发送阶段事件；本计划只约束“最终落库的 output_json” shape，不要求实时先等最终 JSON 再渲染。
3. `output_json` 如果塞入过大的全文，会带来 MySQL 存储与查询压力；对 `file_tool/get` 这类文件正文场景，默认应只存 `artifact` 引用和必要元信息，不重复持久化长文本。
4. 当前工作区里 `docs/superpowers/plans/2026-05-01-conversation-history-projector-replay.md` 已存在，实施时必须同步更新，避免两份计划的工具恢复语义互相冲突。
