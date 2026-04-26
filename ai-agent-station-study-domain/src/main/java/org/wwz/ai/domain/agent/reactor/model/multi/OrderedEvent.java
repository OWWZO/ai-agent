package org.wwz.ai.domain.agent.reactor.model.multi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 按到达顺序缓存的事实块模型。
 * 这里只表达后端真实发生过的语义事件，不再承担前端最终态快照职责。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderedEvent {

    /** 内存投影阶段使用的逻辑去重键，不参与持久化 */
    private String dedupKey;
    private Integer seqNo;
    private String eventType;
    private String eventSubType;
    private String displayArea;
    private String taskId;
    private Integer taskOrder;
    private String messageIdExt;
    private String toolUseId;
    private String toolName;
    private String toolArgumentsJson;
    private boolean referenceOnly;
    private String artifactRefsJson;
    private String structuredDataJson;
    private boolean isFinal;
    private String title;
    private String contentText;
    private String payloadJson;
    private LocalDateTime eventTime;
}
