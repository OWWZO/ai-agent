package org.wwz.ai.domain.agent.service.execute.react;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.genie.config.GenieConfig;
import org.wwz.ai.domain.agent.genie.model.req.AgentRequest;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.wwz.ai.domain.agent.service.IExecuteStrategy;
import org.wwz.ai.domain.agent.service.execute.react.step.factory.DefaultReactAgentExecuteStrategyFactory;

import java.util.Map;

/**
 * React Agent 执行策略：以 AgentRequest 贯穿逻辑树，避免重复转换
 */
@Slf4j
@Service("reactAgentExecuteStrategy")
public class ReactAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultReactAgentExecuteStrategyFactory defaultReactAgentExecuteStrategyFactory;

    @Resource
    private GenieConfig genieConfig;

    /**
     * 主入口：直接使用 AgentRequest，无转换（AutoAgent 等 Genie 入口调用）
     */
    @Override
    public void execute(AgentRequest request, SseEmitter emitter) throws Exception {
        applyOutputStyle(request);
        doExecute(request, emitter);
    }


    private void doExecute(AgentRequest request, SseEmitter emitter) throws Exception {

        StrategyHandler<AgentRequest, DefaultReactAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
                = defaultReactAgentExecuteStrategyFactory.armoryStrategyHandler();

        DefaultReactAgentExecuteStrategyFactory.DynamicContext dynamicContext = DefaultReactAgentExecuteStrategyFactory.DynamicContext.builder()
                .emitter(emitter)
                .build();

        String result = executeHandler.apply(request, dynamicContext);
        log.info("ReactAgent execute result: {}", result);
    }

    private void applyOutputStyle(AgentRequest request) {
        Map<String, String> outputStyleMap = genieConfig.getOutputStylePrompts();
        if (StringUtils.isNotEmpty(request.getOutputStyle())) {
            String append = outputStyleMap.computeIfAbsent(request.getOutputStyle(), k -> "");
            request.setQuery(request.getQuery() + append);
        }
    }

    private AgentRequest toAgentRequest(ExecuteCommandEntity e) {
        AgentRequest r = new AgentRequest();
        r.setRequestId(e.getRequestId());
        r.setQuery(e.getMessage());
        r.setAgentType(e.getAgentType());
        r.setOutputStyle(e.getOutputStyle());
        r.setIsStream(e.getIsStream());
        r.setSopPrompt(e.getSopPrompt());
        r.setBasePrompt(e.getBasePrompt());
        return r;
    }
}
