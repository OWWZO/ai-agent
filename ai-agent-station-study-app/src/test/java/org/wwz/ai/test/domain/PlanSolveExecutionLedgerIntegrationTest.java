package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.dto.Plan;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ExecutionRunDetail;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.PlanningToolOutput;

import java.util.List;
import java.util.Map;

/**
 * PlanSolve 并发工具账本运行时回归。
 */
public class PlanSolveExecutionLedgerIntegrationTest {

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
