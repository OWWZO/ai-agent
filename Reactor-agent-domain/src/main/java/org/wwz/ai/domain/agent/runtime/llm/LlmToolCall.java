package org.wwz.ai.domain.agent.runtime.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 供应商无关的工具调用结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmToolCall {

    private String id;
    private String type;
    private String name;
    private String arguments;
}
