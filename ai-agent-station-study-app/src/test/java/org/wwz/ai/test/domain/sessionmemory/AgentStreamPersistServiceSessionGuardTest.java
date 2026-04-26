package org.wwz.ai.test.domain.sessionmemory;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.model.valobj.FixRoleVO;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentConversationDao;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentMessageDao;
import org.wwz.ai.domain.agent.reactor.service.IAgentConversationService;
import org.wwz.ai.domain.agent.reactor.service.IAgentMessageEventService;
import org.wwz.ai.domain.agent.reactor.service.IAgentMessageService;
import org.wwz.ai.domain.agent.reactor.service.IAgentSessionMemoryService;
import org.wwz.ai.domain.agent.reactor.service.impl.AgentStreamPersistServiceImpl;
import org.wwz.ai.domain.agent.reactor.model.history.ConversationTurnDetail;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryDecisionType;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryPreparationResult;
import org.wwz.ai.domain.agent.reactor.model.multi.OrderedEvent;
import org.wwz.ai.domain.agent.reactor.service.support.SessionArtifactRestoreSupport;
import org.wwz.ai.domain.agent.service.IFixRoleService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AgentStreamPersistServiceSessionGuardTest {

    @Test
    public void test_rejectsModeConflictWithoutInsertingPlaceholder() {
        AgentStreamPersistServiceImpl service = new AgentStreamPersistServiceImpl();
        StubConversationService conversationService = new StubConversationService(
                AgentConversation.builder()
                        .id(1001L)
                        .sessionId(SessionMemoryTestSupport.SESSION_ID)
                        .agentType(2)
                        .messageCount(2)
                        .title("已有会话")
                        .build());
        StubMessageService messageService = new StubMessageService();
        StubSessionMemoryService sessionMemoryService = new StubSessionMemoryService();

        injectMinimalDependencies(service, conversationService, messageService, sessionMemoryService, 0);

        SseEmitter emitter = service.sendAndPersist(
                SessionMemoryTestSupport.SESSION_ID,
                "req-mode-conflict",
                SessionMemoryTestSupport.DEVICE_ID,
                "把上面的结果改成执行计划",
                1,
                "html",
                null,
                null);

        Assert.assertNotNull(emitter);
        Assert.assertEquals(0, messageService.insertPlaceholderCount.get());
        Assert.assertEquals(0, conversationService.createConversationCount.get());
        Assert.assertEquals(0, sessionMemoryService.rebuildCount.get());
    }

    @Test
    public void test_rejectsBusySessionWithoutInsertingPlaceholder() {
        AgentStreamPersistServiceImpl service = new AgentStreamPersistServiceImpl();
        StubConversationService conversationService = new StubConversationService(
                AgentConversation.builder()
                        .id(1001L)
                        .sessionId(SessionMemoryTestSupport.SESSION_ID)
                        .agentType(2)
                        .messageCount(2)
                        .title("已有会话")
                        .build());
        StubMessageService messageService = new StubMessageService();
        StubSessionMemoryService sessionMemoryService = new StubSessionMemoryService();

        injectMinimalDependencies(service, conversationService, messageService, sessionMemoryService, 1);

        SseEmitter emitter = service.sendAndPersist(
                SessionMemoryTestSupport.SESSION_ID,
                "req-busy-session",
                SessionMemoryTestSupport.DEVICE_ID,
                "继续补充，但当前先不要创建新轮次",
                0,
                "html",
                null,
                null);

        Assert.assertNotNull(emitter);
        Assert.assertEquals(0, messageService.insertPlaceholderCount.get());
        Assert.assertEquals(0, conversationService.createConversationCount.get());
        Assert.assertEquals(0, sessionMemoryService.rebuildCount.get());
    }

    @Test
    public void test_rejectsPreflightContextLimitWithoutInsertingPlaceholder() {
        AgentStreamPersistServiceImpl service = new AgentStreamPersistServiceImpl();
        StubConversationService conversationService = new StubConversationService(
                AgentConversation.builder()
                        .id(1001L)
                        .sessionId(SessionMemoryTestSupport.SESSION_ID)
                        .agentType(2)
                        .messageCount(2)
                        .title("已有会话")
                        .build());
        StubMessageService messageService = new StubMessageService();
        StubSessionMemoryService sessionMemoryService = new StubSessionMemoryService();
        sessionMemoryService.preparationResult = SessionMemoryPreparationResult.builder()
                .decisionType(SessionMemoryDecisionType.REJECTED)
                .rejectReason("当前会话上下文过长且压缩失败，请稍后重试或新建会话")
                .build();

        injectMinimalDependencies(service, conversationService, messageService, sessionMemoryService, 0);

        SseEmitter emitter = service.sendAndPersist(
                SessionMemoryTestSupport.SESSION_ID,
                "req-context-limit",
                SessionMemoryTestSupport.DEVICE_ID,
                "继续补充，但这轮应该在 preflight 就被拦截",
                0,
                "html",
                null,
                null);

        Assert.assertNotNull(emitter);
        Assert.assertEquals(0, messageService.insertPlaceholderCount.get());
        Assert.assertEquals(1, sessionMemoryService.prepareCount.get());
        Assert.assertEquals(0, sessionMemoryService.rebuildCount.get());
    }

    private void injectMinimalDependencies(AgentStreamPersistServiceImpl service,
                                           StubConversationService conversationService,
                                           StubMessageService messageService,
                                           StubSessionMemoryService sessionMemoryService,
                                           int streamingCount) {
        ReflectionTestUtils.setField(service, "conversationService", conversationService);
        ReflectionTestUtils.setField(service, "messageService", messageService);
        ReflectionTestUtils.setField(service, "messageDao", new StubMessageDao(streamingCount));
        ReflectionTestUtils.setField(service, "messageEventService", new StubMessageEventService());
        ReflectionTestUtils.setField(service, "conversationDao", new StubConversationDao());
        ReflectionTestUtils.setField(service, "fixRoleService", new StubFixRoleService());
        ReflectionTestUtils.setField(service, "handlerMap", Map.of());
        ReflectionTestUtils.setField(service, "sessionMemoryService", sessionMemoryService);
        ReflectionTestUtils.setField(service, "sessionArtifactRestoreSupport", new SessionArtifactRestoreSupport());
    }

    private static class StubConversationService implements IAgentConversationService {

        private final AgentConversation conversation;
        private final AtomicInteger createConversationCount = new AtomicInteger();

        private StubConversationService(AgentConversation conversation) {
            this.conversation = conversation;
        }

        @Override
        public AgentConversation createConversation(String sessionId, String deviceId, String title, Integer agentType, String productType, String aiAgentId, String aiAgentNameSnapshot) {
            createConversationCount.incrementAndGet();
            return conversation;
        }

        @Override
        public AgentConversation getBySessionId(String sessionId) {
            return conversation;
        }

        @Override
        public AgentConversation getAccessibleConversation(String sessionId, String deviceId, Long userId) {
            return conversation;
        }

        @Override
        public void renameConversation(String sessionId, String deviceId, String newTitle) {
        }

        @Override
        public void deleteConversation(String sessionId, String deviceId) {
        }

        @Override
        public List<AgentConversation> listConversations(String deviceId, Long userId, int pageNo, int pageSize) {
            return List.of();
        }

        @Override
        public int countConversations(String deviceId, Long userId) {
            return 0;
        }

        @Override
        public List<ConversationTurnDetail> getConversationTurns(String sessionId, String deviceId, Long userId) {
            return List.of();
        }

        @Override
        public void togglePin(String sessionId, String deviceId, boolean pinned) {
        }

        @Override
        public int migrateToUser(String deviceId, Long userId) {
            return 0;
        }

        @Override
        public AgentConversation bindChatRole(AgentConversation conversation, String aiAgentId, String aiAgentNameSnapshot) {
            return conversation;
        }

        @Override
        public org.wwz.ai.domain.agent.model.valobj.ConversationRoleVO buildConversationRole(AgentConversation conversation) {
            return null;
        }
    }

    private static class StubMessageService implements IAgentMessageService {

        private final AtomicInteger insertPlaceholderCount = new AtomicInteger();

        @Override
        public AgentMessage insertPlaceholder(Long conversationId, String requestId, String query, Integer agentType, String filesJson) {
            insertPlaceholderCount.incrementAndGet();
            return AgentMessage.builder().id(1L).conversationId(conversationId).build();
        }

        @Override
        public void completeMessage(Long messageId, String response, String metricsJson, String generatedFilesJson) {
        }

        @Override
        public void markError(Long messageId, String partialResponse, String metricsJson, String generatedFilesJson) {
        }

        @Override
        public void markForceStop(Long messageId, String partialResponse, String metricsJson, String generatedFilesJson) {
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

    private static class StubMessageDao implements IAgentMessageDao {

        private final int streamingCount;

        private StubMessageDao(int streamingCount) {
            this.streamingCount = streamingCount;
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
            return List.of();
        }

        @Override
        public List<AgentMessage> queryCompletedByConversationId(Long conversationId) {
            return List.of();
        }

        @Override
        public int countStreamingByConversationId(Long conversationId) {
            return streamingCount;
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

    private static class StubMessageEventService implements IAgentMessageEventService {
        @Override
        public void persistEvents(List<OrderedEvent> events, Long messageId, String finalStatus) {
        }
    }

    private static class StubConversationDao implements IAgentConversationDao {
        @Override
        public int insert(AgentConversation conversation) {
            return 0;
        }

        @Override
        public int updateById(AgentConversation conversation) {
            return 0;
        }

        @Override
        public int incrementMessageCount(Long id) {
            return 0;
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

    private static class StubFixRoleService implements IFixRoleService {
        @Override
        public List<FixRoleVO> queryAvailableRoles() {
            return List.of();
        }

        @Override
        public FixRoleVO queryRole(String agentId) {
            return null;
        }

        @Override
        public FixRoleVO queryDefaultRole() {
            return null;
        }

        @Override
        public org.wwz.ai.domain.agent.model.valobj.ConversationRoleVO buildConversationRole(AgentConversation conversation) {
            return null;
        }
    }

    private static class StubSessionMemoryService implements IAgentSessionMemoryService {

        private SessionMemoryPreparationResult preparationResult = SessionMemoryPreparationResult.builder()
                .decisionType(SessionMemoryDecisionType.BYPASS)
                .workingMemory(org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory.builder()
                        .historyDialogue("")
                        .build())
                .build();
        private final AtomicInteger prepareCount = new AtomicInteger();
        private final AtomicInteger rebuildCount = new AtomicInteger();

        @Override
        public SessionMemoryPreparationResult prepareForRequest(AgentConversation conversation) {
            prepareCount.incrementAndGet();
            return preparationResult;
        }

        @Override
        public org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory rebuildWorkingMemory(AgentConversation conversation) {
            rebuildCount.incrementAndGet();
            return org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory.builder()
                    .historyDialogue("")
                    .build();
        }
    }
}
