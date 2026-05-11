package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.agent.ExecutorAgent;
import org.wwz.ai.domain.agent.runtime.agent.PlanningAgent;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.SubTaskExecutionResult;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;
import org.wwz.ai.domain.agent.runtime.llm.LLM;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.dto.Plan;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.Step2PlanExecuteNode;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ExecutionRunDetail;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.PlanningToolOutput;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * PlanSolve 并发工具账本运行时回归。
 */
public class PlanSolveExecutionLedgerIntegrationTest {

    @Test
    public void shouldSupportNestedTaskAndToolConcurrencyWithoutBreakingLedger() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ledger = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "plannerMaxParallelTasks", 2);
        ReflectionTestUtils.setField(reactorConfig, "plannerMaxSteps", 10);
        ReflectionTestUtils.setField(reactorConfig, "executorModelName", "test-model");
        ReflectionTestUtils.setField(reactorConfig, "maxObserve", "2048");
        ReflectionTestUtils.setField(reactorConfig, "taskPrePrompt", "");
        reactorConfig.setExecutorSystemPromptMap("{}");
        reactorConfig.setExecutorNextStepPromptMap("{}");
        reactorConfig.setExecutorSopPromptMap("{}");
        ReactorRuntimeDependencies runtimeDependencies = ReactorRuntimeTestSupport.runtimeDependencies(
                reactorConfig,
                null,
                new MockEnvironment()
                        .withProperty("llm.default.base_url", "http://127.0.0.1")
                        .withProperty("llm.default.apikey", "test-key")
                        .withProperty("llm.default.model", "test-model")
        );

        AgentContext context = ExecutionLedgerFixtureFactory.newAgentContext("req-plan-nested-001", "session-plan-nested-001", ledger.recorder);
        context.setPrinter(new SilentPrinter());
        context.setRuntimeDependencies(runtimeDependencies);
        context.setQuery("并发执行嵌套任务");
        context.setDateInfo("2026-05-10");
        context.setBasePrompt("");
        context.setSopPrompt("");
        context.setHistoryDialogue("");
        context.getToolCollection().setAgentContext(context);
        context.getToolCollection().addTool(new ParallelArtifactTool(context));
        ExecutionLedgerFixtureFactory.activateRun(context, ledger.recorder, ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE);
        ExecutionLedgerFixtureFactory.createLlmInvocation(
                context,
                ledger.recorder,
                "executor",
                2,
                ExecutionLedgerConstants.CALL_KIND_ASK_TOOL
        );

        ExecutorAgent parentExecutor = new ExecutorAgent(context);
        parentExecutor.getMemory().clear();
        parentExecutor.getMemory().addMessage(Message.userMessage("父任务上下文", null));

        NestedConcurrencyStepNode node = new NestedConcurrencyStepNode(reactorConfig, Executors.newFixedThreadPool(4));
        List<SubTaskExecutionResult> childResults = node.runParallelTasks(
                context,
                AgentRequest.builder()
                        .requestId(context.getRequestId())
                        .sessionId(context.getSessionId())
                        .query(context.getQuery())
                        .outputStyle("html")
                        .build(),
                parentExecutor,
                List.of("你的任务是：外层任务A", "你的任务是：外层任务B")
        );
        node.mergeIntoParent(parentExecutor, childResults);

        ledger.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(context.getAgentRunState().getRunId())
                .requestId(context.getRequestId())
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .finalSummaryText("nested summary")
                .build());

        ExecutionRunDetail detail = ledger.queryService.queryRunDetail(context.getRequestId());
        Assert.assertEquals(4, detail.getToolInvocations().size());
        Assert.assertEquals(4, detail.getArtifacts().size());
        Assert.assertEquals(AgentState.FINISHED, parentExecutor.getState());
        Assert.assertTrue(detail.getToolInvocations().stream().allMatch(item -> item.getStatus().equals(ExecutionLedgerConstants.STATUS_SUCCESS)));
    }

    @Test
    public void shouldKeepDispatchOrderAndFailOpenForParallelTools() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ledger = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        AgentContext context = ExecutionLedgerFixtureFactory.newAgentContext("req-plan-ledger-001", "session-plan-ledger-001", ledger.recorder);
        ExecutionLedgerFixtureFactory.activateRun(context, ledger.recorder, ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE);
        ExecutionLedgerFixtureFactory.createLlmInvocation(
                context,
                ledger.recorder,
                "executor",
                2,
                ExecutionLedgerConstants.CALL_KIND_ASK_TOOL
        );

        context.getToolCollection().addTool(new ParallelArtifactTool(context));

        TestAgent agent = new TestAgent("executor", context);
        agent.availableTools = context.getToolCollection();
        Map<String, String> result = agent.executeTools(List.of(
                ExecutionLedgerFixtureFactory.newToolCall(
                        "plan-tool-call-001",
                        "parallel_artifact_tool",
                        "{\"fileName\":\"plan-a.md\",\"url\":\"https://file.example.com/plan-a.md\",\"sleepMs\":120}"
                ),
                ExecutionLedgerFixtureFactory.newToolCall(
                        "plan-tool-call-002",
                        "parallel_artifact_tool",
                        "{\"fileName\":\"plan-b.md\",\"url\":\"https://file.example.com/plan-b.md\",\"sleepMs\":10,\"fail\":true}"
                )
        ));

        Assert.assertTrue(result.get("plan-tool-call-001").startsWith("执行成功:plan-tool-call-001"));
        Assert.assertTrue(result.get("plan-tool-call-001").contains("artifactKey:plan-tool-call-001::plan-a.md"));
        Assert.assertEquals("Tool parallel_artifact_tool Error.", result.get("plan-tool-call-002"));

        ledger.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(context.getAgentRunState().getRunId())
                .requestId(context.getRequestId())
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .finalSummaryText("plan summary")
                .build());

        ExecutionRunDetail detail = ledger.queryService.queryRunDetail(context.getRequestId());
        Assert.assertEquals(2, detail.getToolInvocations().size());
        Assert.assertEquals("plan-tool-call-001", detail.getToolInvocations().get(0).getToolCallId());
        Assert.assertEquals(Integer.valueOf(1), detail.getToolInvocations().get(0).getDispatchIndex());
        Assert.assertEquals(result.get("plan-tool-call-001"), detail.getToolInvocations().get(0).getLlmObservation());
        Assert.assertNull(detail.getToolInvocations().get(0).getStructuredOutput());
        Assert.assertEquals("plan-tool-call-002", detail.getToolInvocations().get(1).getToolCallId());
        Assert.assertEquals(Integer.valueOf(2), detail.getToolInvocations().get(1).getDispatchIndex());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_FAILED), detail.getToolInvocations().get(1).getStatus());
        Assert.assertEquals(result.get("plan-tool-call-002"), detail.getToolInvocations().get(1).getLlmObservation());
        Assert.assertNull(detail.getToolInvocations().get(1).getStructuredOutput());
        Assert.assertEquals(1, detail.getArtifacts().size());
        Assert.assertEquals("plan-a.md", detail.getArtifacts().get(0).getFileName());
    }

    @Test
    public void shouldExposeStoppedRunHistoryWithReadableTerminalState() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ledger = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        AgentContext context = ExecutionLedgerFixtureFactory.newAgentContext("req-plan-stop-001", "session-plan-stop-001", ledger.recorder);
        Long runId = ExecutionLedgerFixtureFactory.activateRun(context, ledger.recorder, ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE);
        Long llmInvocationId = ExecutionLedgerFixtureFactory.createLlmInvocation(
                context,
                ledger.recorder,
                "planning",
                1,
                ExecutionLedgerConstants.CALL_KIND_ASK_TOOL
        );
        ledger.recorder.finishLlmInvocation(LlmInvocationFinishRecord.builder()
                .llmInvocationId(llmInvocationId)
                .requestId(context.getRequestId())
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .responseText("先规划执行步骤")
                .toolCallCount(0)
                .promptTokens(6)
                .completionTokens(10)
                .totalTokens(16)
                .finishReason("stop")
                .finishedAt(java.time.LocalDateTime.now())
                .build());
        ledger.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(runId)
                .requestId(context.getRequestId())
                .status(ExecutionLedgerConstants.STATUS_STOPPED)
                .finalSummaryText("已停止，但保留当前结果")
                .errorCode("PLAN_SOLVE_STOPPED")
                .errorMsg("达到最大迭代次数，任务终止。")
                .build());

        List<GptProcessResult> historyFrames = ledger.replayService.queryConversationHistory(context.getSessionId())
                .getRuns()
                .get(0)
                .getReplayFrames();

        Assert.assertEquals(2, historyFrames.size());
        Assert.assertEquals("plan_thought", eventMessageType(historyFrames.get(0)));
        Assert.assertEquals("task", eventMessageType(historyFrames.get(1)));
        Assert.assertEquals("result", nestedMessageType(historyFrames.get(1)));
        Assert.assertEquals("已停止，但保留当前结果", nestedResult(historyFrames.get(1)));
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_STOPPED),
                ledger.queryService.queryRunDetail(context.getRequestId()).getRun().getStatus());
    }

    @Test
    public void shouldReplayPlanningToolAsPlanAndTaskFrames() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ledger = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        AgentContext context = ExecutionLedgerFixtureFactory.newAgentContext("req-plan-history-001", "session-plan-history-001", ledger.recorder);
        Long runId = ExecutionLedgerFixtureFactory.activateRun(context, ledger.recorder, ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE);
        Long llmInvocationId = ExecutionLedgerFixtureFactory.createLlmInvocation(
                context,
                ledger.recorder,
                "planning",
                1,
                ExecutionLedgerConstants.CALL_KIND_ASK_TOOL
        );
        Map<String, Long> toolIds = ledger.recorder.createToolInvocations(
                org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord.builder()
                        .runId(runId)
                        .requestId(context.getRequestId())
                        .llmInvocationId(llmInvocationId)
                        .agentName("planning")
                        .stepNo(1)
                        .items(List.of(org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord.Item.builder()
                                .toolCallId("plan-history-tool-001")
                                .dispatchIndex(1)
                                .toolName("planning")
                                .toolProvider(ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL)
                                .inputJson("{\"command\":\"create\",\"title\":\"调研计划\",\"steps\":[\"执行顺序1. 信息收集：搜集资料\",\"执行顺序2. 输出总结：整理结论\"]}")
                                .startedAt(java.time.LocalDateTime.now())
                                .build()))
                        .build()
        );
        ledger.recorder.finishToolInvocation(org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord.builder()
                .toolInvocationId(toolIds.get("plan-history-tool-001"))
                .runId(runId)
                .requestId(context.getRequestId())
                .sessionId(context.getSessionId())
                .toolCallId("plan-history-tool-001")
                .toolName("planning")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .llmObservation("我已创建plan")
                .finishedAt(java.time.LocalDateTime.now())
                .build());
        ledger.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(runId)
                .requestId(context.getRequestId())
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .finalSummaryText("计划已生成")
                .build());

        List<GptProcessResult> historyFrames = ledger.replayService.queryConversationHistory(context.getSessionId())
                .getRuns()
                .get(0)
                .getReplayFrames();

        Assert.assertTrue(historyFrames.size() >= 3);
        Assert.assertEquals("plan", eventMessageType(historyFrames.get(0)));
        Assert.assertEquals("task", eventMessageType(historyFrames.get(1)));
        Assert.assertEquals("task", nestedMessageType(historyFrames.get(1)));
        Assert.assertEquals("信息收集：搜集资料", nestedTask(historyFrames.get(1)));
    }

    @Test
    public void shouldPersistPlanningStructuredOutputAndReplayOrdinaryReplanLifecycle() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ledger = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        AgentContext context = ExecutionLedgerFixtureFactory.newAgentContext("req-plan-history-002", "session-plan-history-002", ledger.recorder);
        Long runId = ExecutionLedgerFixtureFactory.activateRun(context, ledger.recorder, ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE);
        Long llmInvocationId = ExecutionLedgerFixtureFactory.createLlmInvocation(
                context,
                ledger.recorder,
                "planning",
                1,
                ExecutionLedgerConstants.CALL_KIND_ASK_TOOL
        );
        Map<String, Long> toolIds = ledger.recorder.createToolInvocations(
                org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord.builder()
                        .runId(runId)
                        .requestId(context.getRequestId())
                        .llmInvocationId(llmInvocationId)
                        .agentName("planning")
                        .stepNo(1)
                        .items(List.of(
                                org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord.Item.builder()
                                        .toolCallId("plan-history-tool-002")
                                        .dispatchIndex(1)
                                        .toolName("planning")
                                        .toolProvider(ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL)
                                        .inputJson("{\"command\":\"create\",\"title\":\"旧计划\",\"steps\":[\"旧步骤\"]}")
                                        .startedAt(java.time.LocalDateTime.now())
                                        .build(),
                                org.wwz.ai.domain.agent.ledger.model.ToolInvocationBatchStartRecord.Item.builder()
                                        .toolCallId("plan-history-tool-003")
                                        .dispatchIndex(2)
                                        .toolName("planning")
                                        .toolProvider(ExecutionLedgerConstants.TOOL_PROVIDER_LOCAL)
                                        .inputJson("{\"command\":\"update\",\"title\":\"旧计划\",\"steps\":[\"旧步骤\"]}")
                                        .startedAt(java.time.LocalDateTime.now())
                                        .build()))
                        .build()
        );
        ledger.recorder.finishToolInvocation(org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord.builder()
                .toolInvocationId(toolIds.get("plan-history-tool-002"))
                .runId(runId)
                .requestId(context.getRequestId())
                .sessionId(context.getSessionId())
                .toolCallId("plan-history-tool-002")
                .toolName("planning")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .llmObservation("我已创建plan")
                .structuredOutput(PlanningToolOutput.builder()
                        .command("create")
                        .afterPlan(Plan.builder()
                                .title("普通 replan")
                                .steps(List.of("步骤一", "步骤二"))
                                .stepStatus(List.of("in_progress", "not_started"))
                                .notes(List.of("", ""))
                                .build())
                        .currentStep("步骤一")
                        .currentStepIndex(0)
                        .autoAdvanced(true)
                        .autoFinished(false)
                        .build())
                .finishedAt(java.time.LocalDateTime.now())
                .build());
        ledger.recorder.finishToolInvocation(org.wwz.ai.domain.agent.ledger.model.ToolInvocationFinishRecord.builder()
                .toolInvocationId(toolIds.get("plan-history-tool-003"))
                .runId(runId)
                .requestId(context.getRequestId())
                .sessionId(context.getSessionId())
                .toolCallId("plan-history-tool-003")
                .toolName("planning")
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .llmObservation("我已更新plan")
                .structuredOutput(PlanningToolOutput.builder()
                        .command("update")
                        .beforePlan(Plan.builder()
                                .title("普通 replan")
                                .steps(List.of("步骤一", "步骤二"))
                                .stepStatus(List.of("completed", "in_progress"))
                                .notes(List.of("已完成", ""))
                                .build())
                        .afterPlan(Plan.builder()
                                .title("普通 replan")
                                .steps(List.of("步骤一", "新步骤A", "新步骤B"))
                                .stepStatus(List.of("completed", "in_progress", "not_started"))
                                .notes(List.of("已完成", "", ""))
                                .build())
                        .currentStep("新步骤A")
                        .currentStepIndex(1)
                        .autoAdvanced(true)
                        .autoFinished(false)
                        .build())
                .finishedAt(java.time.LocalDateTime.now())
                .build());
        ledger.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(runId)
                .requestId(context.getRequestId())
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .finalSummaryText("普通 replan 已完成")
                .build());

        ExecutionRunDetail detail = ledger.queryService.queryRunDetail(context.getRequestId());
        Assert.assertEquals(2, detail.getToolInvocations().size());
        Assert.assertTrue(detail.getToolInvocations().get(0).getStructuredOutput() instanceof PlanningToolOutput);
        Assert.assertTrue(detail.getToolInvocations().get(1).getStructuredOutput() instanceof PlanningToolOutput);

        List<GptProcessResult> historyFrames = ledger.replayService.queryConversationHistory(context.getSessionId())
                .getRuns()
                .get(0)
                .getReplayFrames();

        Assert.assertTrue(historyFrames.size() >= 5);
        Assert.assertEquals("plan", eventMessageType(historyFrames.get(0)));
        Assert.assertEquals("task", eventMessageType(historyFrames.get(1)));
        Assert.assertEquals("步骤一", nestedTask(historyFrames.get(1)));
        Assert.assertEquals("plan", eventMessageType(historyFrames.get(2)));
        Assert.assertEquals("task", eventMessageType(historyFrames.get(3)));
        Assert.assertEquals("新步骤A", nestedTask(historyFrames.get(3)));
    }

    @Test
    public void shouldPersistCompatibilityPlanningProgressForHistoryReplay() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ledger = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        AgentContext context = newPlanningAgentContext(
                "req-plan-history-compat-001",
                "session-plan-history-compat-001",
                ledger.recorder,
                "1"
        );
        Long runId = ExecutionLedgerFixtureFactory.activateRun(
                context,
                ledger.recorder,
                ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE
        );
        Long llmInvocationId = ExecutionLedgerFixtureFactory.createLlmInvocation(
                context,
                ledger.recorder,
                "planning",
                1,
                ExecutionLedgerConstants.CALL_KIND_ASK_TOOL
        );
        PlanningAgent agent = newCompatibilityPlanningAgent(context);
        agent.setToolCalls(List.of(ExecutionLedgerFixtureFactory.newToolCall(
                "plan-compat-tool-001",
                "planning",
                "{\"command\":\"create\",\"title\":\"兼容计划\",\"steps\":[\"步骤一\",\"步骤二\"]}"
        )));

        Assert.assertEquals("步骤一", agent.run(context.getQuery()));
        ledger.recorder.finishLlmInvocation(LlmInvocationFinishRecord.builder()
                .llmInvocationId(llmInvocationId)
                .requestId(context.getRequestId())
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .responseText("先创建兼容计划")
                .toolCallCount(1)
                .finishReason("tool_calls")
                .finishedAt(LocalDateTime.now())
                .build());

        Assert.assertEquals("步骤二", agent.run("步骤一执行完成"));
        Assert.assertEquals("finish", agent.run("步骤二执行完成"));

        ledger.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(runId)
                .requestId(context.getRequestId())
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .finalSummaryText("兼容计划已完成")
                .build());

        ExecutionRunDetail detail = ledger.queryService.queryRunDetail(context.getRequestId());
        List<ToolInvocationView> planningInvocations = detail.getToolInvocations().stream()
                .filter(item -> "planning".equals(item.getToolName()))
                .toList();

        Assert.assertEquals(3, planningInvocations.size());
        Assert.assertEquals(List.of("create", "mark_step", "mark_step"), planningInvocations.stream()
                .map(item -> ((PlanningToolOutput) item.getStructuredOutput()).getCommand())
                .toList());
        PlanningToolOutput finalOutput = (PlanningToolOutput) planningInvocations.get(planningInvocations.size() - 1)
                .getStructuredOutput();
        Assert.assertEquals(List.of("completed", "completed"), finalOutput.getAfterPlan().getStepStatus());

        List<GptProcessResult> historyFrames = ledger.replayService.queryConversationHistory(context.getSessionId())
                .getRuns()
                .get(0)
                .getReplayFrames();

        List<GptProcessResult> planFrames = historyFrames.stream()
                .filter(frame -> "plan".equals(eventMessageType(frame)))
                .toList();
        Assert.assertEquals(3, planFrames.size());
        Assert.assertEquals(List.of("completed", "completed"),
                plainResultMap(planFrames.get(planFrames.size() - 1)).get("stepStatus"));
    }

    @SuppressWarnings("unchecked")
    private String eventMessageType(GptProcessResult frame) {
        return String.valueOf(((Map<String, Object>) frame.getResultMap().get("eventData")).get("messageType"));
    }

    @SuppressWarnings("unchecked")
    private String nestedMessageType(GptProcessResult frame) {
        return String.valueOf(((Map<String, Object>) ((Map<String, Object>) frame.getResultMap().get("eventData")).get("resultMap")).get("messageType"));
    }

    @SuppressWarnings("unchecked")
    private String nestedResult(GptProcessResult frame) {
        return String.valueOf(((Map<String, Object>) ((Map<String, Object>) frame.getResultMap().get("eventData")).get("resultMap")).get("result"));
    }

    @SuppressWarnings("unchecked")
    private String nestedTask(GptProcessResult frame) {
        return String.valueOf(((Map<String, Object>) ((Map<String, Object>) frame.getResultMap().get("eventData")).get("resultMap")).get("task"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> plainResultMap(GptProcessResult frame) {
        return (Map<String, Object>) ((Map<String, Object>) frame.getResultMap().get("eventData")).get("resultMap");
    }

    private AgentContext newPlanningAgentContext(String requestId,
                                                 String sessionId,
                                                 org.wwz.ai.domain.agent.ledger.AgentExecutionRecorder recorder,
                                                 String closeUpdate) {
        ReactorConfig reactorConfig = new ReactorConfig();
        reactorConfig.setPlannerSystemPromptMap("{}");
        reactorConfig.setPlannerNextStepPromptMap("{}");
        ReflectionTestUtils.setField(reactorConfig, "plannerModelName", "test-planner-model");
        ReflectionTestUtils.setField(reactorConfig, "plannerMaxSteps", 10);
        ReflectionTestUtils.setField(reactorConfig, "planningCloseUpdate", closeUpdate);
        ReflectionTestUtils.setField(reactorConfig, "llmSettingsMap", Map.of(
                "test-planner-model",
                LLMSettings.builder()
                        .model("test-planner-model")
                        .maxTokens(1024)
                        .temperature(0)
                        .baseUrl("http://127.0.0.1")
                        .interfaceUrl("/v1/chat/completions")
                        .functionCallType("function_call")
                        .apiKey("test-key")
                        .maxInputTokens(4096)
                        .build()
        ));
        ReactorRuntimeDependencies runtimeDependencies = ReactorRuntimeTestSupport.runtimeDependencies(reactorConfig);
        ToolCollection toolCollection = new ToolCollection();
        AgentContext context = AgentContext.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .query("执行兼容计划")
                .dateInfo("2026-05-05")
                .basePrompt("")
                .sopPrompt("")
                .historyDialogue("")
                .printer(new SilentPrinter())
                .toolCollection(toolCollection)
                .productFiles(new ArrayList<>())
                .taskProductFiles(new ArrayList<>())
                .executionRecorder(recorder)
                .isStream(false)
                .runtimeDependencies(runtimeDependencies)
                .build();
        toolCollection.setAgentContext(context);
        return context;
    }

    private PlanningAgent newCompatibilityPlanningAgent(AgentContext context) {
        PlanningAgent agent = new PlanningAgent(context);
        LLM llm = Mockito.mock(LLM.class);
        Mockito.when(llm.askTool(Mockito.any(), Mockito.anyList(), Mockito.any(), Mockito.any(),
                        Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyInt()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(LLM.ToolCallResponse.builder()
                        .content("mock planning thought")
                        .toolCalls(agent.getToolCalls() == null ? List.of() : agent.getToolCalls())
                        .build()));
        agent.setLlm(llm);
        return agent;
    }

    private static final class TestAgent extends BaseAgent {
        private TestAgent(String name, AgentContext context) {
            setName(name);
            setContext(context);
        }

        @Override
        public String step() {
            return "";
        }
    }

    private static final class NestedConcurrencyStepNode extends Step2PlanExecuteNode {
        private final Executor taskExecutor;

        private NestedConcurrencyStepNode(ReactorConfig reactorConfig, Executor taskExecutor) {
            this.taskExecutor = taskExecutor;
            ReflectionTestUtils.setField(this, "reactorConfig", reactorConfig);
        }

        @Override
        protected Executor resolveTaskExecutor(AgentContext agentContext) {
            return taskExecutor;
        }

        @Override
        protected SubTaskExecutionResult executeSingleParallelTask(AgentContext parentContext,
                                                                  AgentRequest request,
                                                                  ExecutorAgent parentExecutor,
                                                                  String task) {
            AgentContext childContext = parentContext.forkForParallelTask(task);
            childContext.setPrinter(new SilentPrinter());
            childContext.setRuntimeDependencies(parentContext.getRuntimeDependencies());
            ToolCollection childTools = new ToolCollection();
            childTools.setAgentContext(childContext);
            childTools.addTool(new ParallelArtifactTool(childContext));
            childContext.setToolCollection(childTools);

            ExecutionLedgerFixtureFactory.createLlmInvocation(
                    childContext,
                    parentContext.getExecutionRecorder(),
                    "executor",
                    3,
                    ExecutionLedgerConstants.CALL_KIND_ASK_TOOL
            );

            TestAgent childAgent = new TestAgent("executor", childContext);
            childAgent.availableTools = childContext.getToolCollection();
            Map<String, String> toolResults = childAgent.executeTools(List.of(
                    ExecutionLedgerFixtureFactory.newToolCall(
                            task + "-tool-1",
                            "parallel_artifact_tool",
                            "{\"fileName\":\"" + task + "-1.md\",\"url\":\"https://file.example.com/" + task + "-1.md\",\"sleepMs\":50}"
                    ),
                    ExecutionLedgerFixtureFactory.newToolCall(
                            task + "-tool-2",
                            "parallel_artifact_tool",
                            "{\"fileName\":\"" + task + "-2.md\",\"url\":\"https://file.example.com/" + task + "-2.md\",\"sleepMs\":10}"
                    )
            ));

            return SubTaskExecutionResult.builder()
                    .task(task)
                    .taskResult(String.join("\n", toolResults.values()))
                    .state(AgentState.FINISHED)
                    .memoryIncrementMessages(List.of(Message.assistantMessage("child:" + task, null)))
                    .build();
        }

        private List<SubTaskExecutionResult> runParallelTasks(AgentContext parentContext,
                                                              AgentRequest request,
                                                              ExecutorAgent parentExecutor,
                                                              List<String> tasks) {
            return executeParallelTasks(parentContext, request, parentExecutor, tasks);
        }

        private void mergeIntoParent(ExecutorAgent parentExecutor, List<SubTaskExecutionResult> results) {
            mergeChildResultsIntoParent(parentExecutor, results);
        }
    }

    private static final class SilentPrinter implements Printer {

        @Override
        public void send(String messageId, String messageType, Object message, String digitalEmployee, Boolean isFinal) {
        }

        @Override
        public void send(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, String digitalEmployee, Boolean isFinal) {
        }

        @Override
        public void send(String messageType, Object message) {
        }

        @Override
        public void send(String messageType, Object message, String digitalEmployee) {
        }

        @Override
        public void send(String messageId, String messageType, Object message, Boolean isFinal) {
        }

        @Override
        public void sendWithResultMap(String messageId, String messageType, Object message, Map<String, Object> extraResultMap, Boolean isFinal) {
        }

        @Override
        public void sendWithResultMap(String messageType, Object message, Map<String, Object> extraResultMap) {
        }

        @Override
        public void close() {
        }

        @Override
        public void updateAgentType(AgentType agentType) {
        }
    }

    private static final class ParallelArtifactTool implements BaseTool {
        private final AgentContext agentContext;

        private ParallelArtifactTool(AgentContext agentContext) {
            this.agentContext = agentContext;
        }

        @Override
        public String getName() {
            return "parallel_artifact_tool";
        }

        @Override
        public String getDescription() {
            return "测试并发账本工具";
        }

        @Override
        public Map<String, Object> toParams() {
            return Map.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object execute(Object input) {
            Map<String, Object> params = (Map<String, Object>) input;
            try {
                Thread.sleep(Long.parseLong(String.valueOf(params.get("sleepMs"))));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (Boolean.parseBoolean(String.valueOf(params.getOrDefault("fail", false)))) {
                throw new IllegalStateException("tool failed");
            }
            ToolArtifactSource source = agentContext.requireCurrentToolArtifactSource(getName());
            agentContext.registerGeneratedArtifact(source, File.builder()
                    .fileName(String.valueOf(params.get("fileName")))
                    .ossUrl(String.valueOf(params.get("url")))
                    .domainUrl(String.valueOf(params.get("url")))
                    .isInternalFile(false)
                    .build());
            return "执行成功:" + source.getToolCallId();
        }
    }
}
