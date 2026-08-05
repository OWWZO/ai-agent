package org.wwz.ai.domain.agent.runtime.agent;

import lombok.Data;
import lombok.experimental.Accessors;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolStructuredOutput;

/**
 * 单次工具执行结果（展示、记忆写回、账本共用）。
 */
@Data
@Accessors(chain = true)
public class ToolExecutionOutcome {
    private boolean success;
    private String toolResult;
    private String llmObservation;
    private ToolStructuredOutput structuredOutput;
    private String errorMsg;
    private String base64Image;
    private String imageMimeType;

    static ToolExecutionOutcome success(String toolResult,
                                        String llmObservation,
                                        ToolStructuredOutput structuredOutput,
                                        String base64Image,
                                        String imageMimeType) {
        return new ToolExecutionOutcome()
                .setSuccess(true)
                .setToolResult(toolResult)
                .setLlmObservation(llmObservation)
                .setStructuredOutput(structuredOutput)
                .setBase64Image(base64Image)
                .setImageMimeType(imageMimeType);
    }

    static ToolExecutionOutcome failure(String toolResult,
                                        String llmObservation,
                                        ToolStructuredOutput structuredOutput,
                                        String errorMsg) {
        return new ToolExecutionOutcome()
                .setSuccess(false)
                .setToolResult(toolResult)
                .setLlmObservation(llmObservation)
                .setStructuredOutput(structuredOutput)
                .setErrorMsg(errorMsg);
    }
}
