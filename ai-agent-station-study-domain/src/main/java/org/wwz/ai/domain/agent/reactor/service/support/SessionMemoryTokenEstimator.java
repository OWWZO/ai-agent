package org.wwz.ai.domain.agent.reactor.service.support;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionTurnMemory;
import org.wwz.ai.domain.agent.reactor.model.memory.TranscriptBlockType;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionWorkingMemory;
import org.wwz.ai.domain.agent.reactor.model.memory.TranscriptContextBlock;

import java.util.List;

/**
 * 会话记忆 token 粗估器。
 */
@Component
public class SessionMemoryTokenEstimator {

    public int estimateWorkingMemoryTokens(SessionWorkingMemory workingMemory) {
        if (workingMemory == null) {
            return 0;
        }
        return estimateWorkingMemoryTokens(
                workingMemory.getSummaryText(),
                workingMemory.getRecentTurns(),
                workingMemory.getHistoryDialogue());
    }

    public int estimateWorkingMemoryTokens(String summaryText,
                                           List<SessionTurnMemory> turns,
                                           String historyDialogue) {
        int textLength = safeLength(summaryText) + safeLength(historyDialogue);
        if (!CollectionUtils.isEmpty(turns)) {
            for (SessionTurnMemory turn : turns) {
                textLength += estimateTurnTextLength(turn);
            }
        }
        return textLength / 3;
    }

    public int estimateTurnTokens(SessionTurnMemory turn) {
        return estimateTurnTextLength(turn) / 3;
    }

    private int estimateTurnTextLength(SessionTurnMemory turn) {
        if (turn == null) {
            return 0;
        }
        int textLength = 0;
        if (!CollectionUtils.isEmpty(turn.getBlocks())) {
            for (TranscriptContextBlock block : turn.getBlocks()) {
                if (block == null) {
                    continue;
                }
                textLength += safeLength(block.getText());
                // 仅统计真正会回灌到 working memory prompt 的字段。
                // resultPayloadJson 只保留给恢复/排障使用，不会注入模型；如果继续统计，
                // HTML/Markdown 大产物会把 token 估算显著放大，触发误判拒绝。
                if (TranscriptBlockType.TOOL_USE == block.getBlockType()) {
                    textLength += safeLength(block.getToolArgumentsJson());
                }
            }
        }
        return textLength;
    }

    private int safeLength(String text) {
        return StringUtils.hasText(text) ? text.length() : 0;
    }
}
