package org.wwz.ai.application.agent.dispatch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.execute.IExecuteStrategy;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.types.exception.BizException;

import javax.annotation.Resource;
import java.util.Map;

/**
 * Agent 应用层调度器。
 * 负责根据 agentType 选择执行策略，并把输出协议隔离在 case 边界之外。
 */
@Slf4j
@Service
public class AgentDispatchService implements IAgentDispatchService {

    @Resource
    private Map<String, IExecuteStrategy> executeStrategyMap;

    @Override
    public void dispatch(AgentRequest request, AgentSessionStream stream) throws Exception {
        String strategy = null;

        if (request.getAgentType() != null) {
            if (AgentType.WORKFLOW.getValue().equals(request.getAgentType())) {
                strategy = "flowAgentExecuteStrategy";
            } else if (AgentType.PLAN_SOLVE.getValue().equals(request.getAgentType())) {
                strategy = "planSolveAgentExecuteStrategy";
            } else if (AgentType.REACT.getValue().equals(request.getAgentType())) {
                strategy = "reactAgentExecuteStrategy";
            }
        }

        if (strategy == null || strategy.isEmpty()) {
            strategy = "reactAgentExecuteStrategy";
        }

        IExecuteStrategy executeStrategy = executeStrategyMap.get(strategy);
        if (executeStrategy == null) {
            throw new BizException("不存在的执行策略类型 strategy:" + strategy);
        }

        executeStrategy.execute(request, stream);
    }
}
