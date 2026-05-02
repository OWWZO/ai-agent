# Conversation History Projector Replay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在当前“工具分表 + artifact 账本”架构下补齐会话历史恢复，并引入独立会话主表，让前端刷新或重进后能够按 `sessionId` 恢复完整会话轨迹，同时沉淀会话级统计与摘要信息。

**Architecture:** 新增 `ai_agent_dialogue_session` 作为“一个 session 一行”的主表，负责会话级元数据与低成本统计；`ai_agent_dialogue_run` 明确退回为“会话里一次请求”的执行账本，继续关联 `llm/tool/artifact` 明细。rich tool 的完整终态仍然只存 `ai_agent_tool_output_*` 分表，历史恢复统一走 `ExecutionLedgerQueryService -> ReplayFactBundle -> ReplayProjector + ToolInvocationProjectorRegistry`；LLM 历史语义不再新增 `semantic_kind`，而是直接基于现有 `agent_name` 约定判断 `TOOL_THOUGHT / PLAN_THOUGHT / FINAL_ANSWER`。

**Tech Stack:** Java 17, Spring Boot 3.4.3, MyBatis / Mapper XML, MySQL 8, React 19, TypeScript 5, Vitest

---

## 当前基线（2026-05-02）

### 已确认的真实链路

1. 当前 Web UI 主 SSE 入口是 `/web/api/v1/gpt/queryAgentStreamIncr`，请求先进入 `GptProcessServiceImpl`，再由 `MultiAgentServiceImpl` 转发到 `http://127.0.0.1:8100/AutoAgent`。
2. 下游真实执行链仍然是 React / PlanSolve 主链路：`ExecutionLedgerRunSupport.initializeRun(...)` 创建 run，`LLM` 记录 `llm_invocation`，`BaseAgent` 记录 `tool_invocation` 与 `artifact`。
3. rich tool 已经不是把完整结构塞回 `ai_agent_tool_invocation`。当前完整终态分别落在：
   - `ai_agent_tool_output_deep_search`
   - `ai_agent_tool_output_file_tool`
   - `ai_agent_tool_output_code_interpreter`
   - `ai_agent_tool_output_report_tool`
   - `ai_agent_tool_output_data_analysis`
   - `ai_agent_tool_output_multimodal_agent`
   - `ai_agent_tool_output_image_generation`
   - `ai_agent_tool_output_script_runner`
4. `AgentExecutionRecorderImpl.finishToolInvocation(...)` 已经会在 rich tool 成功或失败时调用 `ToolOutputWriter.write(...)`，按 `ToolStructuredOutput` 类型写入各自分表。
5. `ExecutionLedgerQueryServiceImpl` 已经会通过 `ToolOutputReader.readByInvocationId(...)` 回填 `ToolInvocationView.structuredOutput`。
6. `ReplayProjector + ToolInvocationProjectorRegistry + per-tool projector` 已存在，能够把 `ToolInvocationView.structuredOutput + artifact` 投影成历史 `eventData`。
7. 按当前项目约定，`ai_agent_llm_invocation.agent_name` 已经能够区分 `TOOL_THOUGHT / PLAN_THOUGHT / FINAL_ANSWER`，因此本期不再为此增加新的 `semantic_kind` 字段。

### 当前真正未闭环的点

1. 现在还没有独立的 `ai_agent_dialogue_session` 主表，`session` 与 `run` 的边界没有在账本层沉淀清楚。
2. `ai_agent_dialogue_run` 目前虽然带 `session_id`，但仍然更像“会话头表”在被间接使用，没有形成一条清晰的“session -> 多个 run”恢复路径。
3. 实时路径仍然走 `BaseAgentResponseHandler` 里的硬编码 `switch`，没有和 `ReplayProjector` 共用同一套语义。
4. 历史读侧虽然能查单个 run 细节，但还没有基于“会话主表”的 session list / detail 服务与接口。
5. 前端当前只保留单会话运行态，没有把后端 replay frames 重新 hydrate 成 `ConversationHistory` 的入口。

---

## 设计结论

### 事实源职责

1. `ai_agent_dialogue_session`
   一行代表一个会话，负责保存会话级稳定元数据与低成本统计，例如 `title / output_style / deep_think / role_agent_id / latest_request_id / run_count / finished_run_count / failed_run_count / started_at / last_active_at`。
