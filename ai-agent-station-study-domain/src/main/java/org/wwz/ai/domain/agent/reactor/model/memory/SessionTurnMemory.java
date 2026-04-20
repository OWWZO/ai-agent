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
     * 兼容现有压缩链路，仍保留用户问题的扁平字段。
     */
    private String userMessage;
    /**
     * 兼容现有压缩链路，仍保留最终回答的扁平字段。
     */
    private String assistantMessage;
    /**
     * 当前轮的有序 transcript blocks。
     */
    @Builder.Default
    private List<TranscriptContextBlock> blocks = new ArrayList<>();

    @Builder.Default
    private List<JSONObject> artifactRefs = new ArrayList<>();

    /**
     * 当前轮最终回答，供 richer message 和旧链路共同使用。
     */
    private String finalAnswer;
}
