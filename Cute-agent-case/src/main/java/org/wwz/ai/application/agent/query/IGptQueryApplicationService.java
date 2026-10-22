package org.wwz.ai.application.agent.query;

/**
 * GPT 查询应用服务接口。
 * trigger 进入主聊天链路的唯一应用层入口：协议翻译、会话守卫、进程内调度与事件投影。
 */
public interface IGptQueryApplicationService {

    /**
     * 提交一轮 Agent 执行。不占用 SSE；观察走 GET session stream。
     */
    AgentQuerySubmitResult submitAgentQuery(GptQueryCommand params);
}
