package org.wwz.ai.application.agent.query;

import org.wwz.ai.application.agent.stream.AgentSessionStream;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;

/**
 * GPT 查询应用服务接口。
 * trigger 进入主聊天链路的唯一应用层入口：协议翻译、会话守卫、进程内调度与事件投影。
 */
public interface IGptQueryApplicationService {

    void queryAgentStreamIncr(GptQueryReq params, AgentSessionStream stream);
}
