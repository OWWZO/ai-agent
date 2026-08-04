package org.wwz.ai.domain.agent.memory.ltm;

import org.wwz.ai.domain.agent.runtime.dto.Message;

import java.util.List;

/**
 * 周期性后台整理（对齐 Hermes background review）。
 * 默认：同模型 + 全量会话 messages 重放 + 仅 memory 工具（prefix-cache 友好）。
 */
public interface BackgroundReviewService {

    void maybeScheduleAfterSuccessTurn(String sessionId,
                                       String requestId,
                                       LtmOwner owner,
                                       String userQuery,
                                       String assistantSummary,
                                       List<Message> conversationSnapshot,
                                       String parentSystemPrompt);
}
