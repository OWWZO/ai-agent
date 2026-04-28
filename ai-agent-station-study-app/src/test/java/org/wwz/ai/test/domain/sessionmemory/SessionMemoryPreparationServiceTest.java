package org.wwz.ai.test.domain.sessionmemory;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.entity.AgentSessionMemory;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageEventDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentSessionMemoryDao;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryDecisionType;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryPreparationResult;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory;
import org.wwz.ai.domain.agent.reactor.service.impl.AgentSessionMemoryServiceImpl;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemoryCompactionService;
import org.wwz.ai.domain.agent.reactor.service.support.SessionWorkingMemoryAssembler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SessionMemoryPreparationServiceTest {

    @Test
    public void test_prepareForRequestReturnsBypassWhenBelowThreshold() {
        StubSessionMemoryDao sessionMemoryDao = new StubSessionMemoryDao();
        StubWorkingMemoryAssembler assembler = new StubWorkingMemoryAssembler(
                SessionWorkingMemory.builder().historyDialogue("").estimatedTokens(600).build());
        AgentSessionMemoryServiceImpl service = buildService(
                sessionMemoryDao,
                assembler,
                new StubCompactionService());

        SessionMemoryPreparationResult result = service.prepareForRequest(buildConversation());

        Assert.assertEquals(SessionMemoryDecisionType.BYPASS, result.getDecisionType());
        Assert.assertEquals(Integer.valueOf(600), result.getEstimatedTokens());
        Assert.assertEquals(0, sessionMemoryDao.insertCount.get());
        Assert.assertEquals(1, assembler.buildFactEventMapCount.get());
    }

    @Test
    public void test_prepareForRequestReturnsCompactedWhenCompactionSucceeds() {
        StubSessionMemoryDao sessionMemoryDao = new StubSessionMemoryDao();
        StubWorkingMemoryAssembler assembler = new StubWorkingMemoryAssembler(
                SessionWorkingMemory.builder().historyDialogue("before").estimatedTokens(1500).build(),
                SessionWorkingMemory.builder().historyDialogue("after").estimatedTokens(700).build());
        StubCompactionService compactionService = new StubCompactionService();
        compactionService.result = SessionMemoryCompactionService.CompactionResult.builder()
                .conversationId(SessionMemoryTestSupport.CONVERSATION_ID)
                .sessionId(SessionMemoryTestSupport.SESSION_ID)
                .agentType(2)
                .summaryText("# Session Title\n压缩后的记忆")
                .artifactRefsJson("[]")
                .boundarySortOrder(0)
                .sourceTurnCount(1)
                .postCompactionTokens(700)
                .build();
        AgentSessionMemoryServiceImpl service = buildService(sessionMemoryDao, assembler, compactionService);

        SessionMemoryPreparationResult result = service.prepareForRequest(buildConversation());

        Assert.assertEquals(SessionMemoryDecisionType.COMPACTED, result.getDecisionType());
        Assert.assertEquals(Integer.valueOf(1500), result.getEstimatedTokens());
        Assert.assertEquals(Integer.valueOf(700), result.getPostCompactionTokens());
        Assert.assertEquals(1, sessionMemoryDao.insertCount.get());
        Assert.assertEquals(Long.valueOf(9001L), result.getSnapshotVersionId());
    }

    @Test
    public void test_prepareForRequestReturnsDegradedContinueWhenCompactionFailsUnderHardLimit() {
        StubSessionMemoryDao sessionMemoryDao = new StubSessionMemoryDao();
        StubWorkingMemoryAssembler assembler = new StubWorkingMemoryAssembler(
                SessionWorkingMemory.builder().historyDialogue("before").estimatedTokens(1500).build());
        StubCompactionService compactionService = new StubCompactionService();
        compactionService.throwException = true;
        AgentSessionMemoryServiceImpl service = buildService(sessionMemoryDao, assembler, compactionService);

        SessionMemoryPreparationResult result = service.prepareForRequest(buildConversation());

        Assert.assertEquals(SessionMemoryDecisionType.DEGRADED_CONTINUE, result.getDecisionType());
        Assert.assertEquals(Integer.valueOf(1500), result.getEstimatedTokens());
        Assert.assertEquals(0, sessionMemoryDao.insertCount.get());
    }

    @Test
    public void test_prepareForRequestReturnsRejectedWhenCompactionFailsOverHardLimit() {
        StubSessionMemoryDao sessionMemoryDao = new StubSessionMemoryDao();
        StubWorkingMemoryAssembler assembler = new StubWorkingMemoryAssembler(
                SessionWorkingMemory.builder().historyDialogue("before").estimatedTokens(2600).build());
        StubCompactionService compactionService = new StubCompactionService();
        compactionService.throwException = true;
        AgentSessionMemoryServiceImpl service = buildService(sessionMemoryDao, assembler, compactionService);

        SessionMemoryPreparationResult result = service.prepareForRequest(buildConversation());

        Assert.assertEquals(SessionMemoryDecisionType.REJECTED, result.getDecisionType());
        Assert.assertEquals(Integer.valueOf(2600), result.getEstimatedTokens());
        Assert.assertEquals(0, sessionMemoryDao.insertCount.get());
    }

    @Test
    public void test_prepareForRequestSkipsCompactionWhenCircuitOpen() {
        StubSessionMemoryDao sessionMemoryDao = new StubSessionMemoryDao();
        StubCompactionService compactionService = new StubCompactionService();
        compactionService.throwException = true;
        AgentSessionMemoryServiceImpl service = buildService(
                sessionMemoryDao,
                new StubWorkingMemoryAssembler(
                        SessionWorkingMemory.builder().historyDialogue("before").estimatedTokens(1500).build(),
                        SessionWorkingMemory.builder().historyDialogue("before").estimatedTokens(1500).build(),
                        SessionWorkingMemory.builder().historyDialogue("before").estimatedTokens(1500).build(),
                        SessionWorkingMemory.builder().historyDialogue("before").estimatedTokens(1500).build()),
                compactionService);

        service.prepareForRequest(buildConversation());
        service.prepareForRequest(buildConversation());
        service.prepareForRequest(buildConversation());
        SessionMemoryPreparationResult result = service.prepareForRequest(buildConversation());

        Assert.assertEquals(SessionMemoryDecisionType.SKIPPED_CIRCUIT_OPEN, result.getDecisionType());
        Assert.assertEquals(3, compactionService.callCount.get());
        Assert.assertEquals(0, sessionMemoryDao.insertCount.get());
    }

    private AgentSessionMemoryServiceImpl buildService(StubSessionMemoryDao sessionMemoryDao,
                                                       StubWorkingMemoryAssembler assembler,
                                                       StubCompactionService compactionService) {
        AgentSessionMemoryServiceImpl service = new AgentSessionMemoryServiceImpl();
        ReflectionTestUtils.setField(service, "reactorConfig", buildConfig());
        ReflectionTestUtils.setField(service, "sessionMemoryDao", sessionMemoryDao);
        ReflectionTestUtils.setField(service, "messageDao", new StubMessageDao());
        ReflectionTestUtils.setField(service, "workingMemoryAssembler", assembler);
        ReflectionTestUtils.setField(service, "compactionService", compactionService);
        return service;
    }

    private ReactorConfig buildConfig() {
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "sessionMemoryEnabled", true);
        ReflectionTestUtils.setField(config, "sessionMemoryCompactionThresholdTokens", 1000);
        ReflectionTestUtils.setField(config, "sessionMemoryHardLimitTokens", 2000);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowTurns", 2);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowMinMessages", 2);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowMaxTokens", 800);
        ReflectionTestUtils.setField(config, "sessionMemorySummaryMaxLength", 1200);
        ReflectionTestUtils.setField(config, "sessionMemoryMaxConsecutiveFailures", 3);
        ReflectionTestUtils.setField(config, "sessionMemoryCircuitOpenSeconds", 600);
        return config;
    }

    private AgentConversation buildConversation() {
        return AgentConversation.builder()
                .id(SessionMemoryTestSupport.CONVERSATION_ID)
                .sessionId(SessionMemoryTestSupport.SESSION_ID)
                .agentType(2)
                .build();
    }

    private static class StubSessionMemoryDao implements IAgentSessionMemoryDao {
        private final AtomicInteger insertCount = new AtomicInteger();

        @Override
        public int insert(AgentSessionMemory sessionMemory) {
            insertCount.incrementAndGet();
            sessionMemory.setId(9001L);
            return 1;
        }

        @Override
        public int updateById(AgentSessionMemory sessionMemory) {
            return 0;
        }

        @Override
        public AgentSessionMemory queryBySessionId(String sessionId) {
            return null;
        }

        @Override
        public List<AgentSessionMemory> queryHistoryBySessionId(String sessionId) {
            return List.of();
        }

        @Override
        public int softDeleteBySessionId(String sessionId) {
            return 0;
        }
    }

    private static class StubMessageDao implements IAgentMessageDao {
        @Override
        public int insert(AgentMessage message) {
            return 0;
        }

        @Override
        public int updateById(AgentMessage message) {
            return 0;
        }

        @Override
        public AgentMessage queryByRequestId(String requestId) {
            return null;
        }

        @Override
        public List<AgentMessage> queryByConversationId(Long conversationId) {
            return List.of();
        }

        @Override
        public List<AgentMessage> queryRecentCompleted(Long conversationId, int limit) {
            return List.of();
        }

        @Override
        public List<AgentMessage> queryCompletedAfterSortOrder(Long conversationId, Integer afterSortOrder, int limit) {
            return List.of();
        }

        @Override
        public List<AgentMessage> queryCompletedByConversationId(Long conversationId) {
            return List.of(SessionMemoryTestSupport.completedMessage(
                    101L,
                    "req-preparation",
                    0,
                    "继续任务",
                    "好的，继续任务",
                    null));
        }

        @Override
        public int countStreamingByConversationId(Long conversationId) {
            return 0;
        }

        @Override
        public Integer queryMaxSortOrder(Long conversationId) {
            return 0;
        }

        @Override
        public int softDeleteByConversationId(Long conversationId) {
            return 0;
        }
    }

    private static class StubMessageEventDao implements IAgentMessageEventDao {
        @Override
        public int batchInsert(List<AgentMessageEvent> events) {
            return 0;
        }

        @Override
        public List<AgentMessageEvent> queryByMessageId(Long messageId) {
            return List.of();
        }

        @Override
        public List<AgentMessageEvent> queryByMessageIds(List<Long> messageIds) {
            return List.of();
        }

        @Override
        public List<AgentMessageEvent> queryArtifactEventsByMessageIds(List<Long> messageIds) {
            return List.of();
        }

        @Override
        public List<AgentMessageEvent> queryFinalEventsByMessageIds(List<Long> messageIds) {
            return List.of();
        }

        @Override
        public int deleteByMessageId(Long messageId) {
            return 0;
        }
    }

    private static class StubWorkingMemoryAssembler extends SessionWorkingMemoryAssembler {
        private final Deque<SessionWorkingMemory> memories = new ArrayDeque<>();
        private final AtomicInteger buildFactEventMapCount = new AtomicInteger();

        private StubWorkingMemoryAssembler(SessionWorkingMemory... memories) {
            this.memories.addAll(List.of(memories));
        }

        @Override
        public Map<Long, List<AgentMessageEvent>> buildFactEventMap(List<AgentMessage> messages) {
            buildFactEventMapCount.incrementAndGet();
            return Map.of();
        }

        @Override
        public SessionWorkingMemory assemble(AgentConversation conversation,
                                             AgentSessionMemory snapshot,
                                             List<AgentMessage> completedMessages,
                                             Map<Long, List<AgentMessageEvent>> eventMap) {
            return this.memories.isEmpty()
                    ? SessionWorkingMemory.builder().historyDialogue("").estimatedTokens(0).build()
                    : this.memories.removeFirst();
        }
    }

    private static class StubCompactionService extends SessionMemoryCompactionService {
        private final AtomicInteger callCount = new AtomicInteger();
        private boolean throwException;
        private CompactionResult result;

        @Override
        public CompactionResult compact(AgentConversation conversation,
                                        AgentSessionMemory snapshot,
                                        List<AgentMessage> completedMessages,
                                        Map<Long, List<AgentMessageEvent>> eventMap) throws Exception {
            callCount.incrementAndGet();
            if (throwException) {
                throw new IllegalStateException("mock compaction failed");
            }
            return result;
        }
    }
}
