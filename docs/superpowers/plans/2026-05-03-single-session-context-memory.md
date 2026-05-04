# Single Session Context Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为同一 `sessionId` 下的新请求构建单会话上下文记忆，从 `ai_agent_llm_invocation`、`ai_agent_tool_invocation`、`ai_agent_artifact` 读取完整历史，并按“一次 ReAct 循环”组装为 `historyDialogue` 注入到后续推理链路中。

**Architecture:** 在执行策略入口前新增一层 request enrich。该层按 `sessionId` 查询历史 run，再按 `llm_invocation.id` 作为 ReAct 循环锚点，将思考内容、工具调用、工具关联文件信息组装成线性 `historyDialogue` 文本，回填到 `AgentRequest.historyDialogue`。本期不做上下文压缩、不做思考内容裁剪、不读取 artifact 对应文件正文，仅保留文件元信息。

**Tech Stack:** Java 17, Spring Boot 3.4.3, MyBatis Mapper XML, 现有 Reactor Agent 执行链, JUnit 测试

---

## File Structure

### 需要新增的文件

- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/SessionHistoryMemory.java`
  - 单会话记忆聚合根，包含 run 列表与格式化前的中间结构。
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/RunHistoryMemory.java`
  - 单次 run 的记忆结构，包含 run 级输入文件与 ReAct 循环列表。
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/ReactCycleMemory.java`
  - 单次 LLM 调用对应的一次循环，保存完整 `responseText` 与工具调用列表。
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/ToolCallMemory.java`
  - 工具调用记忆结构，保存 `inputJson`、`llmObservation` 与关联文件。
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/FileArtifactMemory.java`
  - 文件元信息结构，保存 `fileName`、`storageKey`、`downloadUrl`、`previewUrl`、`mimeType`、`fileSize`。
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/SessionContextMemoryService.java`
  - 单会话上下文记忆服务接口。
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/SessionContextMemoryServiceImpl.java`
  - 查询历史账本并组装 `historyDialogue` 的核心实现。
- `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryServiceTest.java`
  - 单元测试，验证 cycle 组装与输出文本结构。
- `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryIntegrationTest.java`
  - 集成测试，验证执行策略入口会自动注入记忆。

### 需要修改的文件

- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ILlmInvocationLedgerDao.java`
  - 补充按 `runIds` 批量查询接口。
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IToolInvocationLedgerDao.java`
  - 补充按 `runIds`、`llmInvocationIds` 批量查询接口。
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IArtifactLedgerDao.java`
  - 补充按 `toolInvocationIds` 查询输出文件、按 `runIds` 查询输入文件接口。
- `ai-agent-station-study-app/src/main/resources/mybatis/mapper/llm_invocation_ledger_mapper.xml`
  - 增加批量查询 SQL。
- `ai-agent-station-study-app/src/main/resources/mybatis/mapper/tool_invocation_ledger_mapper.xml`
  - 增加按 `llm_invocation_id` 批量查询 SQL。
- `ai-agent-station-study-app/src/main/resources/mybatis/mapper/artifact_ledger_mapper.xml`
  - 复用现有 `queryByRunIds`；新增按 `tool_invocation_id IN (...)` 与 `artifact_role='input'` 批量查询 SQL。
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/ReactAgentExecuteStrategy.java`
  - 在执行前调用单会话记忆服务，回填 `request.historyDialogue`。
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/PlanSolveAgentExecuteStrategy.java`
  - 同步接入单会话记忆服务。

### 需要参考的现有文件

- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java`
  - 现有 `historyDialogue` 注入点与 `toolInvocationId`、`artifact` 挂载关系。
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerRunSupport.java`
  - 输入文件如何以 artifact 账本落库。
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java`
  - 现有账本查询与 view 转换模式。
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/step/RootNode.java`
  - `historyDialogue` 如何进入 `AgentContext`。
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/step/Step1SopRecallAndPrepareNode.java`
  - PlanSolve 模式下 `historyDialogue` 如何进入 `AgentContext`。
- `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java`
  - 内存 DAO 测试桩；新增 DAO 接口方法时必须同步补齐。

## 设计约束

- 本期严格遵循用户要求，不做上下文压缩，不做思考内容裁剪，不做 token 控制。
- `ai_agent_llm_invocation.response_text` 作为完整思考内容原样进入记忆。
- `ai_agent_tool_invocation.input_json` 与 `llm_oberserve` 原样进入记忆。
- `ai_agent_artifact` 只读取文件元信息，不读取文件正文。
- 记忆组织单位固定为“一次 ReAct 循环”，锚点为 `llm_invocation.id`，不是 `run_id`。
- 需要排除当前正在执行的请求对应 run，避免把本轮未完成内容注入本轮。本期按 `requestId` 过滤当前 run，依赖 `DialogueRun.requestId` 作为单次请求唯一标识，且当前首期实现里 `runUid` 复用该请求标识。
- 保持现有 `BaseAgent.injectHistoryDialogue(...)` 注入方式不变，只在进入执行策略前补齐 `request.historyDialogue`。
- 本期只覆盖 `ReactAgentExecuteStrategy` 与 `PlanSolveAgentExecuteStrategy`。`AutoAgentExecuteStrategy` 当前 `execute(AgentRequest, SseEmitter)` 为空实现，且 auto 链已有独立 chat memory 机制；`workflow/FlowAgentExecuteStrategy` 使用 `CHAT_MEMORY_CONVERSATION_ID_KEY` 走 Spring AI chat memory，不走 `historyDialogue` 注入链路，因此均不纳入本期改动。

## 输出模板约定

最终 `historyDialogue` 采用如下结构：

```text
## 单会话历史记忆

### Run {requestId}
[Session Input Files]
- fileName={fileName}, mimeType={mimeType}, storageKey={storageKey}, downloadUrl={downloadUrl}, previewUrl={previewUrl}

[ReAct Cycle {invocationSeq}]
Thought:
{llmInvocation.responseText}

Tool Calls:
1. toolName={toolName}
   toolProvider={toolProvider}
   inputJson={inputJson}
   llmObservation={llmObservation}
   Files:
   - artifactRole={artifactRole}, fileName={fileName}, storageKey={storageKey}, downloadUrl={downloadUrl}, previewUrl={previewUrl}
```

若某个 cycle 无工具调用，输出：

```text
Tool Calls:
- none
```

若某个 tool 无文件输出，输出：

```text
Files:
- none
```

若某个 run 无输入文件，省略 `[Session Input Files]` 段。

### Task 1: 扩展账本 DAO 以支持单会话记忆查询

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ILlmInvocationLedgerDao.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IToolInvocationLedgerDao.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IArtifactLedgerDao.java`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/llm_invocation_ledger_mapper.xml`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/tool_invocation_ledger_mapper.xml`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/artifact_ledger_mapper.xml`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryServiceTest.java`

- [ ] **Step 1: 为 DAO 接口补齐批量查询签名**

```java
List<LlmInvocation> queryByRunIds(@Param("runIds") List<Long> runIds);

List<ToolInvocation> queryByRunIds(@Param("runIds") List<Long> runIds);

List<ToolInvocation> queryByLlmInvocationIds(@Param("llmInvocationIds") List<Long> llmInvocationIds);

List<ArtifactRecord> queryByToolInvocationIds(@Param("toolInvocationIds") List<Long> toolInvocationIds);

List<ArtifactRecord> queryInputArtifactsByRunIds(@Param("runIds") List<Long> runIds);
```

- [ ] **Step 2: 为 `llm_invocation_ledger_mapper.xml` 增加批量查询 SQL**

```xml
<select id="queryByRunIds" resultMap="LlmInvocationMap">
    SELECT *
    FROM ai_agent_llm_invocation
    WHERE deleted = 0
      AND run_id IN
    <foreach collection="runIds" item="runId" open="(" separator="," close=")">
        #{runId}
    </foreach>
    ORDER BY run_id ASC, invocation_seq ASC, id ASC
</select>
```

- [ ] **Step 3: 为 `tool_invocation_ledger_mapper.xml` 增加批量查询 SQL**

