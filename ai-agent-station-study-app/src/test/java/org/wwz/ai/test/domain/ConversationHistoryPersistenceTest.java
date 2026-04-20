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
import org.wwz.ai.domain.agent.reactor.model.multi.OrderedEvent;
import org.wwz.ai.domain.agent.reactor.service.impl.AgentSessionMemoryServiceImpl;
import org.wwz.ai.domain.agent.reactor.service.impl.AgentMessageEventServiceImpl;
import org.wwz.ai.domain.agent.reactor.service.impl.AgentMessageServiceImpl;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemoryCompactionService;
import org.wwz.ai.domain.agent.reactor.service.support.SessionWorkingMemoryAssembler;
import org.wwz.ai.domain.agent.reactor.service.support.SessionArtifactRestoreSupport;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemoryPromptFormatter;
import org.wwz.ai.domain.agent.reactor.service.support.SessionMemorySummaryBuilder;
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
    public void test_refreshSessionMemorySkipsWhenNoCompletedTurns() {
        AgentSessionMemoryServiceImpl service = new AgentSessionMemoryServiceImpl();
        AtomicReference<AgentSessionMemory> upserted = new AtomicReference<>();
        ReflectionTestUtils.setField(service, "reactorConfig", buildSessionMemoryConfig());
        ReflectionTestUtils.setField(service, "sessionMemoryDao", new StubSessionMemoryDao(upserted));
        ReflectionTestUtils.setField(service, "messageDao", new StubMessageDao(new AtomicReference<>()) {
            @Override
            public List<AgentMessage> queryCompletedByConversationId(Long conversationId) {
                return List.of();
            }
        });
        ReflectionTestUtils.setField(service, "messageEventDao", new StubMessageEventDao(new AtomicReference<>(), new AtomicReference<>()));
        ReflectionTestUtils.setField(service, "workingMemoryAssembler", new SessionWorkingMemoryAssembler());
        ReflectionTestUtils.setField(service, "compactionService", buildCompactionService());

        service.refreshSessionMemory(AgentConversation.builder()
                .id(1L)
                .sessionId("sess-err-001")
                .agentType(2)
                .build());

        Assert.assertNull(upserted.get());
    }

    @Test
    public void test_compactionArtifactsComeFromNormalizedPayload() {
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
        ReflectionTestUtils.setField(config, "sessionMemorySummaryMaxLength", 800);
        return config;
    }

    private SessionMemoryCompactionService buildCompactionService() {
        SessionMemoryCompactionService service = new SessionMemoryCompactionService();
        ReflectionTestUtils.setField(service, "reactorConfig", buildSessionMemoryConfig());
        ReflectionTestUtils.setField(service, "artifactRestoreSupport", new SessionArtifactRestoreSupport());
        ReflectionTestUtils.setField(service, "summaryBuilder", new SessionMemorySummaryBuilder());
        return service;
    }

    private static class StubSessionMemoryDao implements IAgentSessionMemoryDao {

        private final AtomicReference<AgentSessionMemory> upserted;

        private StubSessionMemoryDao(AtomicReference<AgentSessionMemory> upserted) {
            this.upserted = upserted;
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
            return null;
        }

        @Override
        public int upsert(AgentSessionMemory sessionMemory) {
            upserted.set(sessionMemory);
            return 1;
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
}
