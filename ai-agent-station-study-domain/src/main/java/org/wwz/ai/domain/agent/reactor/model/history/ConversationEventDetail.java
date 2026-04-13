package org.wwz.ai.domain.agent.reactor.model.history;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单轮历史事件详情。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationEventDetail {

    private Integer seqNo;
    private String eventType;
    private String eventSubType;
    private String displayArea;
    private String taskId;
    private Integer taskOrder;
    private String messageIdExt;
    private String title;
    private String contentText;
    private Object payload;
    private Integer isFinal;
    private String status;
}
