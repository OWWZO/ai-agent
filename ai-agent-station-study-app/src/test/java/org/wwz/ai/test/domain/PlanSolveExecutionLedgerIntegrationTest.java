package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.agent.BaseAgent;
import org.wwz.ai.domain.agent.reactor.agent.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.reactor.agent.dto.File;
import org.wwz.ai.domain.agent.reactor.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.reactor.model.ledger.DialogueRunFinishRecord;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionRunDetail;

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
        Assert.assertEquals("plan-tool-call-002", detail.getToolInvocations().get(1).getToolCallId());
        Assert.assertEquals(Integer.valueOf(2), detail.getToolInvocations().get(1).getDispatchIndex());
        Assert.assertEquals(Integer.valueOf(ExecutionLedgerConstants.STATUS_FAILED), detail.getToolInvocations().get(1).getStatus());
        Assert.assertEquals(result.get("plan-tool-call-002"), detail.getToolInvocations().get(1).getLlmObservation());
        Assert.assertEquals(1, detail.getArtifacts().size());
        Assert.assertEquals("plan-a.md", detail.getArtifacts().get(0).getFileName());
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
