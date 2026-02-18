package org.wwz.ai.domain.agent.genie.service;


import org.wwz.ai.domain.agent.genie.agent.agent.AgentContext;
import org.wwz.ai.domain.agent.genie.model.req.AgentRequest;

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