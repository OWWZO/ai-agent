package org.wwz.ai.trigger.http.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ConversationEventRespVO {
    private Integer seqNo;
    private String eventType;
    private String eventSubType;
    private String displayArea;
    private String taskId;
    private Integer taskOrder;
    private String messageIdExt;
    private String title;
    private String contentText;
    private String status;
    private Integer isFinal;
    private Map<String, Object> payload;
}
