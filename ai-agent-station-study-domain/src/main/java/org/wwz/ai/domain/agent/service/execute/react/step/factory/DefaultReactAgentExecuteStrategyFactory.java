package org.wwz.ai.domain.agent.service.execute.react.step.factory;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.genie.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;

import org.wwz.ai.domain.agent.service.execute.react.step.RootNode;

@Service
public class DefaultReactAgentExecuteStrategyFactory {

    private final RootNode reactRootNode;

    public DefaultReactAgentExecuteStrategyFactory(RootNode reactRootNode) {
        this.reactRootNode = reactRootNode;
    }

    public StrategyHandler<ExecuteCommandEntity, AgentContext, String> armoryStrategyHandler(){
        return reactRootNode;
    }

}
