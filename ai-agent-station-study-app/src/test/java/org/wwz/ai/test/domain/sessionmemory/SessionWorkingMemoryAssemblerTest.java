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
import org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory;
import org.wwz.ai.domain.agent.reactor.service.support.SessionArtifactRestoreSupport;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemoryTokenEstimator;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemoryPromptFormatter;
import org.wwz.ai.domain.agent.reactor.service.support.SessionTranscriptBlockAssembler;
import org.wwz.ai.domain.agent.reactor.service.support.SessionWorkingMemoryAssembler;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SessionWorkingMemoryAssemblerTest {

    @Test
    public void test_rebuildsSummaryRecentTurnsAndRestoredFilesWithSnapshot() {
        SessionWorkingMemoryAssembler assembler = new SessionWorkingMemoryAssembler();
        StubSessionMemoryDao sessionMemoryDao = new StubSessionMemoryDao(SessionMemoryFixtureFactory.buildSnapshot());
        StubMessageDao messageDao = new StubMessageDao(SessionMemoryFixtureFactory.buildRecentWindowMessages());
        StubMessageEventDao eventDao = new StubMessageEventDao(SessionMemoryFixtureFactory.buildRecentWindowEvents());

        ReflectionTestUtils.setField(assembler, "reactorConfig", buildConfig());
        ReflectionTestUtils.setField(assembler, "sessionMemoryDao", sessionMemoryDao);
        ReflectionTestUtils.setField(assembler, "messageDao", messageDao);
        ReflectionTestUtils.setField(assembler, "messageEventDao", eventDao);
        ReflectionTestUtils.setField(assembler, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        ReflectionTestUtils.setField(assembler, "promptFormatter", new SessionMemoryPromptFormatter());
        ReflectionTestUtils.setField(assembler, "transcriptBlockAssembler", buildTranscriptAssembler());
        ReflectionTestUtils.setField(assembler, "tokenEstimator", new SessionMemoryTokenEstimator());

        SessionWorkingMemory workingMemory = assembler.assemble(buildConversation());

        Assert.assertEquals("用户要求后续输出都使用中文表格。", workingMemory.getSummaryText());
        Assert.assertEquals(Integer.valueOf(0), Integer.valueOf(workingMemory.getFacts().size()));
        Assert.assertEquals(Integer.valueOf(2), Integer.valueOf(workingMemory.getRecentTurns().size()));
        Assert.assertTrue(workingMemory.getRecentTurns().get(0).getBlocks().stream()
                .anyMatch(block -> block != null && "TOOL_USE".equals(String.valueOf(block.getBlockType()))));
        Assert.assertTrue(workingMemory.getRecentTurns().get(0).getBlocks().stream()
                .anyMatch(block -> block != null && Boolean.TRUE.equals(block.getReferenceOnly())));
        SessionMemoryTestSupport.assertFileNames(
                workingMemory.getRestoredFiles(),
                "existing-report.html",
                "uploaded-spec.pdf",
                "summary-report.html");
        Assert.assertTrue(workingMemory.getHistoryDialogue().contains("历史摘要"));
        Assert.assertTrue(workingMemory.getHistoryDialogue().contains("中文表格"));
        Assert.assertEquals(1, sessionMemoryDao.queryCount.get());
        Assert.assertEquals(1, messageDao.queryCompletedCount.get());
        Assert.assertEquals(1, eventDao.queryFinalBatchCount.get());
    }

    @Test
    public void test_rebuildFallsBackToRecentTurnsWithoutSnapshot() {
        SessionWorkingMemoryAssembler assembler = new SessionWorkingMemoryAssembler();
        StubSessionMemoryDao sessionMemoryDao = new StubSessionMemoryDao(null);
        StubMessageDao messageDao = new StubMessageDao(List.of(
                SessionMemoryTestSupport.completedMessage(
                        201L,
                        "req-memory-101",
                        0,
                        "先分析 2025 年 Agent 趋势",
                        "我先给出趋势概览。",
                        null)));
        StubMessageEventDao eventDao = new StubMessageEventDao(List.of());

        ReflectionTestUtils.setField(assembler, "reactorConfig", buildConfig());
        ReflectionTestUtils.setField(assembler, "sessionMemoryDao", sessionMemoryDao);
        ReflectionTestUtils.setField(assembler, "messageDao", messageDao);
        ReflectionTestUtils.setField(assembler, "messageEventDao", eventDao);
        ReflectionTestUtils.setField(assembler, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        ReflectionTestUtils.setField(assembler, "promptFormatter", new SessionMemoryPromptFormatter());
        ReflectionTestUtils.setField(assembler, "transcriptBlockAssembler", buildTranscriptAssembler());
        ReflectionTestUtils.setField(assembler, "tokenEstimator", new SessionMemoryTokenEstimator());

        SessionWorkingMemory workingMemory = assembler.assemble(buildConversation());

        Assert.assertNull(workingMemory.getSummaryText());
        Assert.assertEquals(Integer.valueOf(-1), workingMemory.getBoundarySortOrder());
        Assert.assertTrue(workingMemory.getHistoryDialogue().contains("最近对话片段"));
        Assert.assertEquals(1, workingMemory.getRecentTurns().size());
        Assert.assertEquals(1, sessionMemoryDao.queryCount.get());
        Assert.assertEquals(1, messageDao.queryCompletedCount.get());
        Assert.assertEquals(1, eventDao.queryFinalBatchCount.get());
    }

    private SessionTranscriptBlockAssembler buildTranscriptAssembler() {
        SessionTranscriptBlockAssembler assembler = new SessionTranscriptBlockAssembler();
        ReflectionTestUtils.setField(assembler, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        return assembler;
    }

    private ReactorConfig buildConfig() {
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "sessionMemoryCompactionThresholdTokens", 12000);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowTurns", 2);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowMinMessages", 2);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowMaxTokens", 4000);
        ReflectionTestUtils.setField(config, "sessionMemorySummaryMaxLength", 4000);
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

        private final AgentSessionMemory snapshot;
        private final AtomicInteger queryCount = new AtomicInteger();

        private StubSessionMemoryDao(AgentSessionMemory snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public int insert(AgentSessionMemory sessionMemory) {
            return 0;
        }

        @Override
        public int updateById(AgentSessionMemory sessionMemory) {
            return 0;
        }

        @Override
        public AgentSessionMemory queryBySessionId(String sessionId) {
            queryCount.incrementAndGet();
            return snapshot;
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

        private final List<AgentMessage> recentMessages;
        private final AtomicInteger queryCompletedCount = new AtomicInteger();

        private StubMessageDao(List<AgentMessage> recentMessages) {
            this.recentMessages = recentMessages;
        }

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
            return recentMessages;
        }

        @Override
        public List<AgentMessage> queryCompletedByConversationId(Long conversationId) {
            queryCompletedCount.incrementAndGet();
            return recentMessages;
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

        private final List<AgentMessageEvent> events;
        private final AtomicInteger queryFinalBatchCount = new AtomicInteger();

        private StubMessageEventDao(List<AgentMessageEvent> events) {
            this.events = events;
        }

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
            return events;
        }

        @Override
        public List<AgentMessageEvent> queryArtifactEventsByMessageIds(List<Long> messageIds) {
            return events;
        }

        @Override
        public List<AgentMessageEvent> queryFinalEventsByMessageIds(List<Long> messageIds) {
            queryFinalBatchCount.incrementAndGet();
            return events;
        }

        @Override
        public int deleteByMessageId(Long messageId) {
            return 0;
        }
    }
}
