package org.wwz.ai.config.reactor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.domain.agent.reactor.agent.enums.AgentType;
import org.wwz.ai.domain.agent.reactor.handler.AgentResponseHandler;
import org.wwz.ai.domain.agent.reactor.handler.PlanSolveAgentResponseHandler;
import org.wwz.ai.domain.agent.reactor.handler.ReactAgentResponseHandler;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Reactor handler 装配归 app 模块所有。
 */
@Configuration
public class AgentHandlerAutoConfiguration {

    @Bean
    public Map<AgentType, AgentResponseHandler> handlerMap(List<AgentResponseHandler> handlerList) {
        Map<AgentType, AgentResponseHandler> map = new EnumMap<>(AgentType.class);
        for (AgentResponseHandler handler : handlerList) {
            if (handler instanceof PlanSolveAgentResponseHandler) {
                map.put(AgentType.PLAN_SOLVE, handler);
            } else if (handler instanceof ReactAgentResponseHandler) {
                map.put(AgentType.REACT, handler);
                map.put(AgentType.WORKFLOW, handler);
                map.put(AgentType.COMPREHENSIVE, handler);
            }
        }
        return map;
    }
}
