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
