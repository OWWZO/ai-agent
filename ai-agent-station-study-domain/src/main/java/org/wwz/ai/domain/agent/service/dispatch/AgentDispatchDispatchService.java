package org.wwz.ai.domain.agent.service.dispatch;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.adapter.repository.IAgentRepository;
import org.wwz.ai.domain.agent.reactor.agent.enums.AgentType;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.AiAgentVO;
import org.wwz.ai.domain.agent.service.IAgentDispatchService;
import org.wwz.ai.domain.agent.service.IExecuteStrategy;
import org.wwz.ai.types.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 服务接口
 * 2025/9/6 06:55
 */
@Slf4j
@Service
public class AgentDispatchDispatchService implements IAgentDispatchService {

    @Resource
    private Map<String, IExecuteStrategy> executeStrategyMap;

    @Resource
    private IAgentRepository repository;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Override
    public void dispatch(AgentRequest request, SseEmitter emitter) throws Exception {

        String strategy = null;

        if (request.getAgentType() != null) {
            if (AgentType.WORKFLOW.getValue().equals(request.getAgentType())) {
                // 聊天模式：固定策略（无深度研究）
                strategy = "flowAgentExecuteStrategy";
            } else if (AgentType.PLAN_SOLVE.getValue().equals(request.getAgentType())) {
                // 规划-执行模式（深度研究开启时使用）
                strategy = "planSolveAgentExecuteStrategy";
            } else if (AgentType.REACT.getValue().equals(request.getAgentType())) {
                // React 模式
                strategy = "reactAgentExecuteStrategy";
            }
        }

        if (null == strategy || strategy.isEmpty()) {
            // 如果无法从 agentType 确定策略，可以根据业务需求抛出异常或者设定默认策略
            // AgentRequest 中通常没有 aiAgentId，所以这里无法查表回退，除非有默认值
            // 假设默认走 React 模式
            strategy = "reactAgentExecuteStrategy";
        }

        IExecuteStrategy executeStrategy = executeStrategyMap.get(strategy);
        if (null == executeStrategy) {
            throw new BizException("不存在的执行策略类型 strategy:" + strategy);
        }

        executeStrategy.execute(request, emitter);
    }

//    @Override
//    public void dispatch(ExecuteCommandEntity requestParameter, ResponseBodyEmitter emitter) throws Exception {
//        String strategy = requestParameter.getStrategy();
//
//        // 根据 agentType 动态选择策略
//        if (requestParameter.getAgentType() != null) {
//            if (requestParameter.getAgentType() == 3) {
//                // 规划-执行模式（深度研究开启时使用）
//                strategy = "flowAgentExecuteStrategy";
//            } else if (requestParameter.getAgentType() == 5) {
//                // React 模式
//                strategy = "reactAgentExecuteStrategy";
//            }
//        }
//
//        if (null == strategy || strategy.isEmpty()) {
//            AiAgentVO aiAgentVO = repository.queryAiAgentByAgentId(requestParameter.getAiAgentId());
//            if (aiAgentVO != null) {
//                strategy = aiAgentVO.getStrategy();
//            }
//        }
//
//        IExecuteStrategy executeStrategy = executeStrategyMap.get(strategy);
//        if (null == executeStrategy) {
//            throw new BizException("不存在的执行策略类型 strategy:" + strategy);
//        }
//
//        // 3. 执行策略
//        executeStrategy.execute(requestParameter, emitter);
//
//    }

}
