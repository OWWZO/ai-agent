package org.wwz.ai.test.domain;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.service.execute.auto.step.RootNode;
import org.wwz.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;

@RunWith(SpringRunner.class)
@SpringBootTest
public class StepReactNodeRoutingTest {

    @Autowired
    private RootNode rootNode;

    @Test
    public void whenMaxStepIsOne_shouldRouteToStepReactNode() throws Exception {
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
    }
}