```xml
<select id="queryByRunIds" resultMap="ToolInvocationMap">
    SELECT *
    FROM ai_agent_tool_invocation
    WHERE deleted = 0
      AND run_id IN
    <foreach collection="runIds" item="runId" open="(" separator="," close=")">
        #{runId}
    </foreach>
    ORDER BY run_id ASC, llm_invocation_id ASC, dispatch_index ASC, id ASC
</select>

<select id="queryByLlmInvocationIds" resultMap="ToolInvocationMap">
    SELECT *
    FROM ai_agent_tool_invocation
    WHERE deleted = 0
      AND llm_invocation_id IN
    <foreach collection="llmInvocationIds" item="llmInvocationId" open="(" separator="," close=")">
        #{llmInvocationId}
    </foreach>
    ORDER BY llm_invocation_id ASC, dispatch_index ASC, id ASC
</select>
```

- [ ] **Step 4: 为 `artifact_ledger_mapper.xml` 复用现有 `queryByRunIds`，并新增输入/输出文件批量查询 SQL**

```xml
<select id="queryByToolInvocationIds" resultMap="ArtifactRecordMap">
    SELECT *
    FROM ai_agent_artifact
    WHERE deleted = 0
      AND artifact_role = 'output'
      AND visibility = 'visible'
      AND tool_invocation_id IN
    <foreach collection="toolInvocationIds" item="toolInvocationId" open="(" separator="," close=")">
        #{toolInvocationId}
    </foreach>
    ORDER BY tool_invocation_id ASC, create_time ASC, id ASC
</select>

<select id="queryInputArtifactsByRunIds" resultMap="ArtifactRecordMap">
    SELECT *
    FROM ai_agent_artifact
    WHERE deleted = 0
      AND artifact_role = 'input'
      AND visibility = 'visible'
      AND run_id IN
    <foreach collection="runIds" item="runId" open="(" separator="," close=")">
        #{runId}
    </foreach>
    ORDER BY run_id ASC, create_time ASC, id ASC
</select>
```

- [ ] **Step 5: 为 `ExecutionLedgerFixtureFactory` 的内存 DAO 同步补齐新接口实现**

```java
@Override
public List<LlmInvocation> queryByRunIds(List<Long> runIds) {
    return store.llmInvocations.values().stream()
            .filter(item -> item.getDeleted() == 0 && runIds.contains(item.getRunId()))
            .sorted(Comparator.comparing(LlmInvocation::getRunId)
                    .thenComparing(LlmInvocation::getInvocationSeq)
                    .thenComparing(LlmInvocation::getId))
            .map(ExecutionLedgerFixtureFactory::cloneLlm)
            .toList();
}

@Override
public List<ToolInvocation> queryByLlmInvocationIds(List<Long> llmInvocationIds) {
    return store.toolInvocations.values().stream()
            .filter(item -> item.getDeleted() == 0 && llmInvocationIds.contains(item.getLlmInvocationId()))
            .sorted(Comparator.comparing(ToolInvocation::getLlmInvocationId)
                    .thenComparing(ToolInvocation::getDispatchIndex)
                    .thenComparing(ToolInvocation::getId))
            .map(ExecutionLedgerFixtureFactory::cloneTool)
            .toList();
}

@Override
public List<ArtifactRecord> queryInputArtifactsByRunIds(List<Long> runIds) {
    return store.artifacts.values().stream()
            .filter(item -> item.getDeleted() == 0
                    && runIds.contains(item.getRunId())
                    && ExecutionLedgerConstants.ARTIFACT_ROLE_INPUT.equals(item.getArtifactRole())
                    && ExecutionLedgerConstants.VISIBILITY_VISIBLE.equals(item.getVisibility()))
            .sorted(Comparator.comparing(ArtifactRecord::getRunId)
                    .thenComparing(ArtifactRecord::getCreateTime)
                    .thenComparing(ArtifactRecord::getId))
            .map(ExecutionLedgerFixtureFactory::cloneArtifact)
            .toList();
}
```

- [ ] **Step 6: 写 DAO 查询层失败测试**

