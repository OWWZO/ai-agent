package org.wwz.ai.domain.agent.runtime.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 供应商无关的流式增量事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmStreamEvent {

    private String content;
    private String reasoningContent;
    private List<LlmToolCall> toolCalls;
    private String finishReason;
    private LlmUsage usage;
}
