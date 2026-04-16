package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageEventDao;
import org.wwz.ai.domain.agent.reactor.model.multi.OrderedEvent;
import org.wwz.ai.domain.agent.reactor.service.impl.AgentMessageEventServiceImpl;
import org.wwz.ai.domain.agent.reactor.service.impl.AgentMessageServiceImpl;

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
        public Integer queryMaxSortOrder(Long conversationId) {
            return 0;
        }

        @Override
        public int softDeleteByConversationId(Long conversationId) {
            return 0;
        }
    }
}