```java
@Test
public void shouldQueryToolInvocationsByLlmInvocationIdsInStableOrder() {
    // given
    // seed 两个 llmInvocationId，下挂多个 toolInvocation，dispatchIndex 乱序写入

    // when
    // queryByLlmInvocationIds(...)

    // then
    // 按 llmInvocationId ASC, dispatchIndex ASC, id ASC 返回
}
```

- [ ] **Step 7: 运行测试验证失败**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=SessionContextMemoryServiceTest -DskipTests=false`
Expected: FAIL，提示缺少 DAO 方法或 mapper SQL。

- [ ] **Step 8: 实现 DAO、mapper 与内存 DAO 代码**

```java
// 生产 DAO 接口、mapper XML 与 ExecutionLedgerFixtureFactory 内存 DAO 必须保持同签名。
```

- [ ] **Step 9: 再次运行测试验证通过**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=SessionContextMemoryServiceTest -DskipTests=false`
Expected: PASS 或进入下一层 service 未实现的失败。

- [ ] **Step 10: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ILlmInvocationLedgerDao.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IToolInvocationLedgerDao.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IArtifactLedgerDao.java ai-agent-station-study-app/src/main/resources/mybatis/mapper/llm_invocation_ledger_mapper.xml ai-agent-station-study-app/src/main/resources/mybatis/mapper/tool_invocation_ledger_mapper.xml ai-agent-station-study-app/src/main/resources/mybatis/mapper/artifact_ledger_mapper.xml ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryServiceTest.java
git commit -m "feat: add ledger batch queries for session context memory"
```

### Task 2: 建立单会话记忆中间模型

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/SessionHistoryMemory.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/RunHistoryMemory.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/ReactCycleMemory.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/ToolCallMemory.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory/FileArtifactMemory.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryServiceTest.java`

- [ ] **Step 1: 写模型结构测试**

```java
@Test
public void shouldAssembleOneRunIntoOrderedReactCycles() {
    // given
    // 一个 run 下有 2 个 llmInvocation，其中第一个有 2 个工具，第二个无工具

    // when
    // 组装 SessionHistoryMemory

    // then
    // run 下 cycle 顺序按 invocationSeq 排列
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=SessionContextMemoryServiceTest -DskipTests=false`
Expected: FAIL，提示 memory model 类型不存在。

- [ ] **Step 3: 创建内存模型**

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactCycleMemory {
    private Long runId;
    private String requestId;
    private Long llmInvocationId;
    private Integer invocationSeq;
    private String agentName;
    private Integer stepNo;
    private String thoughtContent;
    @Builder.Default
    private List<ToolCallMemory> toolCalls = new ArrayList<>();
}
```

- [ ] **Step 4: 为文件结构增加中文注释，明确职责边界**

```java
/**
 * 单次工具调用关联的文件元信息。
 * 本期只保留文件账本事实，不读取文件正文。
 */
```

- [ ] **Step 5: 再次运行测试验证通过**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=SessionContextMemoryServiceTest -DskipTests=false`
Expected: PASS 或进入 service 未实现失败。

- [ ] **Step 6: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/memory ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryServiceTest.java
git commit -m "feat: add session context memory models"
```

### Task 3: 实现单会话记忆组装服务

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/SessionContextMemoryService.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/SessionContextMemoryServiceImpl.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryServiceTest.java`

- [ ] **Step 1: 写服务层失败测试，覆盖 run -> cycle -> tool -> artifact 组装**

```java
@Test
public void shouldBuildSessionMemoryFromLedgerFacts() {
    // given
    // session 下两次历史 run，第二次 run 有两个 cycle

    // when
    // buildHistoryDialogue(sessionId, currentRequestId)

    // then
    // 输出中包含完整 Thought、Tool Calls、Files 段
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=SessionContextMemoryServiceTest -DskipTests=false`
Expected: FAIL，提示 `SessionContextMemoryService` 未实现。

- [ ] **Step 3: 定义服务接口**

```java
public interface SessionContextMemoryService {

    String buildHistoryDialogue(String sessionId, String currentRequestId);
}
```

- [ ] **Step 4: 实现查询与组装逻辑**

