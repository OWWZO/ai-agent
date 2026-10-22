package org.wwz.ai.domain.agent.runtime.stream;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.ledger.model.replay.ReplayTiming;

import java.util.Map;

/**
 * Agent runtime 发出的领域流事件。
 * <p>它描述运行事实和 typed payload，不直接承担 HTTP response 的增量包装。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStreamEvent {

    private String requestId;
    private String messageId;
    private Boolean isFinal;
    private String messageType;
    private String digitalEmployee;
    private String messageTime;
    private ReplayTiming timing;
    private String planThought;
    private PlanStreamPayload plan;
    private String task;
    private String taskSummary;
    private String toolThought;
    private String reasoningContent;
    private ToolResultStreamPayload toolResult;
    private Map<String, Object> resultMap;
    private String result;
    private Boolean finish;
    private Map<String, String> ext;

    /**
     * Builder alias for callers that describe the envelope payload as event data.
     */
    public static class AgentStreamEventBuilder {
        public AgentStreamEventBuilder eventData(Map<String, Object> eventData) {
            return resultMap(eventData);
        }
    }
}
