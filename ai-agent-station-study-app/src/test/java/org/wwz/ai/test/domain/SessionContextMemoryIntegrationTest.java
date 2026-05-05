package org.wwz.ai.test.domain;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.runtime.printer.Printer;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.application.agent.execute.planexecute.PlanSolveAgentExecuteStrategy;
import org.wwz.ai.domain.agent.service.execute.planexecute.step.factory.DefaultPlanSolveAgentExecuteStrategyFactory;
import org.wwz.ai.application.agent.execute.react.ReactAgentExecuteStrategy;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;
import org.wwz.ai.infrastructure.reactor.service.impl.SessionContextMemoryServiceImpl;

import java.time.LocalDateTime;

/**
 * 单会话上下文记忆入口注入测试。
 */
public class SessionContextMemoryIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    public void shouldInjectHistoryDialogueBeforeReactExecution() throws Exception {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedSimpleHistory(ctx, "req-react-history-001", "session-react-history-001", "历史 thought from react");
        SessionContextMemoryServiceImpl memoryService = new SessionContextMemoryServiceImpl(
                ctx.queryService,
                ctx.llmDao,
                ctx.toolDao,
                ctx.artifactDao
        );

        StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> handler =
                Mockito.mock(StrategyHandler.class);
        Mockito.when(handler.apply(Mockito.any(AgentRequest.class), Mockito.any()))
                .thenAnswer(invocation -> {
                    AgentRequest request = invocation.getArgument(0);
                    Assert.assertTrue(request.getHistoryDialogue().contains("历史 thought from react"));
                    Assert.assertTrue(request.getHistoryDialogue().contains("### Run req-react-history-001"));
                    return "ok";
                });

        DefaultReactAgentExecuteStrategyFactory factory = Mockito.mock(DefaultReactAgentExecuteStrategyFactory.class);
        Mockito.when(factory.armoryStrategyHandler()).thenReturn(handler);

        ReactAgentExecuteStrategy strategy = new ReactAgentExecuteStrategy();
        ReflectionTestUtils.setField(strategy, "defaultReactAgentExecuteStrategyFactory", factory);
        ReflectionTestUtils.setField(strategy, "reactorConfig", new ReactorConfig());
        ReflectionTestUtils.setField(strategy, "sessionContextMemoryService", memoryService);

        AgentRequest request = AgentRequest.builder()
                .requestId("req-react-current-001")
                .sessionId("session-react-history-001")
                .query("当前 react 请求")
                .build();
        AgentSessionStream stream = Mockito.mock(AgentSessionStream.class);
        strategy.execute(request, stream);

