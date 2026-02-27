package org.wwz.ai.domain.agent.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.genie.model.req.AgentRequest;
import org.wwz.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.yaml.snakeyaml.emitter.Emitter;

/**
 * Agent 策略调度器接口
 * 2025/9/6 06:54
 */
public interface IAgentDispatchService {


    void dispatch(AgentRequest request, SseEmitter emitter) throws Exception;

}
