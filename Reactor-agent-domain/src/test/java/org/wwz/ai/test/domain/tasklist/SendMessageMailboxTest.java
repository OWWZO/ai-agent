package org.wwz.ai.test.domain.tasklist;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.cancel.PendingInjectMessage;
import org.wwz.ai.domain.agent.runtime.tasklist.SessionAgentMailboxHub;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.TaskToolNames;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * SessionAgentMailboxHub 纯单元测试（避免 domain 侧 AgentContext/Slf4j 与 logback 冲突）。
 */
public class SendMessageMailboxTest {

    @After
    public void cleanup() {
        SessionAgentMailboxHub.clearAll();
    }

    @Test
    public void offerAndDrainQueue() {
        String session = "sess-mail";
        String agentId = "agent-1";
        SessionAgentMailboxHub.markActive(session, agentId, true);
        Assert.assertTrue(SessionAgentMailboxHub.isActive(session, agentId));

        int n = SessionAgentMailboxHub.offer(session, agentId, PendingInjectMessage.builder()
                .text("先只列目录")
                .source(PendingInjectMessage.SOURCE_COORDINATOR)
                .createdAtMs(System.currentTimeMillis())
                .build());
        Assert.assertEquals(1, n);

        ConcurrentLinkedQueue<PendingInjectMessage> q = SessionAgentMailboxHub.queue(session, agentId);
        Assert.assertEquals(1, q.size());
        Assert.assertEquals("先只列目录", q.poll().getText());
        Assert.assertEquals(PendingInjectMessage.SOURCE_COORDINATOR,
                PendingInjectMessage.builder()
                        .text("x")
                        .source(PendingInjectMessage.SOURCE_COORDINATOR)
                        .build()
                        .getSource());

        SessionAgentMailboxHub.markActive(session, agentId, false);
        Assert.assertFalse(SessionAgentMailboxHub.isActive(session, agentId));
    }

    @Test
    public void inactiveAfterMarkFalse() {
        SessionAgentMailboxHub.markActive("s", "a", true);
        SessionAgentMailboxHub.offer("s", "a", PendingInjectMessage.builder()
                .text("hi")
                .source(PendingInjectMessage.SOURCE_COORDINATOR)
                .createdAtMs(1L)
                .build());
        SessionAgentMailboxHub.markActive("s", "a", false);
        Assert.assertFalse(SessionAgentMailboxHub.isActive("s", "a"));
        // 队列仍可保留未消费消息
        Assert.assertEquals(1, SessionAgentMailboxHub.queue("s", "a").size());
    }

    @Test
    public void toolNameConstant() {
        Assert.assertEquals("SendMessage", TaskToolNames.SEND_MESSAGE);
    }
}
