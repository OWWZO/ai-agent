package org.wwz.ai.domain.agent.memory.ltm;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 在 AgentContext 就绪后初始化 LTM（owner、prefetch）。
 */
public final class LtmRuntimeBootstrap {

    private LtmRuntimeBootstrap() {
    }

    public static void bootstrap(AgentContext agentContext, AgentRequest request) {
        if (agentContext == null || request == null) {
            return;
        }
        if (LtmMemoryGuard.isSkipMemory(agentContext)) {
            agentContext.setLtmMemoryContext(null);
            return;
        }
        ReactorRuntimeDependencies deps = agentContext.getRuntimeDependencies();
        if (deps == null) {
            return;
        }
        LtmManager ltmManager = deps.getOptionalLtmManager();
        if (ltmManager == null) {
            return;
        }
        LtmOwner owner = LtmOwnerResolver.resolve(request.getVisitorId(), request.getErp());
        agentContext.setLtmOwner(owner);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("requestId", request.getRequestId());
        ctx.put("agentType", request.getAgentType());
        ltmManager.initializeAll(request.getSessionId(), owner, ctx);
        if (StringUtils.isNotBlank(request.getQuery())) {
            String fenced = ltmManager.prefetchAll(request.getQuery(), request.getSessionId());
            agentContext.setLtmMemoryContext(fenced);
        }
    }
}
