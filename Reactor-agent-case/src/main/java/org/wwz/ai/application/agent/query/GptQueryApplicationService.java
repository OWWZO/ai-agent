package org.wwz.ai.application.agent.query;

import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.runtime.AgentQueryService;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;

import javax.annotation.Resource;

/**
 * GPT 查询应用服务。
 * 通过稳定 runtime seam 进入多智能体查询主链路，避免 case 层继续桥接 legacy reactor service。
 */
@Service
public class GptQueryApplicationService implements IGptQueryApplicationService {

    @Resource
    private AgentQueryService agentQueryService;

    @Override
    public void queryAgentStreamIncr(GptQueryReq params, AgentSessionStream stream) {
        agentQueryService.queryAgentStreamIncr(params, stream);
    }
}
