package org.wwz.ai.test.domain;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.service.execute.auto.step.RootNode;
import org.wwz.ai.domain.agent.service.execute.auto.step.Step1AnalyzerNode;
import org.wwz.ai.domain.agent.service.execute.auto.step.Step2PrecisionExecutorNode;
import org.wwz.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;

public class StepReactNodeRoutingTest {

    @Test
    public void whenMaxStepIsOne_shouldRouteToStepReactNode() throws Exception {
        RootNode rootNode = new RootNode();
        Step1AnalyzerNode step1AnalyzerNode = Mockito.mock(Step1AnalyzerNode.class);
        Step2PrecisionExecutorNode step2PrecisionExecutorNode = Mockito.mock(Step2PrecisionExecutorNode.class);
        ReflectionTestUtils.setField(rootNode, "step1AnalyzerNode", step1AnalyzerNode);
        ReflectionTestUtils.setField(rootNode, "step2PrecisionExecutorNode", step2PrecisionExecutorNode);

        ExecuteCommandEntity cmd = ExecuteCommandEntity.builder()
                .aiAgentId("any")
                .message("测试单节点ReAct路由")
                .sessionId("session-" + System.currentTimeMillis())
                .maxStep(1)
                .build();

        DefaultAutoAgentExecuteStrategyFactory.DynamicContext ctx = new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        ctx.setMaxStep(1);

        StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> next = rootNode.get(cmd, ctx);
        Assert.assertNotNull("单步模式应当能路由到可执行节点", next);
        Assert.assertSame("单步模式应直接路由到精确执行节点", step2PrecisionExecutorNode, next);
    }
}
