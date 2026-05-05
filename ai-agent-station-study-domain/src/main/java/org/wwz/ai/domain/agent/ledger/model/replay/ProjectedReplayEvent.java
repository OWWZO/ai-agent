package org.wwz.ai.domain.agent.ledger.model.replay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 回放投影后的 eventData 视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectedReplayEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String taskId;
    private Integer taskOrder;
    private String messageId;
    private String messageType;
    private Integer messageOrder;
    private Object resultMap;
    private List<Map<String, Object>> artifactRefs;
}
