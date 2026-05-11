package org.wwz.ai.domain.agent.runtime;

import org.wwz.ai.domain.agent.adapter.port.AgentMessageStream;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;

/**
 * GPT 查询与多智能体请求的稳定运行时 seam。
 * 只暴露领域查询语义，不再让 case 依赖 legacy reactor bridge。
 */
public interface AgentQueryService {

    /**
     * 处理增量流式查询请求。
     *
     * @param req    查询请求
     * @param stream 领域输出流
     */
    void queryAgentStreamIncr(GptQueryReq req, AgentMessageStream stream);
}
