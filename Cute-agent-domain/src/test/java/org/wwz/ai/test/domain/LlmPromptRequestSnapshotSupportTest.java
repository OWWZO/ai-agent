package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.llm.LlmPromptRequestSnapshotSupport;

import java.util.ArrayList;
import java.util.List;

/**
 * prompt 请求快照辅助测试。
 */
public class LlmPromptRequestSnapshotSupportTest {

    @Test
    public void shouldCollapseMultipleSystemMessagesForObservability() {
        List<Message> systemMessages = new ArrayList<>();
        systemMessages.add(Message.systemMessage("系统提示一", null));
        systemMessages.add(null);
        systemMessages.add(Message.systemMessage("系统提示二", null));

        Message merged = LlmPromptRequestSnapshotSupport.collapseSystemMessages(systemMessages);

        Assert.assertNotNull(merged);
        Assert.assertEquals("系统提示一\n\n系统提示二", merged.getContent());
        Assert.assertEquals(Message.systemMessage("", null).getRole(), merged.getRole());
    }

    @Test
    public void shouldKeepSingleSystemMessageAsIs() {
        Message original = Message.systemMessage("唯一提示", null);
        Message merged = LlmPromptRequestSnapshotSupport.collapseSystemMessages(List.of(original));

        Assert.assertSame(original, merged);
    }
}
