package org.wwz.ai.domain.agent.service.execute.flow.step;

import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import org.wwz.ai.domain.agent.service.execute.flow.step.factory.DefaultFlowAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;


@Slf4j
@Service("flowRootNode")
public class RootNode extends AbstractExecuteSupport {

    @Resource
    private Step2PlanningNode step2PlanningNode;

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("=== 流程执行开始 ====");
        log.info("用户输入: {}", requestParameter.getMessage());
        log.info("最大执行步数: {}", requestParameter.getMaxStep());
        log.info("会话ID: {}", requestParameter.getSessionId());

        Map<String, AiAgentClientFlowConfigVO> aiAgentClientFlowConfigVOMap = repository.queryAiAgentClientFlowConfig(requestParameter.getAiAgentId());

        // 客户端对话组
        dynamicContext.setAiAgentClientFlowConfigVOMap(aiAgentClientFlowConfigVOMap);
        // 上下文信息
        dynamicContext.setExecutionHistory(new StringBuilder());
        // 当前任务信息
        dynamicContext.setCurrentTask(requestParameter.getMessage());
        // 最大任务步骤：若请求未传，则使用默认值，避免 int 自动拆箱 NPE
        dynamicContext.setMaxStep(requestParameter.getMaxStep() != null ? requestParameter.getMaxStep() : dynamicContext.getMaxStep());

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultFlowAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultFlowAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return step2PlanningNode;
    }

}
