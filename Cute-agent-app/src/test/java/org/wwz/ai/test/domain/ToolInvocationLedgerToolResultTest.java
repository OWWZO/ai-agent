package org.wwz.ai.test.domain;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.ExecutionRunDetail;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 账本双列契约：llm_oberserve 存模型实际看到的文本，tool_result 存完整结果 JSON。
 * 两列独立写入，不再互相回落。
 */
public class ToolInvocationLedgerToolResultTest {

    @Test
    public void persistsModelObservationAndFullResultSeparately() {
        ExecutionLedgerFixtureFactory.LedgerTestContext ledger = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        AgentContext context = ExecutionLedgerFixtureFactory.newAgentContext(
                "req-tool-result-split-001", "session-tool-result-split-001", ledger.recorder);
        ExecutionLedgerFixtureFactory.activateRun(context, ledger.recorder, ExecutionLedgerConstants.ENTRY_AGENT_REACT);
        ExecutionLedgerFixtureFactory.createLlmInvocation(
                context, ledger.recorder, "react", 1, ExecutionLedgerConstants.CALL_KIND_ASK_TOOL);

        context.getToolCollection().addTool(new SplitTool());
        TestAgent agent = new TestAgent(context);
        agent.availableTools = context.getToolCollection();

        String modelObservation = agent.executeTool(ExecutionLedgerFixtureFactory.newToolCall(
                "tool-call-split-001", "split_tool", "{}"));

        ExecutionRunDetail detail = ledger.queryService.queryRunDetail(context.getRequestId());
        Assert.assertNotNull(detail);
        Assert.assertEquals(1, detail.getToolInvocations().size());
        ToolInvocationView view = detail.getToolInvocations().get(0);

        // 模型侧：工具预填的紧凑文本原样落 llm_oberserve。
        Assert.assertEquals("split tool done", modelObservation);
        Assert.assertEquals("split tool done", view.getLlmObservation());

        // 前端回放侧：完整结构化 JSON 落 tool_result，与模型侧内容不同。
        String toolResult = view.getToolResult();
        Assert.assertNotNull("tool_result 未落库", toolResult);
        Assert.assertNotEquals(view.getLlmObservation(), toolResult);
        Assert.assertTrue(toolResult.contains("\"files\""));
        Assert.assertTrue(toolResult.contains("a.txt"));
        Assert.assertTrue(toolResult.contains("\"bytes\":42"));
    }

    /** 预填 llmObservation + llmData 走完整 JSON —— 正是投影层接入后的形态。 */
    private static final class SplitTool implements BaseTool {

        @Override
        public String getName() {
            return "split_tool";
        }

        @Override
        public String getDescription() {
            return "验证账本双列的测试工具";
        }

        @Override
        public Map<String, Object> toParams() {
            return Map.of();
        }

        @Override
        public Object execute(Object input) {
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("name", "a.txt");
            file.put("bytes", 42);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("files", List.of(file));
            return ToolResultPayload.builder()
                    .toolResult(JSON.toJSONString(data))
                    .llmObservation("split tool done")
                    .llmData(data)
                    .failed(Boolean.FALSE)
                    .build();
        }
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
}
