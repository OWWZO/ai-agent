package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.application.agent.dispatch.IAgentDispatchService;
import org.wwz.ai.application.agent.query.AgentQuerySubmitResult;
import org.wwz.ai.application.agent.query.GptQueryApplicationService;
import org.wwz.ai.application.agent.run.AgentRunLaunchGate;
import org.wwz.ai.application.agent.stream.AgentSessionEventBus;
import org.wwz.ai.application.agent.authorization.ConversationSessionAuthorizationService;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.runtime.GptQueryAgentRequestFactory;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.handler.AgentResponseHandler;
import org.wwz.ai.types.agent.user.UserRequestContext;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 主聊天路径 userId / session 归属绑定（GptQueryApplicationService）。
 */
public class GptQueryApplicationServiceUserIdBindingTest {

    @Test
    public void shouldBindSessionBeforeDispatchingQuery() throws Exception {
        GptQueryApplicationService service = newService();
        GptQueryAgentRequestFactory factory = Mockito.mock(GptQueryAgentRequestFactory.class);
        IAgentDispatchService dispatchService = Mockito.mock(IAgentDispatchService.class);
        ConversationSessionAuthorizationService ownershipService =
                Mockito.mock(ConversationSessionAuthorizationService.class);

        ReflectionTestUtils.setField(service, "gptQueryAgentRequestFactory", factory);
        ReflectionTestUtils.setField(service, "agentDispatchService", dispatchService);
        ReflectionTestUtils.setField(service, "conversationSessionAuthorizationService", ownershipService);

        GptQueryReq params = new GptQueryReq();
        params.setRequestId("req-001");
        params.setSessionId("session-001");
        params.setQuery("帮我总结一下这个项目");

        AgentRequest agentRequest = AgentRequest.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .query("帮我总结一下这个项目")
                .build();
        Mockito.doNothing().when(factory).normalize(params);
        Mockito.when(factory.build(params)).thenReturn(agentRequest);
        Mockito.when(ownershipService.ensureSessionAccessible("user-001", "session-001", "帮我总结一下这个项目"))
                .thenReturn(DialogueSession.builder().sessionId("session-001").userId("user-001").build());

        CountDownLatch latch = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(dispatchService).dispatch(Mockito.any(AgentRequest.class), Mockito.any());

        UserRequestContext.bind("user-001");
        AgentQuerySubmitResult submitted;
        try {
            submitted = service.submitAgentQuery(params);
        } finally {
            UserRequestContext.clear();
        }

        Mockito.verify(ownershipService).ensureSessionAccessible("user-001", "session-001", "帮我总结一下这个项目");
        Assert.assertEquals("user-001", agentRequest.getUserId());
        Assert.assertTrue(submitted.isAccepted());
        Assert.assertTrue("异步派发应已触发", latch.await(3, TimeUnit.SECONDS));
    }

    @Test
    public void shouldPreferServerResolvedUserOverCallerSuppliedValue() throws Exception {
        GptQueryApplicationService service = newService();
        GptQueryAgentRequestFactory factory = Mockito.mock(GptQueryAgentRequestFactory.class);
        IAgentDispatchService dispatchService = Mockito.mock(IAgentDispatchService.class);
        ConversationSessionAuthorizationService ownershipService =
                Mockito.mock(ConversationSessionAuthorizationService.class);

        ReflectionTestUtils.setField(service, "gptQueryAgentRequestFactory", factory);
        ReflectionTestUtils.setField(service, "agentDispatchService", dispatchService);
        ReflectionTestUtils.setField(service, "conversationSessionAuthorizationService", ownershipService);

        GptQueryReq params = new GptQueryReq();
        params.setRequestId("req-002");
        params.setSessionId("session-002");
        params.setQuery("继续这个会话");

        AgentRequest agentRequest = AgentRequest.builder()
                .requestId("req-002")
                .sessionId("session-002")
                .userId("forged-user")
                .query("继续这个会话")
                .build();
        Mockito.doNothing().when(factory).normalize(params);
        Mockito.when(factory.build(params)).thenReturn(agentRequest);
        Mockito.when(ownershipService.ensureSessionAccessible("user-002", "session-002", "继续这个会话"))
                .thenReturn(DialogueSession.builder().sessionId("session-002").userId("user-002").build());

        UserRequestContext.bind("user-002");
        try {
            service.submitAgentQuery(params);
        } finally {
            UserRequestContext.clear();
        }

        Assert.assertEquals("user-002", agentRequest.getUserId());
        Mockito.verify(ownershipService).ensureSessionAccessible("user-002", "session-002", "继续这个会话");

        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        Mockito.verify(dispatchService).dispatch(captor.capture(), Mockito.any());
        Assert.assertEquals("user-002", captor.getValue().getUserId());
    }

    private static GptQueryApplicationService newService() {
        GptQueryApplicationService service = new GptQueryApplicationService();
        ActiveAgentRunRegistry registry = Mockito.mock(ActiveAgentRunRegistry.class);
        Mockito.when(registry.begin(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> new ActiveAgentRunRegistry.ActiveRun(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        new RunCancellation()));
        ReflectionTestUtils.setField(service, "handlerMap", Collections.<AgentType, AgentResponseHandler>emptyMap());
        ReflectionTestUtils.setField(service, "dispatchExecutor", (Executor) Runnable::run);
        ReflectionTestUtils.setField(service, "activeAgentRunRegistry", registry);
        ReflectionTestUtils.setField(service, "agentSessionEventBus",
                (AgentSessionEventBus) (sessionId, frame) -> {
                });
        ReflectionTestUtils.setField(service, "agentRunLaunchGate", new AgentRunLaunchGate(0));
        return service;
    }
}
