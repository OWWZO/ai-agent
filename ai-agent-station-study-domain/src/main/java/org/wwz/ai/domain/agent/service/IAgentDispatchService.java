package org.wwz.ai.domain.agent.service;

import org.wwz.ai.domain.agent.genie.model.req.AgentRequest;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * Agent 策略调度器接口
 * 2025/9/6 06:54
 */
public interface IAgentDispatchService {

//    void dispatch(ExecuteCommandEntity requestParameter, ResponseBodyEmitter emitter) throws Exception;

    void dispatch(AgentRequest request, ResponseBodyEmitter emitter) throws Exception;

}
