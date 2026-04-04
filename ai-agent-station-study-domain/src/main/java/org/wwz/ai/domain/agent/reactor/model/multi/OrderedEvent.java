package org.wwz.ai.domain.agent.reactor.model.multi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 按到达顺序缓存的事件模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderedEvent {

    private Integer seqNo;
    private String eventType;
    private String eventSubType;
    private String displayArea;
    private String taskId;
    private Integer taskOrder;
    private String messageIdExt;
    private boolean isFinal;
    private String title;
    private String contentText;
    private String payloadJson;
    private LocalDateTime eventTime;
}
