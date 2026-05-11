package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ExecutionRunDetail;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * 工具 observation 运行时回归。
 * 先锁定“最终 observation 必须和账本一致”的契约，再驱动主实现收口。
 */
public class ToolLlmObservationRuntimeTest {

    @Test
    public void shouldPersistSingleToolObservationForPlanningStyleExecution() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ledger = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        AgentContext context = ExecutionLedgerFixtureFactory.newAgentContext("req-tool-observation-single-001", "session-tool-observation-001", ledger.recorder);
        ExecutionLedgerFixtureFactory.activateRun(context, ledger.recorder, ExecutionLedgerConstants.ENTRY_AGENT_PLAN_SOLVE);
        ExecutionLedgerFixtureFactory.createLlmInvocation(
                context,
                ledger.recorder,
                "planning",
                1,
                ExecutionLedgerConstants.CALL_KIND_ASK_TOOL
        );

        context.getToolCollection().addTool(new ArtifactTool(context, "单工具执行完成"));

        TestAgent agent = new TestAgent(context, 6);
        agent.availableTools = context.getToolCollection();

        String llmObservation = agent.executeTool(ExecutionLedgerFixtureFactory.newToolCall(
                "tool-call-single-001",
                "artifact_tool",
                "{\"fileName\":\"single-report.md\",\"url\":\"https://file.example.com/single-report.md\"}"
        ));

        ExecutionRunDetail detail = ledger.queryService.queryRunDetail(context.getRequestId());
        Assert.assertNotNull(detail);
        Assert.assertEquals(1, detail.getToolInvocations().size());
        Assert.assertEquals(llmObservation, readObservation(detail.getToolInvocations().get(0)));
        Assert.assertNull(detail.getToolInvocations().get(0).getStructuredOutput());
    }

    @Test
    public void shouldPersistSameObservationAsFinalBatchObservation() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ledger = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        AgentContext context = ExecutionLedgerFixtureFactory.newAgentContext("req-tool-observation-batch-001", "session-tool-observation-001", ledger.recorder);
        ExecutionLedgerFixtureFactory.activateRun(context, ledger.recorder, ExecutionLedgerConstants.ENTRY_AGENT_REACT);
        ExecutionLedgerFixtureFactory.createLlmInvocation(
                context,
                ledger.recorder,
                "react",
                1,
                ExecutionLedgerConstants.CALL_KIND_ASK_TOOL
        );

        context.getToolCollection().addTool(new ArtifactTool(context, "批量工具执行结果很长，需要被统一裁剪并追加文件摘要"));

        TestAgent agent = new TestAgent(context, 10);
        agent.availableTools = context.getToolCollection();

        Map<String, String> results = agent.executeTools(List.of(
                ExecutionLedgerFixtureFactory.newToolCall(
                        "tool-call-batch-001",
                        "artifact_tool",
                        "{\"fileName\":\"batch-report.md\",\"url\":\"https://file.example.com/batch-report.md\"}"
                )
        ));

        ExecutionRunDetail detail = ledger.queryService.queryRunDetail(context.getRequestId());
        Assert.assertNotNull(detail);
        Assert.assertEquals(1, detail.getToolInvocations().size());
        Assert.assertEquals(results.get("tool-call-batch-001"), readObservation(detail.getToolInvocations().get(0)));
        Assert.assertNull(detail.getToolInvocations().get(0).getStructuredOutput());
    }

    private String readObservation(ToolInvocationView view) {
        try {
            Method method = view.getClass().getMethod("getLlmObservation");
            Object value = method.invoke(view);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            throw new IllegalStateException("读取 observation 失败", e);
        }
    }

    private static final class TestAgent extends BaseAgent {
        private final Integer maxObserve;

        private TestAgent(AgentContext context, Integer maxObserve) {
            setContext(context);
            this.maxObserve = maxObserve;
        }

        @Override
        public String step() {
            return "";
        }

        @Override
        protected Integer resolveMaxObserveLength() {
            return maxObserve;
        }
    }

    private static final class ArtifactTool implements BaseTool {
        private final AgentContext agentContext;
        private final String resultText;

        private ArtifactTool(AgentContext agentContext, String resultText) {
            this.agentContext = agentContext;
            this.resultText = resultText;
        }

        @Override
        public String getName() {
            return "artifact_tool";
        }

        @Override
        public String getDescription() {
            return "用于验证 observation 的测试工具";
        }

        @Override
        public Map<String, Object> toParams() {
            return Map.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object execute(Object input) {
            Map<String, Object> params = (Map<String, Object>) input;
            ToolArtifactSource source = agentContext.requireCurrentToolArtifactSource(getName());
            agentContext.registerGeneratedArtifact(source, File.builder()
                    .fileName(String.valueOf(params.get("fileName")))
                    .ossUrl(String.valueOf(params.get("url")))
                    .domainUrl(String.valueOf(params.get("url")))
                    .description("observation 回归产物")
                    .isInternalFile(false)
                    .build());
            return resultText;
        }
    }
}
