package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.agent.agent.BaseAgent;
import org.wwz.ai.domain.agent.reactor.agent.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.reactor.agent.tool.BaseTool;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionRunDetail;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;

import java.util.Map;

/**
 * output_json 运行时回归。
 * 验证账本会稳定落 tool-native output_json，而不是继续留空或只靠字符串猜测。
 */
public class ToolOutputJsonRuntimeTest {

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

        context.getToolCollection().addTool(new PlainTextTool());

        TestAgent agent = new TestAgent(context);
        agent.availableTools = context.getToolCollection();
        String observation = agent.executeTool(ExecutionLedgerFixtureFactory.newToolCall(
                "tool-json-runtime-call-001",
                "plain_text_tool",
                "{\"path\":\"/tmp/demo.txt\"}"
        ));

        ExecutionRunDetail detail = ledger.queryService.queryRunDetail(context.getRequestId());
        ToolInvocationView invocation = detail.getToolInvocations().get(0);
        Assert.assertEquals(observation, invocation.getLlmObservation());
        Assert.assertNotNull(invocation.getOutputJson());
        Assert.assertTrue(invocation.getOutputJson().contains("\"resultType\":\"plain_text\""));
        Assert.assertTrue(invocation.getOutputJson().contains("\"schemaVersion\":1"));
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
                "planning",
                1,
                ExecutionLedgerConstants.CALL_KIND_ASK_TOOL
        );

        context.getToolCollection().addTool(new FailedTool());

        TestAgent agent = new TestAgent(context);
        agent.availableTools = context.getToolCollection();
        ToolCall toolCall = ExecutionLedgerFixtureFactory.newToolCall(
                "tool-json-runtime-call-002",
                "failed_tool",
                "{\"path\":\"/tmp/missing.txt\"}"
        );
        String observation = agent.executeTool(toolCall);

        ExecutionRunDetail detail = ledger.queryService.queryRunDetail(context.getRequestId());
        ToolInvocationView invocation = detail.getToolInvocations().get(0);
        Assert.assertEquals("Tool failed_tool Error.", observation);
        Assert.assertNotNull(invocation.getOutputJson());
        Assert.assertTrue(invocation.getOutputJson().contains("\"resultType\":\"error\""));
        Assert.assertTrue(invocation.getOutputJson().contains("\"schemaVersion\":1"));
        Assert.assertFalse(invocation.getOutputJson().contains("\"renderKind\""));
    }

    private static final class TestAgent extends BaseAgent {
        private TestAgent(AgentContext context) {
            setContext(context);
        }

        @Override
        public String step() {
            return "";
        }
    }

    private static final class PlainTextTool implements BaseTool {

        @Override
        public String getName() {
            return "plain_text_tool";
        }

        @Override
        public String getDescription() {
            return "纯文本测试工具";
        }

        @Override
        public Map<String, Object> toParams() {
            return Map.of();
        }

        @Override
        public Object execute(Object input) {
            return "第1行\n第2行";
        }
    }

    private static final class FailedTool implements BaseTool {

        @Override
        public String getName() {
            return "failed_tool";
        }

        @Override
        public String getDescription() {
            return "失败测试工具";
        }

        @Override
        public Map<String, Object> toParams() {
            return Map.of();
        }

        @Override
        public Object execute(Object input) {
            throw new IllegalStateException("Tool returned null");
        }
    }
}
