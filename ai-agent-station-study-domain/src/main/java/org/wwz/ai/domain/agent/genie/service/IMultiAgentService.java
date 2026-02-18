package org.wwz.ai.domain.agent.genie.service;


import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.genie.model.dto.AutoBotsResult;
import org.wwz.ai.domain.agent.genie.model.req.GptQueryReq;

public interface IMultiAgentService {
    /**
     * 请求多 agent发送请求入口函数.
     * @param gptQueryReq
     * @param sseEmitter
     * @return
     */
    AutoBotsResult searchForAgentRequest(GptQueryReq gptQueryReq, SseEmitter sseEmitter);
}