2. `ai_agent_dialogue_run`
   一行代表会话里的一次请求，负责保存请求级状态、提问、总结、耗时与时间线锚点；历史回放仍然以 run 为最小重建单元。
3. `ai_agent_llm_invocation`
   存 LLM 调用顺序、完整 `response_text` 与既有 `agent_name`；历史语义由 `agent_name` 推导，不新增 `semantic_kind`。
4. `ai_agent_tool_invocation`
   只存工具调度账本：`input_json / llm_observation / status / timing / dispatch_index`，不承担 rich tool 完整结构。
5. `ai_agent_tool_output_*`
   存每个 rich tool 的稳定结构化终态，是工具展示事实的唯一来源。
6. `ai_agent_artifact`
   存稳定文件引用，任何 projector 都必须以这里的 URL / storageKey 为准，不重新发明文件来源。

### LLM 语义判定策略

1. 本期不新增 `LlmSemanticKind`、不改 `llm_invocation` 表结构。
2. 历史回放阶段统一在 `ReplayProjector` 内部集中封装 `resolveLlmEventKindByAgentName(...)`。
3. 所有“`agent_name` 如何映射为 `tool_thought / plan_thought / result`”的规则只允许出现在这一处，避免实时和历史各自维护一套分支。
4. 如果后续 `agent_name` 约定发生变化，也只需要改这一处映射，不需要再做数据库演进。

### 会话建模策略

1. 本期新增 `ai_agent_dialogue_session` 主表，明确“一个 session 一行；一个 session 对应多条 run”。
2. session 主表先只落低成本、稳定价值高的统计，例如 `run_count / finished_run_count / failed_run_count`；高成本跨账本统计不在本期强行写放大。
3. 历史接口首版按 `sessionId` 查询，不新增 `deviceId` 维度，也不把 device 绑定逻辑塞进账本模型。
4. 如果后续确实需要做人、设备、租户级归属，再在 `dialogue_session` 上补 owner 相关字段与索引，不影响本期主线。

### 历史回放策略

1. rich tool 历史恢复统一读取 `ToolInvocationView.structuredOutput`，不再要求任何工具返回 `output_json` 字符串。
2. `ReplayProjector.projectHistory(...)` 继续以单个 run 为粒度消费：
   - `DialogueRunView`
   - `LlmInvocationView`
   - `ToolInvocationView`
   - `ArtifactView`
3. `ConversationHistoryReplayService` 负责以 session 为外层聚合：先查会话主表，再查该 session 下的所有 run，最后逐个 run 调 `ReplayProjector.projectHistory(...)`。
4. 如果某个 run 没有稳定的最终回答事件，则由 `run.finalSummaryText` 合成一个最终 `result` 事件，保证前端能恢复结论区。

### 首版前端范围

1. 本期不重做完整历史侧边栏。
2. 首页先恢复“根据当前 `sessionId` 自动重放完整会话”的能力，不默认回退到别的 session，避免在没有归属维度时误切到无关会话。
3. 后端仍提供 session list 接口，供后续 UI 扩展、调试和人工选择使用。

---

## 已落地能力（不要重复实现）

1. `ToolStructuredOutput` 及各工具强类型输出模型已经存在。
2. `ToolOutputWriterImpl / ToolOutputReaderImpl` 已经具备 rich tool 分表写入与读取能力。
3. `ToolInvocationProjectorRegistry` 与 rich tool projector 已经具备历史工具事件投影能力。
4. `ExecutionLedgerQueryServiceImpl` 已经能把 `structuredOutput` 挂回 `ToolInvocationView`。
5. 因此本计划**不再包含**“重新设计 output_json builder”“把 rich tool 结构重新塞回 `tool_invocation`”或“为 LLM 再补一列 `semantic_kind`”之类任务。

---

## 文件结构映射

### 新建文件

