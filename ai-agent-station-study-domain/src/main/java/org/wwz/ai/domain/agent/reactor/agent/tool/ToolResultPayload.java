package org.wwz.ai.domain.agent.reactor.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果载体。
 * 显式区分原始结果、主智能体 observation 和结构化输出，避免多种语义混用一个字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResultPayload {

    /**
     * 工具原始文本结果。
     * 主要用于日志、调试和普通工具结果展示。
     */
    private String toolResult;

    /**
     * 回传给主智能体继续推理的 observation。
     */
    private String llmObservation;

    /**
     * 工具最终结构化输出。
     */
    private String outputJson;

    /**
     * 纯文本工具的快捷工厂。
     */
    public static ToolResultPayload text(String resultText) {
        return ToolResultPayload.builder()
                .toolResult(resultText)
                .llmObservation(resultText)
                .build();
    }
}
