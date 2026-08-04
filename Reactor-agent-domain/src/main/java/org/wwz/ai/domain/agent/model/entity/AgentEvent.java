package org.wwz.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;


/**
 * 延期保留的 Agent 事件传输模型，承载任务消息类型、结果和完成标记。
 */
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