```java
public String buildHistoryDialogue(String sessionId, String currentRequestId) {
    if (StringUtils.isBlank(sessionId)) {
        return "";
    }
    List<DialogueRunView> runs = executionLedgerQueryService.querySessionRuns(sessionId);
    // 过滤当前 requestId
    // 批量查询 llm/tool/artifact
    // 组装 RunHistoryMemory -> ReactCycleMemory -> ToolCallMemory -> FileArtifactMemory
    // 格式化为 historyDialogue
}
```

- [ ] **Step 5: 明确组装顺序并加中文注释**

```java
// 记忆锚点是 llmInvocation，而不是 run。
// 一个 llmInvocation 对应一次完整的 ReAct 循环，工具调用只是该循环下的动作明细。
```

- [ ] **Step 6: 实现 `historyDialogue` 格式化**

```java
builder.append("## 单会话历史记忆\n\n");
for (RunHistoryMemory run : memory.getRuns()) {
    builder.append("### Run ").append(StringUtils.defaultString(run.getRequestId())).append("\n");
    // Session Input Files
    // ReAct Cycle
    // Thought
    // Tool Calls
}
```

- [ ] **Step 6.1: 为可空字段补齐 `null` 防护**

```java
private String valueOrEmpty(String value) {
    return StringUtils.defaultString(value);
}

builder.append("- fileName=").append(valueOrEmpty(file.getFileName()))
        .append(", mimeType=").append(valueOrEmpty(file.getMimeType()))
        .append(", storageKey=").append(valueOrEmpty(file.getStorageKey()))
        .append(", downloadUrl=").append(valueOrEmpty(file.getDownloadUrl()))
        .append(", previewUrl=").append(valueOrEmpty(file.getPreviewUrl()));
```

- [ ] **Step 7: 运行测试验证通过**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=SessionContextMemoryServiceTest -DskipTests=false`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/SessionContextMemoryService.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/SessionContextMemoryServiceImpl.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryServiceTest.java
git commit -m "feat: build history dialogue from session execution ledger"
```

