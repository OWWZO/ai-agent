package org.wwz.ai.domain.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;

import java.util.List;

/**
 * 提示词记忆流中的单条模型可见消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptMemoryMessage {

    private RoleType role;

    private String content;

    private String base64Image;

    private String toolCallId;

    private List<ToolCall> toolCalls;
}
