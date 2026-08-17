package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.application.agent.dispatch.IAgentDispatchService;
import org.wwz.ai.application.agent.query.GptQueryApplicationService;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.application.agent.visitor.ConversationSessionOwnershipApplicationService;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.runtime.GptQueryAgentRequestFactory;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.handler.AgentResponseHandler;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 主聊天路径 visitor / session 归属绑定（GptQueryApplicationService）。
 */
public class GptQueryApplicationServiceVisitorBindingTest {

    @Test
    public void shouldBindSessionBeforeDispatchingQuery() throws Exception {
        GptQueryApplicationService service = new GptQueryApplicationService();
        GptQueryAgentRequestFactory factory = Mockito.mock(GptQueryAgentRequestFactory.class);
        IAgentDispatchService dispatchService = Mockito.mock(IAgentDispatchService.class);
        ConversationSessionOwnershipApplicationService ownershipService =
                Mockito.mock(ConversationSessionOwnershipApplicationService.class);
        AgentSessionStream stream = Mockito.mock(AgentSessionStream.class);

        ReflectionTestUtils.setField(service, "gptQueryAgentRequestFactory", factory);
        ReflectionTestUtils.setField(service, "agentDispatchService", dispatchService);
        ReflectionTestUtils.setField(service, "conversationSessionOwnershipApplicationService", ownershipService);
        ReflectionTestUtils.setField(service, "handlerMap", Collections.<AgentType, AgentResponseHandler>emptyMap());
        ReflectionTestUtils.setField(service, "dispatchExecutor", (Executor) Runnable::run);

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
        Mockito.when(ownershipService.ensureSessionAccessible("visitor-001", "session-001", "帮我总结一下这个项目"))
                .thenReturn(DialogueSession.builder().sessionId("session-001").visitorId("visitor-001").build());

        CountDownLatch latch = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(dispatchService).dispatch(Mockito.any(AgentRequest.class), Mockito.any());

        VisitorRequestContext.bind("visitor-001");
        try {
            service.queryAgentStreamIncr(params, stream);
        } finally {
            VisitorRequestContext.clear();
        }

        Mockito.verify(ownershipService).ensureSessionAccessible("visitor-001", "session-001", "帮我总结一下这个项目");
        Assert.assertEquals("visitor-001", agentRequest.getVisitorId());
        Assert.assertTrue("异步派发应已触发", latch.await(3, TimeUnit.SECONDS));
    }

    @Test
    public void shouldPreferServerResolvedVisitorOverCallerSuppliedValue() throws Exception {
        GptQueryApplicationService service = new GptQueryApplicationService();
        GptQueryAgentRequestFactory factory = Mockito.mock(GptQueryAgentRequestFactory.class);
        IAgentDispatchService dispatchService = Mockito.mock(IAgentDispatchService.class);
        ConversationSessionOwnershipApplicationService ownershipService =
                Mockito.mock(ConversationSessionOwnershipApplicationService.class);
        AgentSessionStream stream = Mockito.mock(AgentSessionStream.class);

        ReflectionTestUtils.setField(service, "gptQueryAgentRequestFactory", factory);
        ReflectionTestUtils.setField(service, "agentDispatchService", dispatchService);
        ReflectionTestUtils.setField(service, "conversationSessionOwnershipApplicationService", ownershipService);
        ReflectionTestUtils.setField(service, "handlerMap", Collections.<AgentType, AgentResponseHandler>emptyMap());
        ReflectionTestUtils.setField(service, "dispatchExecutor", (Executor) Runnable::run);

        GptQueryReq params = new GptQueryReq();
        params.setRequestId("req-002");
        params.setSessionId("session-002");
        params.setQuery("继续这个会话");

        AgentRequest agentRequest = AgentRequest.builder()
                .requestId("req-002")
                .sessionId("session-002")
                .visitorId("forged-visitor")
                .query("继续这个会话")
                .build();
        Mockito.doNothing().when(factory).normalize(params);
        Mockito.when(factory.build(params)).thenReturn(agentRequest);
        Mockito.when(ownershipService.ensureSessionAccessible("visitor-002", "session-002", "继续这个会话"))
                .thenReturn(DialogueSession.builder().sessionId("session-002").visitorId("visitor-002").build());

        VisitorRequestContext.bind("visitor-002");
        try {
            service.queryAgentStreamIncr(params, stream);
        } finally {
            VisitorRequestContext.clear();
        }

        Assert.assertEquals("visitor-002", agentRequest.getVisitorId());
        Mockito.verify(ownershipService).ensureSessionAccessible("visitor-002", "session-002", "继续这个会话");

        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        Mockito.verify(dispatchService).dispatch(captor.capture(), Mockito.any());
        Assert.assertEquals("visitor-002", captor.getValue().getVisitorId());
    }
}
