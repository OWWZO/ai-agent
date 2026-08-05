package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.prompt.ToolCallPrompt;
import org.wwz.ai.domain.agent.runtime.prompt.IntentGatedPrompt;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.Map;

/**
 * 同 session 多次组装 system 时字节必须稳定（prompt cache）。
 */
public class StableSystemPromptTest {

    @Test
    public void sameInputsYieldIdenticalSystemBytesAcrossConstructions() {
        String template = ToolCallPrompt.SYSTEM_PROMPT
                + "\r\n\r\n## 当前日期\n\n{{date}}\n\n"
                + "{{basePrompt}}\n\n"
                + "<files>\n</files>\n\n"
                + "尾部说明  \n";

        String first = assemble(template, "sess-1", "react", "角色A");
        String second = assemble(template.replace("\n", "\r\n"), "sess-1", "react", "角色A");
        String third = assemble("\uFEFF" + template + "\n\n\n", "sess-1", "react", "角色A");

        Assert.assertEquals(first, second);
        Assert.assertEquals(first, third);
        Assert.assertFalse(first.contains("{{date}}"));
        Assert.assertFalse(first.contains("<files>"));
        Assert.assertTrue(first.contains(ToolCallPrompt.USER_FACING_REPLY_CONTRACT_MARKER));
        Assert.assertTrue(first.endsWith("\n"));
    }

    @Test
    public void ensureContractIsIdempotent() {
        String once = ToolCallPrompt.ensureUserFacingReplyContract("hello\r\nworld  ");
        String twice = ToolCallPrompt.ensureUserFacingReplyContract(once);
        Assert.assertEquals(once, twice);
        Assert.assertEquals(1, countMarker(once));
    }

    @Test
    public void documentChartPolicyRequiresMatchingIntentAndTool() {
        ToolCollection documentTools = new ToolCollection();
        documentTools.addTool(tool("document_generate"));

        Assert.assertEquals(IntentGatedPrompt.Selection.DOCUMENT_WITH_CHARTS,
                IntentGatedPrompt.select("生成带饼图的 PDF 报告", documentTools));
        Assert.assertEquals(IntentGatedPrompt.Selection.DOCUMENT,
                IntentGatedPrompt.select("生成 DOCX 报告", documentTools));
        Assert.assertEquals(IntentGatedPrompt.Selection.CHART,
                IntentGatedPrompt.select("解释饼图的含义", documentTools));
        Assert.assertEquals(IntentGatedPrompt.Selection.NONE,
                IntentGatedPrompt.select("生成带饼图的 PDF 报告", new ToolCollection()));

        ToolCollection canvasTools = new ToolCollection();
        canvasTools.addTool(tool("emit_ui_tree"));
        Assert.assertEquals(IntentGatedPrompt.Selection.CANVAS_WITH_CHARTS,
                IntentGatedPrompt.select("生成销售 KPI 看板和柱状图", canvasTools));
    }

    @Test
    public void intentPolicyUsesSeparateFrozenSystemVariant() {
        String template = ToolCallPrompt.ensureUserFacingReplyContract("base");
        ToolCollection tools = new ToolCollection();
        tools.addTool(tool("document_generate"));

        String normal = assemble(template, "sess-intent", "react", "", "普通问答", tools);
        String document = assemble(template, "sess-intent", "react", "", "生成 PDF 报告", tools);

        Assert.assertFalse(normal.contains("# 文档生成策略"));
        Assert.assertTrue(document.contains("# 文档生成策略"));
    }

    @Test
    public void canvasPolicyRequiresCanvasCapability() {
        ToolCollection canvasTools = new ToolCollection();
        canvasTools.addTool(tool("emit_ui_tree"));

        Assert.assertEquals(IntentGatedPrompt.Selection.CANVAS,
                IntentGatedPrompt.select("设计一个销售看板", canvasTools));
        Assert.assertEquals(IntentGatedPrompt.Selection.NONE,
                IntentGatedPrompt.select("设计一个销售看板", new ToolCollection()));
    }

    private static int countMarker(String s) {
        int n = 0;
        int i = 0;
        while ((i = s.indexOf(ToolCallPrompt.USER_FACING_REPLY_CONTRACT_MARKER, i)) >= 0) {
            n++;
            i += ToolCallPrompt.USER_FACING_REPLY_CONTRACT_MARKER.length();
        }
        return n;
    }

    private static String assemble(String rawTemplate, String sessionId, String agentName, String basePrompt) {
        return assemble(rawTemplate, sessionId, agentName, basePrompt, null, new ToolCollection());
    }

    private static String assemble(String rawTemplate, String sessionId, String agentName, String basePrompt,
                                   String query, ToolCollection tools) {
        String template = ToolCallPrompt.ensureUserFacingReplyContract(rawTemplate);
        ProbeAgent agent = new ProbeAgent();
        agent.setName(agentName);
        agent.setContext(AgentContext.builder()
                .sessionId(sessionId)
                .basePrompt(basePrompt)
                .query(query)
                .toolCollection(tools)
                .build());
        return agent.exposeStable(template);
    }

    private static BaseTool tool(String name) {
        return new BaseTool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return name;
            }

            @Override
            public Map<String, Object> toParams() {
                return Map.of();
            }

            @Override
            public Object execute(Object input) {
                return null;
            }
        };
    }

    private static final class ProbeAgent extends BaseAgent {
        @Override
        public String step() {
            return "";
        }

        String exposeStable(String template) {
            return buildStableSystemPrompt(template);
        }
    }
}
