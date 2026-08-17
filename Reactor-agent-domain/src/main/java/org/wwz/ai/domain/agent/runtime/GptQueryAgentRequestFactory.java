package org.wwz.ai.domain.agent.runtime;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
     * 补齐查询请求缺省字段，并生成 traceId（仅日志/追踪用）。
     * <p>run 身份必须用前端 requestId：ActiveAgentRunRegistry / follow / stop 都按该 id 查找。
     * 旧逻辑把 requestId 改写成 user+sessionId:requestId，导致 follow 用前端原始 id 永远 idle，
     * 刷新后 ledger 用改写后的 id 反而能续上。</p>
     */
    public void normalize(GptQueryReq req) {
        if (req == null) {
            return;
        }
        req.setUser(req.getUser() == null ? "reactor" : req.getUser());
        req.setDeepThink(req.getDeepThink() == null ? 0 : req.getDeepThink());
        // traceId 仅作追踪复合键；不要覆盖或替代 requestId
        if (StringUtils.isBlank(req.getTraceId())) {
            req.setTraceId(ChateiUtils.getRequestId(req));
        }
    }

    /**
     * 将前端查询请求映射为统一执行请求。
     */
    public AgentRequest build(GptQueryReq req) {
        AgentRequest request = new AgentRequest();
        // 优先前端 requestId，保证与 follow/stop/UI chat.requestId 一致
        String runRequestId = StringUtils.isNotBlank(req.getRequestId())
                ? req.getRequestId().trim()
                : req.getTraceId();
        request.setRequestId(runRequestId);
        request.setSessionId(req.getSessionId());
        request.setVisitorId(VisitorRequestContext.currentVisitorId());
        request.setErp(req.getUser());
        request.setQuery(req.getQuery());
        request.setSessionFiles(req.getSessionFiles());
        request.setAiAgentId(req.getAiAgentId());
        request.setModel(StringUtils.trimToNull(req.getModel()));
        request.setThinking(req.getThinking());
        request.setThinkingEffort(StringUtils.trimToNull(req.getThinkingEffort()));

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
