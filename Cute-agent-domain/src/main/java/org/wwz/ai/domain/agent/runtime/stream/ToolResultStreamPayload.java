package org.wwz.ai.domain.agent.runtime.stream;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具结果事件的 typed payload。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResultStreamPayload {

    private String toolName;
    private Map<String, Object> toolParam;
    private String toolResult;
    private String toolCallId;
}
