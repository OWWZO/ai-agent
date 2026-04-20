package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentSessionMemory;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageEventDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentSessionMemoryDao;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryDecisionType;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryPreparationResult;
import org.wwz.ai.domain.agent.reactor.model.multi.OrderedEvent;
import org.wwz.ai.domain.agent.reactor.service.impl.AgentSessionMemoryServiceImpl;
import org.wwz.ai.domain.agent.reactor.service.impl.AgentMessageEventServiceImpl;
import org.wwz.ai.domain.agent.reactor.service.impl.AgentMessageServiceImpl;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemoryCompactionService;
import org.wwz.ai.domain.agent.reactor.service.support.SessionWorkingMemoryAssembler;
import org.wwz.ai.domain.agent.reactor.service.support.SessionArtifactRestoreSupport;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemorySummaryGenerator;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemoryPromptFormatter;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemorySummaryBuilder;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemoryTokenEstimator;
import org.wwz.ai.domain.agent.reactor.service.support.SessionTranscriptBlockAssembler;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ConversationHistoryPersistenceTest {

    @Test
    public void test_persistEventsOnlyUsesMessageParentContext() {
        AgentMessageEventServiceImpl service = new AgentMessageEventServiceImpl();
        AtomicReference<List<AgentMessageEvent>> inserted = new AtomicReference<>();
        AtomicReference<Long> deletedMessageId = new AtomicReference<>();
        ReflectionTestUtils.setField(service, "messageEventDao", new StubMessageEventDao(inserted, deletedMessageId));

        service.persistEvents(List.of(
                OrderedEvent.builder()
                        .seqNo(1)
                        .eventType("deep_search")
                        .eventSubType("search")
                        .displayArea("timeline")
                        .taskId("task-1")
                        .title("检索：销量波动")
                        .contentText("已整理 2 条发现")
                        .payloadJson("{\"messageType\":\"task\",\"messageId\":\"search-1\"}")
                        .eventTime(LocalDateTime.now())
                        .build()
        ), 1001L, "completed");

        Assert.assertEquals(Long.valueOf(1001L), deletedMessageId.get());
        Assert.assertEquals(1, inserted.get().size());
        AgentMessageEvent event = inserted.get().get(0);
        Assert.assertEquals(Long.valueOf(1001L), event.getMessageId());
        Assert.assertEquals(Integer.valueOf(1), event.getSeqNo());
        Assert.assertEquals("completed", event.getStatus());
        Assert.assertEquals("task-1", event.getTaskId());
    }

    @Test
    public void test_persistEventsKeepsMultipleSameTypeFinalDetails() {
        AgentMessageEventServiceImpl service = new AgentMessageEventServiceImpl();
        AtomicReference<List<AgentMessageEvent>> inserted = new AtomicReference<>();
        ReflectionTestUtils.setField(service, "messageEventDao", new StubMessageEventDao(inserted, new AtomicReference<>()));

        service.persistEvents(List.of(
                OrderedEvent.builder()
                        .seqNo(1)
                        .eventType("deep_search")
                        .eventSubType("search")
                        .taskId("task-1")
                        .title("检索：华东销量")
                        .contentText("发现区域异常")
                        .payloadJson("{\"messageType\":\"task\",\"messageId\":\"search-1\"}")
                        .build(),
                OrderedEvent.builder()
                        .seqNo(2)
                        .eventType("deep_search")
                        .eventSubType("search")
                        .taskId("task-1")
                        .title("检索：促销活动")
                        .contentText("发现价格波动")
                        .payloadJson("{\"messageType\":\"task\",\"messageId\":\"search-2\"}")
                        .build()
        ), 1002L, "completed");

        Assert.assertEquals(2, inserted.get().size());
        Assert.assertTrue(inserted.get().get(0).getPayloadJson().contains("search-1"));
        Assert.assertTrue(inserted.get().get(1).getPayloadJson().contains("search-2"));
    }

    @Test
    public void test_completeMessageOnlyWritesLedgerFields() {
        AgentMessageServiceImpl service = new AgentMessageServiceImpl();
        AtomicReference<AgentMessage> updated = new AtomicReference<>();
        ReflectionTestUtils.setField(service, "messageDao", new StubMessageDao(updated));

        service.completeMessage(88L, "最终答案", "{\"event_count\":2}");

        AgentMessage message = updated.get();
        Assert.assertEquals(Long.valueOf(88L), message.getId());
        Assert.assertEquals("最终答案", message.getResponse());
        Assert.assertEquals("{\"event_count\":2}", message.getMetricsJson());
        Assert.assertEquals(Integer.valueOf(1), message.getStatus());
    }

    @Test
    public void test_prepareForRequestBypassesWhenNoCompletedTurns() {
        AgentSessionMemoryServiceImpl service = new AgentSessionMemoryServiceImpl();
        AtomicReference<AgentSessionMemory> insertedSnapshot = new AtomicReference<>();
        ReflectionTestUtils.setField(service, "reactorConfig", buildSessionMemoryConfig());
        ReflectionTestUtils.setField(service, "sessionMemoryDao", new StubSessionMemoryDao(insertedSnapshot));
        ReflectionTestUtils.setField(service, "messageDao", new StubMessageDao(new AtomicReference<>()) {
            @Override
            public List<AgentMessage> queryCompletedByConversationId(Long conversationId) {
                return List.of();
            }
        });
        ReflectionTestUtils.setField(service, "messageEventDao", new StubMessageEventDao(new AtomicReference<>(), new AtomicReference<>()));
        ReflectionTestUtils.setField(service, "workingMemoryAssembler", buildWorkingMemoryAssembler());
        ReflectionTestUtils.setField(service, "compactionService", buildCompactionService());

        SessionMemoryPreparationResult result = service.prepareForRequest(AgentConversation.builder()
                .id(1L)
                .sessionId("sess-err-001")
                .agentType(2)
                .build());

        Assert.assertEquals(SessionMemoryDecisionType.BYPASS, result.getDecisionType());
        Assert.assertEquals(Integer.valueOf(0), result.getEstimatedTokens());
        Assert.assertNull(insertedSnapshot.get());
    }

    @Test
    public void test_compactionArtifactsComeFromNormalizedPayload() throws Exception {
        SessionMemoryCompactionService compactionService = buildCompactionService();
        AgentConversation conversation = AgentConversation.builder()
                .id(1L)
                .sessionId("sess-artifact-001")
                .agentType(2)
                .build();
        AgentMessage message = AgentMessage.builder()
                .id(11L)
                .conversationId(1L)
                .requestId("req-artifact")
                .sortOrder(0)
                .query("整理报告")
                .response("报告已生成")
                .status(1)
                .agentType(2)
                .build();
        AgentMessageEvent event = AgentMessageEvent.builder()
                .messageId(11L)
                .seqNo(1)
                .payloadJson("""
                        {"messageType":"task","resultMap":{"resultMap":{"fileInfo":[{"fileName":"normalized.html","downloadUrl":"https://file.example.com/normalized","domainUrl":"https://file.example.com/normalized","resourceKey":"normalized"}]}}}
                        """)
                .status("completed")
                .build();

        SessionMemoryCompactionService.CompactionResult result = compactionService.compact(
                conversation,
                null,
                List.of(
                        message,
                        AgentMessage.builder()
                                .id(12L)
                                .conversationId(1L)
                                .requestId("req-artifact-2")
                                .sortOrder(1)
                                .query("继续整理")
                                .response("继续完成")
                                .status(1)
                                .agentType(2)
                                .build(),
                        AgentMessage.builder()
                                .id(13L)
                                .conversationId(1L)
                                .requestId("req-artifact-3")
                                .sortOrder(2)
                                .query("再补一段")
                                .response("再补一段完成")
                                .status(1)
                                .agentType(2)
                                .build()),
                java.util.Map.of(11L, List.of(event)));

        Assert.assertNotNull(result);
        Assert.assertTrue(result.getArtifactRefsJson().contains("normalized.html"));
        Assert.assertFalse(result.getArtifactRefsJson().contains("fileInfo"));
    }

    private ReactorConfig buildSessionMemoryConfig() {
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "sessionMemoryEnabled", true);
        ReflectionTestUtils.setField(config, "sessionMemoryCompactionThresholdTokens", 1);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowTurns", 2);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowMinMessages", 2);
        ReflectionTestUtils.setField(config, "sessionMemoryRecentWindowMaxTokens", 30);
        ReflectionTestUtils.setField(config, "sessionMemoryHardLimitTokens", 2000);
        ReflectionTestUtils.setField(config, "sessionMemoryMaxConsecutiveFailures", 3);
        ReflectionTestUtils.setField(config, "sessionMemoryCircuitOpenSeconds", 600);
        ReflectionTestUtils.setField(config, "sessionMemorySummaryMaxLength", 800);
        return config;
    }

    private SessionMemoryCompactionService buildCompactionService() {
        SessionMemoryCompactionService service = new SessionMemoryCompactionService();
        ReflectionTestUtils.setField(service, "reactorConfig", buildSessionMemoryConfig());
        ReflectionTestUtils.setField(service, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        ReflectionTestUtils.setField(service, "summaryBuilder", new SessionMemorySummaryBuilder());
        ReflectionTestUtils.setField(service, "summaryGenerator", new StubSummaryGenerator());
        SessionTranscriptBlockAssembler transcriptBlockAssembler = new SessionTranscriptBlockAssembler();
        ReflectionTestUtils.setField(transcriptBlockAssembler, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        ReflectionTestUtils.setField(service, "transcriptBlockAssembler", transcriptBlockAssembler);
        ReflectionTestUtils.setField(service, "tokenEstimator", new SessionMemoryTokenEstimator());
        return service;
    }

    private SessionWorkingMemoryAssembler buildWorkingMemoryAssembler() {
        SessionWorkingMemoryAssembler assembler = new SessionWorkingMemoryAssembler();
        ReflectionTestUtils.setField(assembler, "reactorConfig", buildSessionMemoryConfig());
        ReflectionTestUtils.setField(assembler, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        ReflectionTestUtils.setField(assembler, "promptFormatter", new SessionMemoryPromptFormatter());
        SessionTranscriptBlockAssembler transcriptBlockAssembler = new SessionTranscriptBlockAssembler();
        ReflectionTestUtils.setField(transcriptBlockAssembler, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        ReflectionTestUtils.setField(assembler, "transcriptBlockAssembler", transcriptBlockAssembler);
        ReflectionTestUtils.setField(assembler, "tokenEstimator", new SessionMemoryTokenEstimator());
        return assembler;
    }

    private static class StubSessionMemoryDao implements IAgentSessionMemoryDao {

        private final AtomicReference<AgentSessionMemory> inserted;

        private StubSessionMemoryDao(AtomicReference<AgentSessionMemory> inserted) {
            this.inserted = inserted;
        }

        @Override
        public int insert(AgentSessionMemory sessionMemory) {
            inserted.set(sessionMemory);
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

    private static class StubMessageEventDao implements IAgentMessageEventDao {

        private final AtomicReference<List<AgentMessageEvent>> inserted;
        private final AtomicReference<Long> deletedMessageId;

        private StubMessageEventDao(AtomicReference<List<AgentMessageEvent>> inserted,
                                    AtomicReference<Long> deletedMessageId) {
            this.inserted = inserted;
            this.deletedMessageId = deletedMessageId;
        }

        @Override
        public int batchInsert(List<AgentMessageEvent> events) {
            inserted.set(events);
            return events.size();
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
            deletedMessageId.set(messageId);
            return 0;
        }
    }

    private static class StubMessageDao implements IAgentMessageDao {

        private final AtomicReference<AgentMessage> updated;

        private StubMessageDao(AtomicReference<AgentMessage> updated) {
            this.updated = updated;
        }

        @Override
        public int insert(AgentMessage message) {
            return 0;
        }

        @Override
        public int updateById(AgentMessage message) {
            updated.set(message);
            return 1;
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
            return List.of();
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

    private static class StubSummaryGenerator implements SessionMemorySummaryGenerator {
        @Override
        public String generate(GenerationRequest request) {
            return """
                    # Session Title
                    历史任务压缩

                    # Current State
                    继续推进当前任务

                    # Task specification
                    保留用户需求

                    # Files and Functions

                    # Workflow

                    # Errors & Corrections

                    # Codebase and System Documentation

                    # Learnings

                    # Key results

                    # Worklog
                    已完成一轮压缩
                    """;
        }
    }
}
