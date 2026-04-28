package org.wwz.ai.domain.agent.reactor.model.memory;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 最近窗口中的单轮记忆
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionTurnMemory {

    private Long messageId;
    private String requestId;
    private Integer sortOrder;
    /**
     * 当前轮的有序 transcript blocks。
     */
    @Builder.Default
    private List<TranscriptContextBlock> blocks = new ArrayList<>();

    @Builder.Default
    private List<JSONObject> artifactRefs = new ArrayList<>();

    /**
     * 获取当前轮的用户输入文本。
     */
    public String getUserInputText() {
        return findFirstText(TranscriptBlockType.USER_INPUT);
    }

    /**
     * 获取当前轮的最终回答文本。
     */
    public String getAssistantAnswerText() {
        return findLastText(TranscriptBlockType.ASSISTANT_ANSWER);
    }

    private String findFirstText(TranscriptBlockType targetType) {
        if (targetType == null || blocks == null || blocks.isEmpty()) {
            return null;
        }
        for (TranscriptContextBlock block : blocks) {
            if (block != null && targetType == block.getBlockType() && block.getText() != null && !block.getText().isBlank()) {
                return block.getText();
            }
        }
        return null;
    }

    private String findLastText(TranscriptBlockType targetType) {
        if (targetType == null || blocks == null || blocks.isEmpty()) {
            return null;
        }
        for (int i = blocks.size() - 1; i >= 0; i--) {
            TranscriptContextBlock block = blocks.get(i);
            if (block != null && targetType == block.getBlockType() && block.getText() != null && !block.getText().isBlank()) {
                return block.getText();
            }
        }
        return null;
    }
}
