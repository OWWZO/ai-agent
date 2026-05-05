package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.application.agent.dispatch.IAgentDispatchService;
import org.wwz.ai.application.agent.query.IGptQueryApplicationService;
import org.wwz.ai.application.agent.visitor.ConversationSessionOwnershipApplicationService;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.trigger.http.AiAgentController;
import org.wwz.ai.types.agent.config.AgentExecutorProperties;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * AiAgentController visitor 绑定测试。
 */
public class AiAgentControllerVisitorBindingTest {

    @Test
    public void shouldBindSessionBeforeDispatchingAutoAgent() throws Exception {
        AiAgentController controller = new AiAgentController();
        IAgentDispatchService dispatchService = Mockito.mock(IAgentDispatchService.class);
        ConversationSessionOwnershipApplicationService ownershipService = Mockito.mock(ConversationSessionOwnershipApplicationService.class);
        ReflectionTestUtils.setField(controller, "agentDispatchService", dispatchService);
        ReflectionTestUtils.setField(controller, "gptQueryApplicationService", Mockito.mock(IGptQueryApplicationService.class));
        ReflectionTestUtils.setField(controller, "conversationSessionOwnershipApplicationService", ownershipService);
        ReflectionTestUtils.setField(controller, "agentExecutorProperties", new AgentExecutorProperties());
        ReflectionTestUtils.setField(controller, "dispatchExecutor", (Executor) Runnable::run);
        ReflectionTestUtils.setField(controller, "heartbeatScheduler", new ConcurrentTaskScheduler());

        AgentRequest request = AgentRequest.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .query("帮我总结一下这个项目")
                .build();
        Mockito.when(ownershipService.ensureSessionAccessible("visitor-001", "session-001", "帮我总结一下这个项目"))
                .thenReturn(DialogueSession.builder().sessionId("session-001").visitorId("visitor-001").build());
        CountDownLatch latch = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(dispatchService).dispatch(Mockito.eq(request), Mockito.any());

        VisitorRequestContext.bind("visitor-001");
        try {
            SseEmitter emitter = controller.AutoAgent(request);
            Assert.assertNotNull(emitter);
        } finally {
            VisitorRequestContext.clear();
        }

        Mockito.verify(ownershipService).ensureSessionAccessible("visitor-001", "session-001", "帮我总结一下这个项目");
        Assert.assertEquals("visitor-001", request.getVisitorId());
        Assert.assertTrue("异步派发应已触发", latch.await(3, TimeUnit.SECONDS));
    }

    @Test
    public void shouldPreferServerResolvedVisitorOverCallerSuppliedValue() throws Exception {
        AiAgentController controller = new AiAgentController();
        IAgentDispatchService dispatchService = Mockito.mock(IAgentDispatchService.class);
        ConversationSessionOwnershipApplicationService ownershipService = Mockito.mock(ConversationSessionOwnershipApplicationService.class);
        ReflectionTestUtils.setField(controller, "agentDispatchService", dispatchService);
        ReflectionTestUtils.setField(controller, "gptQueryApplicationService", Mockito.mock(IGptQueryApplicationService.class));
        ReflectionTestUtils.setField(controller, "conversationSessionOwnershipApplicationService", ownershipService);
        ReflectionTestUtils.setField(controller, "agentExecutorProperties", new AgentExecutorProperties());
        ReflectionTestUtils.setField(controller, "dispatchExecutor", (Executor) Runnable::run);
        ReflectionTestUtils.setField(controller, "heartbeatScheduler", new ConcurrentTaskScheduler());

        AgentRequest request = AgentRequest.builder()
                .requestId("req-002")
                .sessionId("session-002")
                .visitorId("forged-visitor")
                .query("继续这个会话")
                .build();
        Mockito.when(ownershipService.ensureSessionAccessible("visitor-002", "session-002", "继续这个会话"))
                .thenReturn(DialogueSession.builder().sessionId("session-002").visitorId("visitor-002").build());

        VisitorRequestContext.bind("visitor-002");
        try {
            controller.AutoAgent(request);
        } finally {
            VisitorRequestContext.clear();
        }

        Assert.assertEquals("visitor-002", request.getVisitorId());
        Mockito.verify(ownershipService).ensureSessionAccessible("visitor-002", "session-002", "继续这个会话");
    }
}
