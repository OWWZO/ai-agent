package org.wwz.ai.domain.agent.runtime.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolStructuredOutput;

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
     * rich tool 强类型输出。
     */
    private ToolStructuredOutput structuredOutput;

    /**
     * 是否失败。
     */
    @Builder.Default
    private Boolean failed = Boolean.FALSE;

    /**
     * 工具错误信息。
     */
    private String errorMsg;

    /**
     * 纯文本工具的快捷工厂。
     */
    public static ToolResultPayload text(String resultText) {
        return ToolResultPayload.builder()
                .toolResult(resultText)
                .llmObservation(resultText)
                .failed(Boolean.FALSE)
                .build();
    }

    /**
     * rich tool 强类型输出快捷工厂。
     */
    public static ToolResultPayload structured(String toolResult,
                                               String llmObservation,
                                               ToolStructuredOutput structuredOutput) {
        return ToolResultPayload.builder()
                .toolResult(toolResult)
                .llmObservation(llmObservation)
                .structuredOutput(structuredOutput)
                .failed(Boolean.FALSE)
                .build();
    }

    /**
     * 失败结果快捷工厂。
     */
    public static ToolResultPayload failure(String toolResult,
                                            String llmObservation,
                                            ToolStructuredOutput structuredOutput,
                                            String errorMsg) {
        return ToolResultPayload.builder()
                .toolResult(toolResult)
                .llmObservation(llmObservation)
                .structuredOutput(structuredOutput)
                .failed(Boolean.TRUE)
                .errorMsg(errorMsg)
                .build();
    }
}
