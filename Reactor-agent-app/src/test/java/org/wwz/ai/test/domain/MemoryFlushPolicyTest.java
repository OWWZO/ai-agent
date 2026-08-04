package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.ltm.MemoryFlushPolicy;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;

import java.util.List;

public class MemoryFlushPolicyTest {

    @Test
    public void shouldFlushOnlyWhenThresholdAndCompacting() {
        Assert.assertFalse(MemoryFlushPolicy.shouldFlush(5, 6, true));
        Assert.assertTrue(MemoryFlushPolicy.shouldFlush(6, 6, true));
        Assert.assertFalse(MemoryFlushPolicy.shouldFlush(10, 6, false));
        Assert.assertFalse(MemoryFlushPolicy.shouldFlush(10, 0, true));
    }

    @Test
    public void countUserTurns() {
        List<Message> messages = List.of(
                Message.userMessage("a", null),
                Message.assistantMessage("b", null),
                Message.userMessage("c", null)
        );
        Assert.assertEquals(2, MemoryFlushPolicy.countUserTurns(messages));
    }

    @Test
    public void prependFlushNudge() {
        List<Message> messages = List.of(Message.userMessage("hello", null));
        List<Message> withNudge = MemoryFlushPolicy.prependFlushNudge(messages);
        Assert.assertEquals(2, withNudge.size());
        Assert.assertEquals(RoleType.USER, withNudge.get(0).getRole());
        Assert.assertTrue(withNudge.get(0).getContent().contains(MemoryFlushPolicy.FLUSH_NOTE_PREFIX));
        Assert.assertEquals("hello", withNudge.get(1).getContent());
    }
}