| 文件路径 | 职责 |
| --- | --- |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueSession.java` | 会话主表实体 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/DialogueSessionUpsertRecord.java` | 会话主表写侧 upsert 参数 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/DialogueSessionView.java` | 会话主表读侧视图 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueSessionLedgerDao.java` | 会话主表 DAO |
| `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_session_ledger_mapper.xml` | 会话主表 Mapper XML |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/HistoryReplayPrinter.java` | 把 `ProjectedReplayEvent` 包装成前端现有消费的 `GptProcessResult` 列表 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ConversationHistoryReplayService.java` | 基于 session 主表组装历史详情与 replay frames |
| `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationHistoryController.java` | 对外暴露 session list / detail 接口 |
| `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationSessionRespVO.java` | 会话列表返回对象 |
| `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationHistoryDetailRespVO.java` | 单会话历史详情返回对象 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java` | 会话历史接口回归 |
| `ui/src/utils/conversationHistory.ts` | 把后端 replay frames 还原成前端 `ConversationHistory` |
| `ui/src/utils/conversationHistory.test.ts` | 前端历史恢复单测 |

### 修改文件

| 文件路径 | 修改内容 |
| --- | --- |
| `ai-agent-station-study-app/src/main/resources/db/schema.sql` | 新增 `ai_agent_dialogue_session` 表，并校正 run / session 的职责说明 |
| `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml` | 补 session 下 run 列表查询 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerRunSupport.java` | run 创建时 upsert 会话主表并维护会话头信息 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java` | run 完成时维护会话统计与最新总结 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerQueryService.java` | 增加 session 查询能力 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java` | 组装 session 级查询结果与 replay 事实 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueRunLedgerDao.java` | 增加按 `sessionId` 查询 runs |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/replay/ReplayFactBundle.java` | 保持 run 粒度事实聚合，并显式承接历史重放入参 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java` | 同时支持 realtime / history 两种投影入口，并集中做 `agent_name` 语义映射 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/BaseAgentResponseHandler.java` | 改为委托共享 `ReplayProjector` |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/ReactAgentResponseHandler.java` | 透传 realtime projector |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/PlanSolveAgentResponseHandler.java` | 透传 realtime projector |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java` | 支持会话主表 fixture |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java` | 增加 session 查询断言 |
| `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorTest.java` | 增加 `agent_name` 语义映射与 realtime/history 同构断言 |
| `ui/src/services/agentConversation.ts` | 新增会话历史接口 |
| `ui/src/types/chat.ts` | 补历史详情类型与 replay frames 类型 |
| `ui/src/pages/Home/index.tsx` | 页面初始化时按当前 `sessionId` 恢复完整历史 |

---

## Task 1: 新增会话主表，并在写侧维护会话头与会话统计

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueSession.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/DialogueSessionUpsertRecord.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/DialogueSessionView.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueSessionLedgerDao.java`
- Create: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_session_ledger_mapper.xml`
- Modify: `ai-agent-station-study-app/src/main/resources/db/schema.sql`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerRunSupport.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java`

- [ ] **Step 1: 先写失败测试，锁定“同一 session 会生成一条主表记录，并累计多次 run 统计”**

```java
@Test
public void shouldCreateDialogueSessionAndAggregateRunStats() {
    ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();

    Long firstRunId = ctx.recorder.createRun(DialogueRunStartRecord.builder()
            .runUid("req-history-001")
            .requestId("req-history-001")
            .sessionId("session-history-001")
            .entryAgent(ExecutionLedgerConstants.ENTRY_AGENT_REACT)
            .queryText("先分析项目风险")
            .build());
    ctx.recorder.finishRun(firstRunId, "SUCCESS", "第一轮总结");

    Long secondRunId = ctx.recorder.createRun(DialogueRunStartRecord.builder()
            .runUid("req-history-002")
            .requestId("req-history-002")
            .sessionId("session-history-001")
            .entryAgent(ExecutionLedgerConstants.ENTRY_AGENT_REACT)
            .queryText("继续补充方案")
            .build());
    ctx.recorder.finishRun(secondRunId, "FAILED", "第二轮失败");

    DialogueSessionView session = ctx.queryService.querySession("session-history-001");
    Assert.assertEquals("session-history-001", session.getSessionId());
    Assert.assertEquals(Integer.valueOf(2), session.getRunCount());
    Assert.assertEquals(Integer.valueOf(1), session.getFinishedRunCount());
    Assert.assertEquals(Integer.valueOf(1), session.getFailedRunCount());
    Assert.assertEquals("req-history-002", session.getLatestRequestId());
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ExecutionLedgerQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: 编译失败或断言失败，提示缺少 `DialogueSession` 相关实体、DAO、查询接口或会话统计字段。

- [ ] **Step 3: 新增 `ai_agent_dialogue_session` 表与 upsert 写模型**

```sql
CREATE TABLE IF NOT EXISTS ai_agent_dialogue_session (
    id                  BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id          VARCHAR(64)    NOT NULL COMMENT '会话ID',
    title               VARCHAR(255)   NULL COMMENT '会话标题',
    output_style        VARCHAR(32)    NULL COMMENT 'chat/html/docs/table/dataAgent',
    deep_think          TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否深度模式',
    role_agent_id       VARCHAR(64)    NULL COMMENT '角色ID',
    status              VARCHAR(32)    NOT NULL DEFAULT 'RUNNING' COMMENT '会话状态',
    latest_request_id   VARCHAR(64)    NULL COMMENT '最近一次请求ID',
    latest_query_text   VARCHAR(1000)  NULL COMMENT '最近一次提问',
    latest_summary_text LONGTEXT       NULL COMMENT '最近一次总结',
    run_count           INT            NOT NULL DEFAULT 0 COMMENT '会话请求次数',
    finished_run_count  INT            NOT NULL DEFAULT 0 COMMENT '成功次数',
    failed_run_count    INT            NOT NULL DEFAULT 0 COMMENT '失败次数',
    started_at          DATETIME       NULL COMMENT '首次开始时间',
    last_active_at      DATETIME       NULL COMMENT '最近活跃时间',
    create_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dialogue_session (session_id, deleted),
    KEY idx_dialogue_session_last_active (last_active_at, deleted)
) COMMENT='AI Agent 会话主表';
```

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogueSessionUpsertRecord {
    private String sessionId;
    private String title;
    private String outputStyle;
    private Boolean deepThink;
    private String roleAgentId;
    private String status;
    private String latestRequestId;
    private String latestQueryText;
    private String latestSummaryText;
    private Integer runCountDelta;
    private Integer finishedRunCountDelta;
    private Integer failedRunCountDelta;
    private LocalDateTime startedAt;
    private LocalDateTime lastActiveAt;
}
```

