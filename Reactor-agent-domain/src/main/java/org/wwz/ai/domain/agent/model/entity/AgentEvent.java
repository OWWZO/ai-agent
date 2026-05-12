package org.wwz.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentEvent {
    private String taskId;
    private String messageType; // "plan", "task", "plan_thought", "tool_thought", "tool_result"
    private Map<String, Object> resultMap;
    private String messageId;
    private Boolean finish;
    private Boolean isFinal;
}
