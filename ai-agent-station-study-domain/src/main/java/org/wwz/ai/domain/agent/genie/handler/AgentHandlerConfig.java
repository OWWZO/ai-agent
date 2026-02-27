package org.wwz.ai.domain.agent.genie.handler;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wwz.ai.domain.agent.genie.agent.enums.AgentType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Configuration
public class AgentHandlerConfig {

    @Autowired
    private List<AgentResponseHandler> handlerList;

    @Bean
    public Map<AgentType, AgentResponseHandler> handlerMap() {
        Map<AgentType, AgentResponseHandler> map = new EnumMap<>(AgentType.class);
        for (AgentResponseHandler handler : handlerList) {
            if (handler instanceof PlanSolveAgentResponseHandler) {
                map.put(AgentType.PLAN_SOLVE, handler);
            } else if (handler instanceof ReactAgentResponseHandler) {
                map.put(AgentType.REACT, handler);
                // 默认使用 React 处理器处理 WORKFLOW / COMPREHENSIVE 模式的流式输出
                map.put(AgentType.WORKFLOW, handler);
                map.put(AgentType.COMPREHENSIVE, handler);
            }
            // 可扩展更多 handler
        }
        return map;
    }
}