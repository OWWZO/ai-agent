package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.application.agent.dispatch.IAgentDispatchService;
import org.wwz.ai.application.agent.query.AgentQuerySubmitResult;
import org.wwz.ai.application.agent.query.GptQueryCommand;
import org.wwz.ai.application.agent.query.GptQueryApplicationService;
import org.wwz.ai.application.agent.query.mapper.AgentExecutionCommandMapper;
import org.wwz.ai.application.agent.run.AgentRunLaunchGate;
import org.wwz.ai.application.agent.stream.AgentSessionEventBus;
import org.wwz.ai.application.agent.authorization.ConversationSessionAuthorizationService;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;
import org.wwz.ai.domain.agent.runtime.cancel.RunCancellation;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.handler.AgentStreamEventHandler;
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
        AgentExecutionCommandMapper mapper = Mockito.mock(AgentExecutionCommandMapper.class);
        IAgentDispatchService dispatchService = Mockito.mock(IAgentDispatchService.class);
        ConversationSessionAuthorizationService ownershipService =
                Mockito.mock(ConversationSessionAuthorizationService.class);

        ReflectionTestUtils.setField(service, "agentExecutionCommandMapper", mapper);
        ReflectionTestUtils.setField(service, "agentDispatchService", dispatchService);
        ReflectionTestUtils.setField(service, "conversationSessionAuthorizationService", ownershipService);

        GptQueryCommand params = GptQueryCommand.builder()
                .requestId("req-001").sessionId("session-001").query("帮我总结一下这个项目").build();

        AgentExecutionCommand agentRequest = AgentExecutionCommand.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .query("帮我总结一下这个项目")
                .build();
        Mockito.when(mapper.toExecutionCommand(params, "user-001")).thenReturn(agentRequest);
        Mockito.when(ownershipService.ensureSessionAccessible("user-001", "session-001", "帮我总结一下这个项目"))
                .thenReturn(DialogueSession.builder().sessionId("session-001").userId("user-001").build());

        CountDownLatch latch = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(dispatchService).dispatch(Mockito.any(AgentExecutionCommand.class), Mockito.any());

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
        AgentExecutionCommandMapper mapper = Mockito.mock(AgentExecutionCommandMapper.class);
        IAgentDispatchService dispatchService = Mockito.mock(IAgentDispatchService.class);
        ConversationSessionAuthorizationService ownershipService =
                Mockito.mock(ConversationSessionAuthorizationService.class);

        ReflectionTestUtils.setField(service, "agentExecutionCommandMapper", mapper);
        ReflectionTestUtils.setField(service, "agentDispatchService", dispatchService);
        ReflectionTestUtils.setField(service, "conversationSessionAuthorizationService", ownershipService);

        GptQueryCommand params = GptQueryCommand.builder()
                .requestId("req-002").sessionId("session-002").query("继续这个会话").build();

        AgentExecutionCommand agentRequest = AgentExecutionCommand.builder()
                .requestId("req-002")
                .sessionId("session-002")
                .userId("forged-user")
                .query("继续这个会话")
                .build();
        Mockito.when(mapper.toExecutionCommand(params, "user-002")).thenReturn(agentRequest);
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

        ArgumentCaptor<AgentExecutionCommand> captor = ArgumentCaptor.forClass(AgentExecutionCommand.class);
        Mockito.verify(dispatchService).dispatch(captor.capture(), Mockito.any());
        Assert.assertEquals("user-002", captor.getValue().getUserId());
    }

    @Test
    public void shouldRejectUnavailableExplicitModelBeforeStartingRun() {
        GptQueryApplicationService service = newService();
        AgentExecutionCommandMapper mapper = Mockito.mock(AgentExecutionCommandMapper.class);
        IAgentDispatchService dispatchService = Mockito.mock(IAgentDispatchService.class);
        ConversationSessionAuthorizationService ownershipService =
                Mockito.mock(ConversationSessionAuthorizationService.class);
        org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog modelCatalog =
                Mockito.mock(org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog.class);
        ActiveAgentRunRegistry registry = Mockito.mock(ActiveAgentRunRegistry.class);

        ReflectionTestUtils.setField(service, "agentExecutionCommandMapper", mapper);
        ReflectionTestUtils.setField(service, "agentDispatchService", dispatchService);
        ReflectionTestUtils.setField(service, "conversationSessionAuthorizationService", ownershipService);
        ReflectionTestUtils.setField(service, "llmModelCatalog", modelCatalog);
        ReflectionTestUtils.setField(service, "activeAgentRunRegistry", registry);

        GptQueryCommand params = GptQueryCommand.builder()
                .requestId("req-invalid-model")
                .sessionId("session-invalid-model")
                .query("使用指定模型")
                .build();
        AgentExecutionCommand agentRequest = AgentExecutionCommand.builder()
                .requestId(params.getRequestId())
                .sessionId(params.getSessionId())
                .model("disabled-model")
                .query(params.getQuery())
                .build();
        Mockito.when(mapper.toExecutionCommand(params, null)).thenReturn(agentRequest);
        Mockito.when(modelCatalog.isUserSelectableModel("disabled-model")).thenReturn(false);

        try {
            service.submitAgentQuery(params);
            Assert.fail("不可用模型应在创建 run 前被拒绝");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("模型不可用"));
        }

        Mockito.verify(registry, Mockito.never()).begin(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verifyNoInteractions(ownershipService, dispatchService);
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
        ReflectionTestUtils.setField(service, "handlerMap", Collections.<AgentType, AgentStreamEventHandler>emptyMap());
        ReflectionTestUtils.setField(service, "dispatchExecutor", (Executor) Runnable::run);
        ReflectionTestUtils.setField(service, "activeAgentRunRegistry", registry);
        ReflectionTestUtils.setField(service, "agentSessionEventBus",
                (AgentSessionEventBus) (sessionId, frame) -> {
                });
        ReflectionTestUtils.setField(service, "agentRunLaunchGate", new AgentRunLaunchGate(0));
        return service;
    }
}