        Assert.assertTrue(request.getHistoryDialogue().contains("历史 thought from react"));
        Mockito.verify(stream, Mockito.never()).send(Mockito.any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldInjectHistoryDialogueBeforePlanSolveExecution() throws Exception {
        ExecutionLedgerFixtureFactory.LedgerTestContext ctx = ExecutionLedgerFixtureFactory.newLedgerTestContext();
        seedSimpleHistory(ctx, "req-plan-history-001", "session-plan-history-001", "历史 thought from plan");
        SessionContextMemoryServiceImpl memoryService = new SessionContextMemoryServiceImpl(
                ctx.queryService,
                ctx.llmDao,
                ctx.toolDao,
                ctx.artifactDao
        );

        StrategyHandler<AgentRequest, DefaultPlanSolveAgentExecuteStrategyFactory.DynamicContext, String> handler =
                Mockito.mock(StrategyHandler.class);
        Mockito.when(handler.apply(Mockito.any(AgentRequest.class), Mockito.any()))
                .thenAnswer(invocation -> {
                    AgentRequest request = invocation.getArgument(0);
                    Assert.assertTrue(request.getHistoryDialogue().contains("历史 thought from plan"));
                    Assert.assertTrue(request.getHistoryDialogue().contains("### Run req-plan-history-001"));
                    return "ok";
                });

        DefaultPlanSolveAgentExecuteStrategyFactory factory = Mockito.mock(DefaultPlanSolveAgentExecuteStrategyFactory.class);
        Mockito.when(factory.armoryStrategyHandler()).thenReturn(handler);

        PlanSolveAgentExecuteStrategy strategy = new PlanSolveAgentExecuteStrategy();
        ReflectionTestUtils.setField(strategy, "defaultPlanSolveAgentExecuteStrategyFactory", factory);
        ReflectionTestUtils.setField(strategy, "sessionContextMemoryService", memoryService);

        AgentRequest request = AgentRequest.builder()
                .requestId("req-plan-current-001")
                .sessionId("session-plan-history-001")
                .query("当前 plan 请求")
                .build();
        AgentSessionStream stream = Mockito.mock(AgentSessionStream.class);
        strategy.execute(request, stream);

        Assert.assertTrue(request.getHistoryDialogue().contains("历史 thought from plan"));
        Mockito.verify(stream, Mockito.never()).send(Mockito.any());
    }

    @Test
    public void shouldConstructCasePrinterInsteadOfRequiringSseEmitter() throws Exception {
        StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> handler =
                Mockito.mock(StrategyHandler.class);
        Mockito.when(handler.apply(Mockito.any(AgentRequest.class), Mockito.any()))
                .thenAnswer(invocation -> {
                    DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext = invocation.getArgument(1);
                    Object printer = ReflectionTestUtils.getField(dynamicContext, "printer");
                    Assert.assertNotNull(printer);
                    Assert.assertTrue(printer instanceof Printer);
                    Assert.assertEquals("org.wwz.ai.application.agent.stream.AgentSessionPrinter", printer.getClass().getName());
                    return "ok";
                });

        DefaultReactAgentExecuteStrategyFactory factory = Mockito.mock(DefaultReactAgentExecuteStrategyFactory.class);
        Mockito.when(factory.armoryStrategyHandler()).thenReturn(handler);

        ReactAgentExecuteStrategy strategy = new ReactAgentExecuteStrategy();
        ReflectionTestUtils.setField(strategy, "defaultReactAgentExecuteStrategyFactory", factory);
        ReflectionTestUtils.setField(strategy, "reactorConfig", new ReactorConfig());
        ReflectionTestUtils.setField(strategy, "sessionContextMemoryService", Mockito.mock(org.wwz.ai.domain.agent.memory.SessionContextMemoryService.class));

        AgentRequest request = AgentRequest.builder()
                .requestId("req-react-current-002")
                .sessionId("session-react-history-002")
                .query("当前 react 请求")
                .agentType(1)
                .build();

        strategy.execute(request, Mockito.mock(AgentSessionStream.class));
    }

    private void seedSimpleHistory(ExecutionLedgerFixtureFactory.LedgerTestContext ctx,
                                   String requestId,
                                   String sessionId,
                                   String thought) {
        Long runId = ctx.recorder.createRun(org.wwz.ai.domain.agent.ledger.model.DialogueRunStartRecord.builder()
                .runUid(requestId)
                .requestId(requestId)
                .sessionId(sessionId)
                .entryAgent(org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants.ENTRY_AGENT_REACT)
                .queryText("query:" + requestId)
                .startedAt(LocalDateTime.of(2026, 5, 4, 12, 0))
                .build());
        Long llmInvocationId = ctx.recorder.createLlmInvocation(org.wwz.ai.domain.agent.ledger.model.LlmInvocationStartRecord.builder()
                .runId(runId)
                .requestId(requestId)
                .invocationSeq(1)
                .agentName("react")
                .stepNo(1)
                .callKind(org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants.CALL_KIND_ASK_TOOL)
                .streaming(false)
                .modelName("test-model")
                .startedAt(LocalDateTime.of(2026, 5, 4, 12, 1))
                .build());
        ctx.recorder.finishLlmInvocation(org.wwz.ai.domain.agent.ledger.model.LlmInvocationFinishRecord.builder()
                .llmInvocationId(llmInvocationId)
                .requestId(requestId)
                .status(org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants.STATUS_SUCCESS)
                .responseText(thought)
                .toolCallCount(0)
                .promptTokens(8)
                .completionTokens(9)
                .totalTokens(17)
                .finishReason("stop")
                .finishedAt(LocalDateTime.of(2026, 5, 4, 12, 1, 30))
                .build());
        ctx.recorder.finishRun(org.wwz.ai.domain.agent.ledger.model.DialogueRunFinishRecord.builder()
                .runId(runId)
                .requestId(requestId)
                .status(org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants.STATUS_SUCCESS)
                .finalSummaryText("summary:" + requestId)
                .finishedAt(LocalDateTime.of(2026, 5, 4, 12, 2))
                .build());
    }
}