```xml
<insert id="upsertSession">
    INSERT INTO ai_agent_dialogue_session (
        session_id, title, output_style, deep_think, role_agent_id, status,
        latest_request_id, latest_query_text, latest_summary_text,
        run_count, finished_run_count, failed_run_count,
        started_at, last_active_at, create_time, update_time, deleted
    ) VALUES (
        #{sessionId}, #{title}, #{outputStyle}, #{deepThink}, #{roleAgentId}, #{status},
        #{latestRequestId}, #{latestQueryText}, #{latestSummaryText},
        #{runCountDelta}, #{finishedRunCountDelta}, #{failedRunCountDelta},
        #{startedAt}, #{lastActiveAt}, NOW(), NOW(), 0
    )
    ON DUPLICATE KEY UPDATE
        title = COALESCE(#{title}, title),
        output_style = COALESCE(#{outputStyle}, output_style),
        deep_think = COALESCE(#{deepThink}, deep_think),
        role_agent_id = COALESCE(#{roleAgentId}, role_agent_id),
        status = COALESCE(#{status}, status),
        latest_request_id = COALESCE(#{latestRequestId}, latest_request_id),
        latest_query_text = COALESCE(#{latestQueryText}, latest_query_text),
        latest_summary_text = COALESCE(#{latestSummaryText}, latest_summary_text),
        run_count = run_count + COALESCE(#{runCountDelta}, 0),
        finished_run_count = finished_run_count + COALESCE(#{finishedRunCountDelta}, 0),
        failed_run_count = failed_run_count + COALESCE(#{failedRunCountDelta}, 0),
        started_at = COALESCE(started_at, #{startedAt}),
        last_active_at = COALESCE(#{lastActiveAt}, last_active_at),
        update_time = NOW();
</insert>
```

- [ ] **Step 4: 在真实写链上维护 session 主表**

```java
Long runId = recorder.createRun(DialogueRunStartRecord.builder()
        .runUid(request.getRequestId())
        .requestId(request.getRequestId())
        .sessionId(request.getSessionId())
        .entryAgent(entryAgent)
        .queryText(request.getQuery())
        .build());

dialogueSessionLedgerDao.upsertSession(DialogueSessionUpsertRecord.builder()
        .sessionId(request.getSessionId())
        .title(buildSessionTitle(request))
        .outputStyle(request.getOutputStyle())
        .deepThink(Boolean.TRUE.equals(agentContext.getDeepThink()))
        .roleAgentId(request.getAiAgentId())
        .status("RUNNING")
        .latestRequestId(request.getRequestId())
        .latestQueryText(request.getQuery())
        .runCountDelta(1)
        .startedAt(LocalDateTime.now())
        .lastActiveAt(LocalDateTime.now())
        .build());
```

