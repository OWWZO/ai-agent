package org.wwz.ai.domain.agent.reactor.model.memory;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 从消息账本和事件账本恢复出的最小 transcript 上下文块。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptContextBlock {

    private TranscriptBlockType blockType;
    private Long sourceMessageId;
    private Integer sourceSeqNo;
    private String role;
    private String text;
    private String toolUseId;
    private String toolName;
    private String toolArgumentsJson;
    private String resultPayloadJson;

    @Builder.Default
    private List<JSONObject> artifactRefs = new ArrayList<>();

    private Boolean referenceOnly;
}
