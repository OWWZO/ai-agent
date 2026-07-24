package org.wwz.ai.domain.agent.memory;

import org.wwz.ai.domain.agent.runtime.dto.Message;

import java.util.List;

/**
 * 跨轮工作记忆投影服务（working_memory_*）。
 * load 供下一轮 preload；persist 在 run 成功结束后写入本轮 delta。
 */
public interface SessionWorkingMemoryService {

    /**
     * 加载 session 内 READY 消息链（排除当前 request），超限丢弃最旧 turn。
     */
    List<Message> loadReadyMessages(String sessionId, String currentRequestId);

    /**
     * 持久化本轮 delta 消息为一个 READY turn。
     */
    void persistTurn(String sessionId,
                     String requestId,
                     Long runId,
                     String entryAgent,
                     List<Message> turnMessages);
}
