package org.wwz.ai.domain.agent.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;

/**
 * 执行策略接口
 * 2025/8/5 09:48
 */
public interface IExecuteStrategy {

    void execute(AgentRequest request, SseEmitter emitter) throws Exception;

}
