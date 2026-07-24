package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.prompt.ToolCallPrompt;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

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
        String template = ToolCallPrompt.ensureUserFacingReplyContract(rawTemplate);
        ProbeAgent agent = new ProbeAgent();
        agent.setName(agentName);
        agent.setContext(AgentContext.builder()
                .sessionId(sessionId)
                .basePrompt(basePrompt)
                .toolCollection(new ToolCollection())
                .build());
        return agent.exposeStable(template);
    }

    private static final class ProbeAgent extends BaseAgent {
        @Override
        public String step() {
            return "";
        }

        String exposeStable(String template) {
            return buildStableSystemPrompt(template, "ignored-tools-text", null, null);
        }
    }
}
