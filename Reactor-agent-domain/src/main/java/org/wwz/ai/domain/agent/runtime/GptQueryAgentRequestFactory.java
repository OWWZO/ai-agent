package org.wwz.ai.domain.agent.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.reactor.util.ChateiUtils;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

/**
 * 把浏览器侧 {@link GptQueryReq} 翻译为运行时 {@link AgentRequest}。
 * 只做协议字段映射，不负责调度与 SSE。
 */
@Component
@RequiredArgsConstructor
public class GptQueryAgentRequestFactory {

    private final ReactorConfig reactorConfig;

    /**
     * 补齐查询请求缺省字段，并生成 traceId。
     */
    public void normalize(GptQueryReq req) {
        if (req == null) {
            return;
        }
        req.setUser(req.getUser() == null ? "reactor" : req.getUser());
        req.setDeepThink(req.getDeepThink() == null ? 0 : req.getDeepThink());
        req.setTraceId(ChateiUtils.getRequestId(req));
    }

    /**
     * 将前端查询请求映射为统一执行请求。
     */
    public AgentRequest build(GptQueryReq req) {
        AgentRequest request = new AgentRequest();
        request.setRequestId(req.getTraceId());
        request.setSessionId(req.getSessionId());
        request.setVisitorId(VisitorRequestContext.currentVisitorId());
        request.setErp(req.getUser());
        request.setQuery(req.getQuery());
        request.setSessionFiles(req.getSessionFiles());
        request.setAiAgentId(req.getAiAgentId());

        if ("chat".equalsIgnoreCase(req.getOutputStyle())) {
            request.setAgentType(AgentType.WORKFLOW.getValue());
            request.setSopPrompt("");
        } else if (req.getDeepThink() != null && req.getDeepThink() != 0) {
            request.setAgentType(AgentType.PLAN_SOLVE.getValue());
            request.setSopPrompt(reactorConfig.getReactorSopPrompt());
            request.setBasePrompt("");
        } else {
            request.setAgentType(AgentType.REACT.getValue());
            request.setSopPrompt("");
            request.setBasePrompt(reactorConfig.getReactorBasePrompt());
        }

        request.setIsStream(true);
        request.setOutputStyle(req.getOutputStyle());
        return request;
    }
}
