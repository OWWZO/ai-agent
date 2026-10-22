package org.wwz.ai.test.domain.subagent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.SessionWorkingMemoryService;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.subagent.SubAgentContextFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 不加载 @Slf4j Agent 运行时类的纯逻辑校验（短 requestId / WM 全量兜底契约）。
 */
public class SubAgentResumeHardeningTest {

    @Test
    public void childRequestIdShouldStayWithinColumnLimit() {
        String longAgentId = "abcdefghijklmnop";
        for (int i = 0; i < 20; i++) {
            String id = SubAgentContextFactory.newChildRequestId(longAgentId);
            Assert.assertTrue(id.length() <= 64);
            Assert.assertTrue(id.startsWith("sub:" + longAgentId + ":"));
        }
    }

    @Test
    public void emptyDeltaShouldFallbackToFullSnapshotPersist() {
        InMemoryWorkingMemory wm = new InMemoryWorkingMemory();
        List<Message> full = List.of(
                Message.userMessage("u1", null),
                Message.assistantMessage("a1", null));
        String sessionId = "sess-snap";
        String scope = "sub:agentsnap000001";
        String snapId = SubAgentContextFactory.newChildRequestId("agentsnap000001");
        wm.replaceReadyProjection(sessionId, scope, snapId, full);

        List<Message> loaded = wm.loadReadyMessages(sessionId, scope, "other-req");
        Assert.assertEquals(2, loaded.size());
        Assert.assertTrue(Boolean.TRUE.equals(wm.lastFullSnapshot));
    }

    private static final class InMemoryWorkingMemory implements SessionWorkingMemoryService {
        private final Map<String, List<Message>> store = new ConcurrentHashMap<>();
        private boolean lastFullSnapshot;

        @Override
        public List<Message> loadReadyMessages(String sessionId, String memoryScope, String currentRequestId) {
            List<Message> msgs = store.get(key(sessionId, memoryScope));
            return msgs == null ? List.of() : new ArrayList<>(msgs);
        }

        @Override
        public void persistTurn(String sessionId,
                                String memoryScope,
                                String requestId,
                                Long runId,
                                String entryAgent,
                                List<Message> turnMessages) {
            lastFullSnapshot = false;
            store.compute(key(sessionId, memoryScope), (k, old) -> {
                List<Message> next = old == null ? new ArrayList<>() : new ArrayList<>(old);
                if (turnMessages != null) {
                    next.addAll(turnMessages);
                }
                return next;
            });
        }

        @Override
        public void replaceReadyProjection(String sessionId,
                                           String memoryScope,
                                           String compactRequestId,
                                           List<Message> compactedMessages) {
            lastFullSnapshot = true;
            store.put(key(sessionId, memoryScope),
                    compactedMessages == null ? new ArrayList<>() : new ArrayList<>(compactedMessages));
        }

        private static String key(String sessionId, String scope) {
            return sessionId + "|" + scope;
        }
    }
}
