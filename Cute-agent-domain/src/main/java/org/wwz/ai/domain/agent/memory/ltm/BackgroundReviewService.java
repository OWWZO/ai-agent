package org.wwz.ai.domain.agent.memory.ltm;

import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.List;

/**
 * 周期性后台整理。
 * Hermes 对齐：同模型 + 父 system/tools 原样 + 全量 messages 重放 + runtime memory 白名单。
 */
public interface BackgroundReviewService {

    default void maybeScheduleAfterSuccessTurn(String sessionId,
                                               String requestId,
                                               LtmOwner owner,
                                               String userQuery,
                                               String assistantSummary,
                                               List<Message> conversationSnapshot,
                                               String parentSystemPrompt) {
        maybeScheduleAfterSuccessTurn(
                sessionId,
                requestId,
                owner,
                userQuery,
                assistantSummary,
                conversationSnapshot,
                parentSystemPrompt,
                null);
    }

    void maybeScheduleAfterSuccessTurn(String sessionId,
                                       String requestId,
                                       LtmOwner owner,
                                       String userQuery,
                                       String assistantSummary,
                                       List<Message> conversationSnapshot,
                                       String parentSystemPrompt,
                                       ToolCollection parentTools);
}
