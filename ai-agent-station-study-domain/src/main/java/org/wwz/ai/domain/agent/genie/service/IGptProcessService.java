package org.wwz.ai.domain.agent.genie.service;


import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.genie.model.req.GptQueryReq;

public interface IGptProcessService {

    /**
     * 单智能体，多智能体 Agent 增量接口.
     */
    SseEmitter queryMultiAgentIncrStream(GptQueryReq req);
}
