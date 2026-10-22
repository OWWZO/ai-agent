package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.prompt.AgentPrompt;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

/**
 * 同 session 多次组装 system 时字节必须稳定（prompt cache）。
 */
public class StableSystemPromptTest {

    @Test
    public void sameInputsYieldIdenticalSystemBytesAcrossConstructions() {
        String template = AgentPrompt.SYSTEM_PROMPT
                + "\r\n\r\n{{basePrompt}}\n\n"
                + "尾部说明  \n";

        String first = assemble(template, "sess-1", "react", "角色A");
        String second = assemble(template.replace("\n", "\r\n"), "sess-1", "react", "角色A");
        String third = assemble("\uFEFF" + template + "\n\n\n", "sess-1", "react", "角色A");

        Assert.assertEquals(first, second);
        Assert.assertEquals(first, third);
        Assert.assertTrue(first.contains("角色A"));
        Assert.assertTrue(first.contains(AgentPrompt.USER_FACING_REPLY_CONTRACT_MARKER));
        Assert.assertTrue(first.endsWith("\n"));
        // 行尾空白与连续空行被规范化掉
        Assert.assertFalse(first.contains("尾部说明  \n"));
        Assert.assertFalse(first.contains("\n\n\n"));
    }

    @Test
    public void queryAndToolsDoNotAffectSystemBytes() {
        String template = AgentPrompt.ensureUserFacingReplyContract("base");

        String plain = assemble(template, "sess-q", "react", "", "普通问答", new ToolCollection());
        String document = assemble(template, "sess-q", "react", "", "生成带饼图的 PDF 报告", new ToolCollection());

        // system 不再按 query 意图追加重型策略：不同问题必须拿到同一份 system。
        Assert.assertEquals(plain, document);
    }

    @Test
    public void ensureContractIsIdempotent() {
        String once = AgentPrompt.ensureUserFacingReplyContract("hello\r\nworld  ");
        String twice = AgentPrompt.ensureUserFacingReplyContract(once);
        Assert.assertEquals(once, twice);
        Assert.assertEquals(1, countMarker(once));
    }

    private static int countMarker(String s) {
        int n = 0;
        int i = 0;
        while ((i = s.indexOf(AgentPrompt.USER_FACING_REPLY_CONTRACT_MARKER, i)) >= 0) {
            n++;
            i += AgentPrompt.USER_FACING_REPLY_CONTRACT_MARKER.length();
        }
        return n;
    }

    private static String assemble(String rawTemplate, String sessionId, String agentName, String basePrompt) {
        return assemble(rawTemplate, sessionId, agentName, basePrompt, null, new ToolCollection());
    }

    private static String assemble(String rawTemplate, String sessionId, String agentName, String basePrompt,
                                   String query, ToolCollection tools) {
        String template = AgentPrompt.ensureUserFacingReplyContract(rawTemplate);
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
