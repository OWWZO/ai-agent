package org.wwz.ai.config.reactor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.domain.agent.runtime.handler.AgentStreamEventHandler;
import org.wwz.ai.domain.agent.runtime.handler.PlanSolveAgentStreamEventHandler;
import org.wwz.ai.domain.agent.runtime.handler.ReactAgentStreamEventHandler;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Reactor handler 装配归 app 模块所有。
 * 这里仅做 Bean 拓扑归并，避免 handler 选择职责回流到 domain 或 trigger。
 */
@Configuration
public class AgentHandlerAutoConfiguration {

    @Bean
    public Map<AgentType, AgentStreamEventHandler> handlerMap(List<AgentStreamEventHandler> handlerList) {
        Map<AgentType, AgentStreamEventHandler> map = new EnumMap<>(AgentType.class);
        for (AgentStreamEventHandler handler : handlerList) {
            if (handler instanceof PlanSolveAgentStreamEventHandler) {
                map.put(AgentType.PLAN_SOLVE, handler);
            } else if (handler instanceof ReactAgentStreamEventHandler) {
                map.put(AgentType.REACT, handler);
            }
        }
        return map;
    }
}