```java
dialogueSessionLedgerDao.upsertSession(DialogueSessionUpsertRecord.builder()
        .sessionId(run.getSessionId())
        .status(runFinishedSuccess ? "COMPLETED" : "FAILED")
        .latestRequestId(run.getRequestId())
        .latestSummaryText(finalSummaryText)
        .finishedRunCountDelta(runFinishedSuccess ? 1 : 0)
        .failedRunCountDelta(runFinishedSuccess ? 0 : 1)
        .lastActiveAt(LocalDateTime.now())
        .build());
```

- [ ] **Step 5: 重新运行聚焦测试并提交**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ExecutionLedgerQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

```bash
git add ai-agent-station-study-app/src/main/resources/db/schema.sql
git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_session_ledger_mapper.xml
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DialogueSession.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/DialogueSessionUpsertRecord.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/DialogueSessionView.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueSessionLedgerDao.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerRunSupport.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerFixtureFactory.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java
git commit -m "feat: add dialogue session ledger"
```

---

## Task 2: 让实时与历史共用同一套 ReplayProjector 语义，并基于 `agent_name` 判定 LLM 历史类型

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/replay/ReplayFactBundle.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/BaseAgentResponseHandler.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/ReactAgentResponseHandler.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/PlanSolveAgentResponseHandler.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorTest.java`

- [ ] **Step 1: 先写失败测试，锁定 realtime / history 必须投出同构 `eventData`，且历史语义来自 `agent_name`**

```java
@Test
public void shouldProjectRealtimeAndHistoryWithSameOuterEventShape() {
    ReplayProjector projector = new ReplayProjector(registry);
    EventResult state = new EventResult();

    ProjectedReplayEvent realtime = projector.projectRealtime(state, AgentResponse.builder()
            .messageId("msg-thought-001")
            .messageType("tool_thought")
            .toolThought("先搜资料")
            .isFinal(true)
            .build());

    Assert.assertEquals("task", realtime.getMessageType());
    Assert.assertEquals("tool_thought", nestedResultMap(realtime).get("messageType"));
}
```

```java
@Test
public void shouldProjectHistoryByAgentNameAndToolStructuredOutput() {
    ReplayFactBundle bundle = ReplayFactBundle.builder()
            .run(DialogueRunView.builder()
                    .requestId("req-history-001")
                    .finalSummaryText("最终总结")
                    .build())
            .llmInvocations(List.of(
                    LlmInvocationView.builder()
                            .id(1L)
                            .runId(10L)
                            .agentName("TOOL_THOUGHT")
                            .responseText("先搜资料")
                            .invocationSeq(1)
                            .build()))
            .toolInvocations(List.of(
                    ToolInvocationView.builder()
                            .id(11L)
                            .llmInvocationId(1L)
                            .toolName("file_tool")
                            .toolCallId("tool-call-001")
                            .structuredOutput(FileToolOutput.builder().command("get").build())
                            .build()))
            .artifacts(List.of())
            .build();

    List<ProjectedReplayEvent> events = projector.projectHistory(bundle);
    Assert.assertEquals(2, events.size());
    Assert.assertEquals("tool_thought", nestedResultMap(events.get(0)).get("messageType"));
    Assert.assertEquals("file", nestedResultMap(events.get(1)).get("messageType"));
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ReplayProjectorTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL，当前 `ReplayProjector` 还没有把 `agent_name` 作为历史语义判定来源，realtime / history 也尚未完全收敛。

- [ ] **Step 3: 在 `ReplayProjector` 内部集中封装 `agent_name` -> 历史语义映射**

```java
private String resolveHistoryMessageType(LlmInvocationView llm) {
    return switch (StringUtils.defaultString(llm.getAgentName())) {
        case "TOOL_THOUGHT" -> "tool_thought";
        case "PLAN_THOUGHT" -> "plan_thought";
        case "FINAL_ANSWER" -> "result";
        default -> "other";
    };
}
```

```java
public List<ProjectedReplayEvent> projectHistory(ReplayFactBundle bundle) {
    EventResult state = new EventResult();
    List<ProjectedReplayEvent> events = new ArrayList<>();
    Map<Long, List<ToolInvocationView>> toolsByLlmId = groupToolsByLlm(bundle.getToolInvocations());
    Map<Long, List<ArtifactView>> artifactsByToolId = groupArtifactsByTool(bundle.getArtifacts());

    for (LlmInvocationView llm : bundle.getLlmInvocations()) {
        appendLlmEventByAgentName(events, state, llm);
        for (ToolInvocationView invocation : toolsByLlmId.getOrDefault(llm.getId(), List.of())) {
            events.addAll(toolInvocationProjectorRegistry.project(
                    invocation,
                    artifactsByToolId.getOrDefault(invocation.getId(), List.of()),
                    state
            ));
        }
    }

    appendRunSummaryFallback(events, state, bundle.getRun());
    return events;
}
```

- [ ] **Step 4: 让实时 handler 只负责包装 `GptProcessResult`，业务语义全部委托 projector**

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

- [ ] **Step 5: 跑 projector 回归并提交**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ReplayProjectorTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/replay/ReplayFactBundle.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ReplayProjector.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/BaseAgentResponseHandler.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/ReactAgentResponseHandler.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/handler/PlanSolveAgentResponseHandler.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorTest.java
git commit -m "refactor: unify realtime and history replay projection"
```

---

## Task 3: 新增基于会话主表的历史服务与接口

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/HistoryReplayPrinter.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ConversationHistoryReplayService.java`
- Create: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationHistoryController.java`
- Create: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationSessionRespVO.java`
- Create: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationHistoryDetailRespVO.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerQueryService.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueRunLedgerDao.java`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_session_ledger_mapper.xml`
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java`

- [ ] **Step 1: 先写失败测试，锁定“按 sessionId 可返回会话统计、多轮 run 与 replay frames”**

```java
@Test
public void shouldReturnSessionDetailWithStatsAndReplayFrames() {
    ConversationHistoryDetailRespVO detail = controller.detail("session-history-001");
    Assert.assertEquals("session-history-001", detail.getSessionId());
    Assert.assertEquals(Integer.valueOf(2), detail.getRunCount());
    Assert.assertEquals(Integer.valueOf(1), detail.getFinishedRunCount());
    Assert.assertEquals(Integer.valueOf(1), detail.getFailedRunCount());
    Assert.assertFalse(detail.getRuns().isEmpty());
    Assert.assertNotNull(detail.getRuns().get(0).getReplayFrames().get(0).getResultMap().get("eventData"));
}
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ConversationHistoryControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL，缺少 controller / service / session 查询接口或会话主表查询 SQL。

- [ ] **Step 3: 扩展查询服务，形成“先查 session，再查 runs”的历史读取入口**

```java
public interface ExecutionLedgerQueryService {
    DialogueSessionView querySession(String sessionId);
    List<DialogueSessionView> queryRecentSessions(int limit);
    List<DialogueRunView> querySessionRuns(String sessionId);
    ExecutionRunDetail queryRunDetail(String requestId);
    List<ToolInvocationView> queryRecentToolInvocations(String toolName, int limit);
}
```

```xml
<select id="queryBySessionId" resultMap="DialogueSessionMap">
    SELECT *
    FROM ai_agent_dialogue_session
    WHERE deleted = 0
      AND session_id = #{sessionId}
    LIMIT 1
</select>

<select id="queryRecentSessions" resultMap="DialogueSessionMap">
    SELECT *
    FROM ai_agent_dialogue_session
    WHERE deleted = 0
    ORDER BY last_active_at DESC, id DESC
    LIMIT #{limit}
</select>

<select id="queryBySessionIdOrderByCreateTime" resultMap="DialogueRunMap">
    SELECT *
    FROM ai_agent_dialogue_run
    WHERE deleted = 0
      AND session_id = #{sessionId}
    ORDER BY create_time ASC, id ASC
</select>
```

- [ ] **Step 4: 新建历史回放服务与 controller**

```java
public ConversationHistoryDetailRespVO querySessionDetail(String sessionId) {
    DialogueSessionView session = executionLedgerQueryService.querySession(sessionId);
    List<DialogueRunView> runs = executionLedgerQueryService.querySessionRuns(sessionId);

    List<ConversationHistoryDetailRespVO.RunDetail> runDetails = runs.stream()
            .map(run -> {
                ExecutionRunDetail detail = executionLedgerQueryService.queryRunDetail(run.getRequestId());
                ReplayFactBundle bundle = ReplayFactBundle.builder()
                        .run(detail.getRun())
                        .llmInvocations(detail.getLlmInvocations())
                        .toolInvocations(detail.getToolInvocations())
                        .artifacts(detail.getArtifacts())
                        .build();
                List<ProjectedReplayEvent> events = replayProjector.projectHistory(bundle);
                return buildRunDetail(run, historyReplayPrinter.print(run, events));
            })
            .toList();

    return buildSessionDetail(session, runDetails);
}
```

```java
@GetMapping("/api/agent/conversation/sessions")
public Response<List<ConversationSessionRespVO>> list(@RequestParam(defaultValue = "20") Integer limit) {
    return Response.<List<ConversationSessionRespVO>>builder()
            .code(ResponseCode.SUCCESS.getCode())
            .info("ok")
            .data(conversationHistoryReplayService.listSessions(limit))
            .build();
}

@GetMapping("/api/agent/conversation/sessions/{sessionId}")
public Response<ConversationHistoryDetailRespVO> detail(@PathVariable String sessionId) {
    return Response.<ConversationHistoryDetailRespVO>builder()
            .code(ResponseCode.SUCCESS.getCode())
            .info("ok")
            .data(conversationHistoryReplayService.querySessionDetail(sessionId))
            .build();
}
```

- [ ] **Step 5: 跑接口测试并提交**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ConversationHistoryControllerTest,ExecutionLedgerQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/HistoryReplayPrinter.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/replay/ConversationHistoryReplayService.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/ExecutionLedgerQueryService.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ExecutionLedgerQueryServiceImpl.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDialogueRunLedgerDao.java
git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_run_ledger_mapper.xml
git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/dialogue_session_ledger_mapper.xml
git add ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationHistoryController.java
git add ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationSessionRespVO.java
git add ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationHistoryDetailRespVO.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ConversationHistoryControllerTest.java
git commit -m "feat: add session based conversation history replay endpoints"
```

---

## Task 4: 恢复前端会话 hydrate，但首版只接当前 `sessionId`

**Files:**
- Modify: `ui/src/services/agentConversation.ts`
- Modify: `ui/src/types/chat.ts`
- Create: `ui/src/utils/conversationHistory.ts`
- Create: `ui/src/utils/conversationHistory.test.ts`
- Modify: `ui/src/pages/Home/index.tsx`

- [ ] **Step 1: 先写前端失败测试，锁定“replay frames -> ConversationHistory”恢复行为**

```ts
import { describe, expect, it } from "vitest";
import { hydrateConversationFromReplayFrames } from "@/utils/conversationHistory";

describe("hydrateConversationFromReplayFrames", () => {
  it("rebuilds chat list from replay eventData", () => {
    const conversation = hydrateConversationFromReplayFrames({
      sessionId: "session-history-001",
      title: "项目风险",
      outputStyle: "chat",
      deepThink: false,
      runCount: 2,
      finishedRunCount: 1,
      failedRunCount: 1,
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

- [ ] **Step 2: 运行测试并确认当前失败**

Run:

```bash
cd ui && npm run test -- conversationHistory.test.ts
```

Expected: FAIL，缺少 `hydrateConversationFromReplayFrames` 或历史详情类型不匹配。

- [ ] **Step 3: 增加历史接口与 hydrate helper**

```ts
export const conversationHistoryApi = {
  listSessions: (limit = 20) =>
    api.get<ConversationSessionItem[]>(`/api/agent/conversation/sessions?limit=${limit}`),
  getSessionDetail: (sessionId: string) =>
    api.get<ConversationHistoryDetail>(`/api/agent/conversation/sessions/${sessionId}`),
};
```

```ts
export function hydrateConversationFromReplayFrames(detail: ConversationHistoryDetail): CHAT.ConversationHistory {
  const chatList: CHAT.ChatItem[] = [];

  detail.runs.forEach((run) => {
    let currentChat = { multiAgent: { tasks: [] } } as any;
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
    chatList.push({ ...currentChat, requestId: run.requestId, sessionId: detail.sessionId });
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

- [ ] **Step 4: 在 Home 页面恢复“按当前 sessionId 拉历史”的初始化逻辑**

```ts
useEffect(() => {
  let disposed = false;

  if (!currentConversation.sessionId) {
    return;
  }

  conversationHistoryApi
    .getSessionDetail(currentConversation.sessionId)
    .then((detail) => {
      if (!disposed && detail) {
        setCurrentConversation(hydrateConversationFromReplayFrames(detail));
      }
    })
    .catch(() => {
      // 当前 session 没有历史时保持现状，不自动切换到其他会话
    });

  return () => {
    disposed = true;
  };
}, [currentConversation.sessionId]);
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
git commit -m "feat: restore conversation history hydration in frontend"
```

---

## Task 5: 做完整回归，确认 session 主表、structured output 重放与实时语义保持一致

**Files:**
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ExecutionLedgerQueryServiceTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java`
- Modify: `ui/src/utils/chat.test.ts`
- Modify: `ui/src/utils/conversationHistory.test.ts`

- [ ] **Step 1: 增加“历史详情必须带上会话统计和 structured replay frames”测试**

```java
@Test
public void shouldExposeSessionSummaryAndStructuredReplayFrames() {
    ConversationHistoryDetailRespVO detail = service.querySessionDetail("session-001");
    Assert.assertEquals(Integer.valueOf(2), detail.getRunCount());
    Assert.assertEquals(Integer.valueOf(1), detail.getFinishedRunCount());
    Assert.assertEquals(Integer.valueOf(1), detail.getFailedRunCount());
    Assert.assertFalse(detail.getRuns().isEmpty());
    Assert.assertNotNull(detail.getRuns().get(0).getReplayFrames().get(0).getResultMap().get("eventData"));
}
```

- [ ] **Step 2: 运行后端聚焦测试**

Run:

```bash
mvn test -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ExecutionLedgerQueryServiceTest,ReplayProjectorTest,ConversationHistoryControllerTest,ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
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
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReplayProjectorTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ReactExecutionLedgerIntegrationTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/PlanSolveExecutionLedgerIntegrationTest.java
git add ui/src/utils/chat.test.ts
git add ui/src/utils/conversationHistory.test.ts
git commit -m "test: verify session replay stays isomorphic"
```

---

## 自我审查

### Spec 覆盖检查

| 需求 | 对应任务 |
| --- | --- |
| rich tool 完整结构不再依赖 `ai_agent_tool_invocation` | 当前基线 + Task 2 / Task 3 |
| 新增独立会话主表，一行代表一个会话 | Task 1, Task 3 |
| `ai_agent_dialogue_run` 明确代表会话里的一次请求 | 当前基线 + Task 1 |
| 会话主表沉淀会话级统计与摘要 | Task 1, Task 3, Task 5 |
| 历史恢复读侧统一基于 `structuredOutput + artifact` | Task 2, Task 3 |
| LLM 历史语义直接使用现有 `agent_name` 约定 | 当前基线 + Task 2 |
| 实时与历史投影同构 | Task 2, Task 5 |
| 前端刷新或重进可恢复当前 session 细节 | Task 3, Task 4, Task 5 |
| 本期不引入 `deviceId` | 设计结论 + Task 3 / Task 4 |

### Placeholder 扫描

- 没有 `TODO / TBD / later`
- 没有“自行处理边界情况”这类空话
- 任务都给出了具体文件、命令和关键代码骨架

### 类型一致性检查

- `DialogueSession` 统一承担 `title / outputStyle / deepThink / roleAgentId / runCount / finishedRunCount / failedRunCount`
- `DialogueRun` 统一承担“单次请求”级别的 `requestId / queryText / finalSummaryText`
- rich tool 完整终态统一通过 `ToolStructuredOutput` 落 `tool_output_*` 分表
- LLM 历史语义统一通过 `ReplayProjector.resolveHistoryMessageType(...)` 从 `agentName` 映射

### 风险提醒

1. 现在把 `agent_name` 当成历史语义来源，必须把映射逻辑集中在 `ReplayProjector` 一处，不能在 controller、service、前端各写一份判断。
2. 新增 `ai_agent_dialogue_session` 后，`session` 和 `run` 的职责边界要守住，不能把请求级字段重新堆回会话表，也不要把会话统计反向塞回 run 表。
3. 本期不引入 `deviceId`，所以前端初始化恢复只能默认读当前 `sessionId`，不要偷偷回退到别的 session，避免误展示无关历史。
4. `BaseAgentResponseHandler` 现有 `switch` 逻辑已经被前端长期消费，替换为共享 projector 时必须用同构测试锁死事件 shape，避免历史能播、实时炸掉。
