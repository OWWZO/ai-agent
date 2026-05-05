package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.handler.ReactAgentResponseHandler;
import org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ExecutionRunDetail;
import org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.response.AgentResponse;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;

import java.util.List;
import java.util.Map;

/**
 * ReAct 主链路账本运行时回归。
 */
public class ReactExecutionLedgerIntegrationTest {

    @Test
    public void shouldCaptureReactToolInvocationAndArtifacts() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ledger = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        AgentContext context = ExecutionLedgerFixtureFactory.newAgentContext("req-react-ledger-001", "session-react-ledger-001", ledger.recorder);
        ExecutionLedgerFixtureFactory.activateRun(context, ledger.recorder, ExecutionLedgerConstants.ENTRY_AGENT_REACT);
        ExecutionLedgerFixtureFactory.createLlmInvocation(
                context,
                ledger.recorder,
                "react",
                1,
                ExecutionLedgerConstants.CALL_KIND_ASK_TOOL
        );

        context.getToolCollection().addTool(new ArtifactTool(context, false));

        TestAgent agent = new TestAgent("react", context);
        agent.availableTools = context.getToolCollection();
        Map<String, String> result = agent.executeTools(List.of(
                ExecutionLedgerFixtureFactory.newToolCall(
                        "react-tool-call-001",
                        "artifact_tool",
                        "{\"fileName\":\"react-report.md\",\"url\":\"https://file.example.com/react-report.md\"}"
                )
        ));

        Assert.assertTrue(result.get("react-tool-call-001").startsWith("执行成功"));
        Assert.assertTrue(result.get("react-tool-call-001").contains("artifactKey:react-tool-call-001::react-report.md"));

        ledger.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(context.getAgentRunState().getRunId())
                .requestId(context.getRequestId())
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .finalSummaryText("react summary")
                .build());

        ExecutionRunDetail detail = ledger.queryService.queryRunDetail(context.getRequestId());
        Assert.assertNotNull(detail);
        Assert.assertEquals(1, detail.getToolInvocations().size());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_SUCCESS), detail.getToolInvocations().get(0).getStatus());
        Assert.assertEquals(result.get("react-tool-call-001"), detail.getToolInvocations().get(0).getLlmObservation());
        Assert.assertNull(detail.getToolInvocations().get(0).getStructuredOutput());
        Assert.assertEquals(1, detail.getArtifacts().size());
        Assert.assertEquals("react-report.md", detail.getArtifacts().get(0).getFileName());
    }

    @Test
    public void shouldKeepRealtimeAndHistoryPlanThoughtContractAligned() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ledger = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        AgentContext context = ExecutionLedgerFixtureFactory.newAgentContext("req-react-plan-001", "session-react-plan-001", ledger.recorder);
        Long runId = ExecutionLedgerFixtureFactory.activateRun(context, ledger.recorder, ExecutionLedgerConstants.ENTRY_AGENT_REACT);
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
                .responseText("先拆分执行计划")
                .toolCallCount(0)
                .promptTokens(8)
                .completionTokens(12)
                .totalTokens(20)
                .finishReason("stop")
                .finishedAt(java.time.LocalDateTime.now())
                .build());
        ledger.recorder.finishRun(DialogueRunFinishRecord.builder()
                .runId(runId)
                .requestId(context.getRequestId())
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .finalSummaryText("react plan summary")
                .build());

        ReactAgentResponseHandler handler = new ReactAgentResponseHandler(
                new org.wwz.ai.domain.agent.ledger.replay.ReplayProjector(
                        new org.wwz.ai.domain.agent.ledger.replay.projector.ToolInvocationProjectorRegistry(
                                List.of(),
                                new org.wwz.ai.domain.agent.ledger.replay.projector.impl.DefaultToolInvocationProjector()
                        )
                )
        );

        GptProcessResult realtime = handler.handle(
                AgentRequest.builder().requestId(context.getRequestId()).build(),
                AgentResponse.builder()
                        .requestId(context.getRequestId())
                        .messageId("msg-react-plan-1")
                        .messageType("plan_thought")
                        .messageTime("1714631000000")
                        .planThought("先拆分执行计划")
                        .isFinal(true)
                        .finish(false)
                        .resultMap(Map.of("agentType", 5, "plannerRoundId", "9001"))
                        .build(),
                List.of(),
                new EventResult()
        );

        ExecutionRunDetail detail = ledger.queryService.queryRunDetail(context.getRequestId());
        List<GptProcessResult> historyFrames = ledger.replayService.queryConversationHistory(context.getSessionId())
                .getRuns()
                .get(0)
                .getReplayFrames();

        Assert.assertNotNull(detail);
        Assert.assertEquals("5", String.valueOf(realtime.getResultMap().get("agentType")));
        Assert.assertEquals("plan_thought", eventMessageType(realtime));
        Assert.assertEquals("plan_thought", nestedMessageType(realtime));
        Assert.assertEquals("9001", nestedPlannerRoundId(realtime));
        Assert.assertFalse(historyFrames.isEmpty());
        Assert.assertEquals("plan_thought", eventMessageType(historyFrames.get(0)));
        Assert.assertEquals("plan_thought", nestedMessageType(historyFrames.get(0)));
        Assert.assertEquals(String.valueOf(llmInvocationId), nestedPlannerRoundId(historyFrames.get(0)));
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
    private String nestedPlannerRoundId(GptProcessResult frame) {
        Object plannerRoundId = ((Map<String, Object>) ((Map<String, Object>) frame.getResultMap().get("eventData")).get("resultMap")).get("plannerRoundId");
        return plannerRoundId == null ? null : String.valueOf(plannerRoundId);
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

    private static final class ArtifactTool implements BaseTool {
        private final AgentContext agentContext;
        private final boolean fail;

        private ArtifactTool(AgentContext agentContext, boolean fail) {
            this.agentContext = agentContext;
            this.fail = fail;
        }

        @Override
        public String getName() {
            return "artifact_tool";
        }

        @Override
        public String getDescription() {
            return "测试账本工具";
        }

        @Override
        public Map<String, Object> toParams() {
            return Map.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object execute(Object input) {
            if (fail) {
                throw new IllegalStateException("tool failed");
            }
            Map<String, Object> params = (Map<String, Object>) input;
            ToolArtifactSource source = agentContext.requireCurrentToolArtifactSource(getName());
            agentContext.registerGeneratedArtifact(source, File.builder()
                    .fileName(String.valueOf(params.get("fileName")))
                    .ossUrl(String.valueOf(params.get("url")))
                    .domainUrl(String.valueOf(params.get("url")))
                    .isInternalFile(false)
                    .build());
            return "执行成功";
        }
    }
}
