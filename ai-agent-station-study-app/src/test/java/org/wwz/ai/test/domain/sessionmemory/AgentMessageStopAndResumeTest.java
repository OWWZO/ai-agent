package org.wwz.ai.test.domain.sessionmemory;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.model.multi.OrderedEvent;
import org.wwz.ai.domain.agent.reactor.service.IAgentMessageEventService;
import org.wwz.ai.domain.agent.reactor.service.IAgentMessageService;
import org.wwz.ai.domain.agent.reactor.service.IAgentSessionMemoryService;
import org.wwz.ai.domain.agent.reactor.service.impl.AgentStreamPersistServiceImpl;
import org.wwz.ai.domain.agent.reactor.service.support.ActiveSessionStreamRegistry;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentConversationDao;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryDecisionType;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryPreparationResult;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 停止请求与强制停止续聊语义测试
 */
public class AgentMessageStopAndResumeTest {

    @Test
    public void test_stopDelegatesToRegistryAndCancelsActiveCall() {
        ActiveSessionStreamRegistry registry = new ActiveSessionStreamRegistry();
        Call call = new OkHttpClient().newCall(
                new Request.Builder().url("http://127.0.0.1:65534/test-stop").build());
        registry.register(
                SessionMemoryTestSupport.SESSION_ID,
                SessionMemoryTestSupport.REQUEST_ID,
                11L,
                call,
                new SseEmitter());

        AgentStreamPersistServiceImpl service = new AgentStreamPersistServiceImpl();
        ReflectionTestUtils.setField(service, "activeSessionStreamRegistry", registry);

        boolean stopped = service.stop(SessionMemoryTestSupport.REQUEST_ID);

        Assert.assertTrue(stopped);
        Assert.assertTrue(call.isCanceled());
        Assert.assertTrue(registry.isStopRequested(SessionMemoryTestSupport.REQUEST_ID));
    }

    @Test
    public void test_forceStoppedTurnDoesNotRefreshSessionMemory() {
        AgentStreamPersistServiceImpl service = new AgentStreamPersistServiceImpl();
        StubMessageService messageService = new StubMessageService();
        StubConversationDao conversationDao = new StubConversationDao();
        StubSessionMemoryService sessionMemoryService = new StubSessionMemoryService();

        ReflectionTestUtils.setField(service, "messageEventService", new StubMessageEventService());
        ReflectionTestUtils.setField(service, "messageService", messageService);
        ReflectionTestUtils.setField(service, "conversationDao", conversationDao);
        ReflectionTestUtils.setField(service, "sessionMemoryService", sessionMemoryService);

        AgentConversation conversation = AgentConversation.builder()
                .id(SessionMemoryTestSupport.CONVERSATION_ID)
                .sessionId(SessionMemoryTestSupport.SESSION_ID)
                .agentType(2)
                .title("历史会话")
                .messageCount(3)
                .build();

        ReflectionTestUtils.invokeMethod(
                service,
                "persistTurnAndEvents",
                2001L,
                conversation,
                SessionMemoryTestSupport.CONVERSATION_ID,
                "继续补充，但这轮会被停止",
                3,
                "历史会话",
                new StringBuilder("部分回答"),
                new StringBuilder("部分思考"),
                new LinkedHashMap<String, OrderedEvent>(),
                "partial");

        Assert.assertEquals(1, messageService.forceStopCount.get());
        Assert.assertEquals(0, messageService.completeCount.get());
        Assert.assertEquals(0, messageService.errorCount.get());
        Assert.assertEquals(0, sessionMemoryService.prepareCount.get());
        Assert.assertEquals(1, conversationDao.incrementMessageCount.get());
        Assert.assertEquals(1, conversationDao.updateConversationCount.get());
    }

    private static class StubMessageService implements IAgentMessageService {

        private final AtomicInteger completeCount = new AtomicInteger();
        private final AtomicInteger errorCount = new AtomicInteger();
        private final AtomicInteger forceStopCount = new AtomicInteger();

        @Override
        public AgentMessage insertPlaceholder(Long conversationId, String requestId, String query, Integer agentType, String filesJson) {
            return null;
        }

        @Override
        public void completeMessage(Long messageId, String response, String metricsJson) {
            completeCount.incrementAndGet();
        }

        @Override
        public void markError(Long messageId, String partialResponse, String metricsJson) {
            errorCount.incrementAndGet();
        }

        @Override
        public void markForceStop(Long messageId, String partialResponse, String metricsJson) {
            forceStopCount.incrementAndGet();
        }

        @Override
        public List<AgentMessage> getRecentCompleted(Long conversationId, int limit) {
            return List.of();
        }

        @Override
        public int getNextSortOrder(Long conversationId) {
            return 0;
        }
    }

    private static class StubMessageEventService implements IAgentMessageEventService {
        @Override
        public void persistEvents(List<OrderedEvent> events, Long messageId, String finalStatus) {
        }
    }

    private static class StubConversationDao implements IAgentConversationDao {

        private final AtomicInteger incrementMessageCount = new AtomicInteger();
        private final AtomicInteger updateConversationCount = new AtomicInteger();

        @Override
        public int insert(AgentConversation conversation) {
            return 0;
        }

        @Override
        public int updateById(AgentConversation conversation) {
            updateConversationCount.incrementAndGet();
            return 1;
        }

        @Override
        public int incrementMessageCount(Long id) {
            incrementMessageCount.incrementAndGet();
            return 1;
        }

        @Override
        public int softDeleteBySessionId(String sessionId, String deviceId) {
            return 0;
        }

        @Override
        public AgentConversation queryById(Long id) {
            return null;
        }

        @Override
        public AgentConversation queryBySessionId(String sessionId) {
            return null;
        }

        @Override
        public List<AgentConversation> queryByDeviceId(String deviceId, int offset, int limit) {
            return List.of();
        }

        @Override
        public int countByDeviceId(String deviceId) {
            return 0;
        }

        @Override
        public List<AgentConversation> queryByUserIdOrDeviceId(Long userId, String deviceId, int offset, int limit) {
            return List.of();
        }

        @Override
        public int countByUserIdOrDeviceId(Long userId, String deviceId) {
            return 0;
        }

        @Override
        public List<AgentConversation> queryAll(int offset, int limit) {
            return List.of();
        }

        @Override
        public int countAll() {
            return 0;
        }

        @Override
        public int migrateDeviceToUser(String deviceId, Long userId) {
            return 0;
        }

        @Override
        public int bindChatRole(Long id, String aiAgentId, String aiAgentNameSnapshot) {
            return 0;
        }
    }

    private static class StubSessionMemoryService implements IAgentSessionMemoryService {

        private final AtomicInteger prepareCount = new AtomicInteger();

        @Override
        public SessionMemoryPreparationResult prepareForRequest(AgentConversation conversation) {
            prepareCount.incrementAndGet();
            return SessionMemoryPreparationResult.builder()
                    .decisionType(SessionMemoryDecisionType.BYPASS)
                    .workingMemory(SessionWorkingMemory.builder()
                            .historyDialogue("")
                            .build())
                    .build();
        }

        @Override
        public SessionWorkingMemory rebuildWorkingMemory(AgentConversation conversation) {
            return SessionWorkingMemory.builder()
                    .historyDialogue("")
                    .build();
        }
    }
}