### Task 4: 在执行策略入口注入单会话记忆

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/ReactAgentExecuteStrategy.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/PlanSolveAgentExecuteStrategy.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryIntegrationTest.java`

- [ ] **Step 1: 写入口注入失败测试**

```java
@Test
public void shouldInjectHistoryDialogueBeforeReactExecution() {
    // given
    // request.sessionId 存在历史 run

    // when
    // execute strategy

    // then
    // 传入 RootNode 的 request.historyDialogue 非空
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=SessionContextMemoryIntegrationTest -DskipTests=false`
Expected: FAIL，提示 `historyDialogue` 仍为空。

- [ ] **Step 3: 在 React 执行策略接入记忆服务**

```java
@Resource
private SessionContextMemoryService sessionContextMemoryService;

@Override
public void execute(AgentRequest request, SseEmitter emitter) throws Exception {
    request.setHistoryDialogue(sessionContextMemoryService.buildHistoryDialogue(
            request.getSessionId(),
            request.getRequestId()
    ));
    applyOutputStyle(request);
    doExecute(request, emitter);
}
```

- [ ] **Step 4: 在 PlanSolve 执行策略同步接入**

```java
@Resource
private SessionContextMemoryService sessionContextMemoryService;

@Override
public void execute(AgentRequest request, SseEmitter emitter) throws Exception {
    request.setHistoryDialogue(sessionContextMemoryService.buildHistoryDialogue(
            request.getSessionId(),
            request.getRequestId()
    ));
    // 原有执行逻辑不变
}
```

- [ ] **Step 5: 为接入点补中文注释**

```java
// 每次进入执行策略前，都先用同一 session 下的历史账本重建 historyDialogue，
// 再交给后续 AgentContext 注入链路复用。
```

- [ ] **Step 6: 在实现说明中明确非本期覆盖范围**

```java
// AutoAgent 当前 execute(AgentRequest, SseEmitter) 为空实现，且 auto 链已有独立 chat memory 机制；
// workflow/FlowAgentExecuteStrategy 使用 Spring AI CHAT_MEMORY_CONVERSATION_ID_KEY，不走 historyDialogue 注入。
// 因此本期只在 React / PlanSolve 两条链路接入 SessionContextMemoryService。
```

- [ ] **Step 7: 运行测试验证通过**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=SessionContextMemoryIntegrationTest -DskipTests=false`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/ReactAgentExecuteStrategy.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/PlanSolveAgentExecuteStrategy.java ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryIntegrationTest.java
git commit -m "feat: inject session context memory before agent execution"
```

### Task 5: 补齐端到端回归测试

**Files:**
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryIntegrationTest.java`

- [ ] **Step 1: 新增 fixture，支持构造多 run 多 cycle 历史**

```java
public static void seedSessionHistoryCycles(
        LedgerTestContext ctx,
        String sessionId,
        String requestId,
        List<CycleSeed> cycles,
        List<FileSeed> inputFiles
) {
    // 创建 run
    // 创建 llm invocation
    // 创建 tool invocation
    // 创建 artifact
}
```

- [ ] **Step 2: 为 React 模式补回归测试**

```java
@Test
public void shouldReusePreviousReactCyclesAsHistoryDialogue() {
    // given
    // 先写入一次历史 react run

    // when
    // 发起同 session 第二次请求

    // then
    // 新请求构建出的 historyDialogue 包含上次完整 thought/tool/files
}
```

- [ ] **Step 3: 为 PlanSolve 模式补回归测试**

```java
@Test
public void shouldReusePreviousPlanSolveCyclesAsHistoryDialogue() {
    // given
    // 同 session 下已有历史 plansolve run

    // when
    // 再次进入 plansolve

    // then
    // historyDialogue 按 llmInvocation cycle 顺序完整注入
}
```

- [ ] **Step 4: 运行目标测试**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=SessionContextMemoryIntegrationTest,ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest -DskipTests=false`
Expected: PASS

- [ ] **Step 5: 运行更大范围回归**

Run: `mvn test -pl ai-agent-station-study-app -DskipTests=false`
Expected: PASS；若已有外部依赖测试被 surefire 排除，则不影响本次结果判定。

- [ ] **Step 6: Commit**

```bash
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/SessionContextMemoryIntegrationTest.java
git commit -m "test: cover session context memory integration"
```

## Self-Review

- **Spec coverage:** 本计划覆盖了记忆源三表接入、按 `llm_invocation` 组装 cycle、只读取 artifact 元信息、不做压缩裁剪、策略入口注入、React 与 PlanSolve 双链路接入、回归测试。
- **Placeholder scan:** 已避免使用 TBD/TODO/“后续补充”之类占位表述；所有新增接口、SQL、服务与测试文件均给出明确路径。`seedSessionHistoryCycles(...)` 已补成明确参数签名。
- **Type consistency:** 计划统一使用 `SessionContextMemoryService`、`ReactCycleMemory`、`ToolCallMemory`、`FileArtifactMemory` 这些固定命名；记忆锚点统一为 `llmInvocationId`。

## 实现细节提示

- `historyDialogue` 格式化时，`downloadUrl`、`previewUrl`、`mimeType`、`storageKey` 等可空字段需要统一走 `StringUtils.defaultString(...)`，避免把 `null` 字面量拼进 prompt。
- `IArtifactLedgerDao.queryByRunIds(...)` 与现有 mapper SQL 已存在，本期直接复用；新增接口只包括 `queryByToolInvocationIds(...)` 与 `queryInputArtifactsByRunIds(...)`。
- `IArtifactLedgerDao.queryByRunIds(...)` 现有 SQL 按 `run_id DESC` 返回，但单会话记忆组装顺序必须以 `querySessionRuns(sessionId)` 返回的 run 序为准，不能直接依赖 artifact SQL 返回顺序。

## 执行提示

- 当前仓库存在较多未提交改动，实施前不要覆盖用户已有修改。
- 本计划建议按 TDD 顺序执行；若某一步测试命令受现有脏工作区影响，需要先缩小测试范围到新增测试类。
- 本期不引入任何压缩、裁剪、摘要规则；若后续需要优化 token 成本，应单独起变更。
