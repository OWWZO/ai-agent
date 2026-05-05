package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.config.reactor.AgentHandlerAutoConfiguration;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.handler.AgentResponseHandler;
import org.wwz.ai.domain.agent.runtime.handler.PlanSolveAgentResponseHandler;
import org.wwz.ai.domain.agent.runtime.handler.ReactAgentResponseHandler;
import org.wwz.ai.domain.agent.ledger.replay.ReplayProjector;

import java.util.List;
import java.util.Map;

/**
 * Handler 装配测试。
 * 锁定 app 模块对 handlerMap 的运行时归并规则，避免装配职责回流到 domain。
 */
public class AgentHandlerAutoConfigurationTest {

    @Test
    public void shouldAssembleHandlerMapByAgentType() {
        AgentHandlerAutoConfiguration configuration = new AgentHandlerAutoConfiguration();
        ReplayProjector replayProjector = Mockito.mock(ReplayProjector.class);
        AgentResponseHandler planSolveHandler = new PlanSolveAgentResponseHandler(replayProjector);
        AgentResponseHandler reactHandler = new ReactAgentResponseHandler(replayProjector);

        Map<AgentType, AgentResponseHandler> handlerMap = configuration.handlerMap(
                List.of(planSolveHandler, reactHandler)
        );

        Assert.assertSame(planSolveHandler, handlerMap.get(AgentType.PLAN_SOLVE));
        Assert.assertSame(reactHandler, handlerMap.get(AgentType.REACT));
        Assert.assertSame(reactHandler, handlerMap.get(AgentType.WORKFLOW));
        Assert.assertSame(reactHandler, handlerMap.get(AgentType.COMPREHENSIVE));
    }
}
