package org.wwz.ai.domain.agent.reactor.service;


import org.wwz.ai.domain.agent.reactor.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;

public interface AgentHandlerService {

    /**
     * 处理Agent请求
     */
    String handle(AgentContext context, AgentRequest request);

    /**
     * 进入handler条件
     */
    Boolean support(AgentContext context, AgentRequest request);

}