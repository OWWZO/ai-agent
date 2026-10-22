package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * rich tool 输出落表命令。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolOutputPersistCommand {

    private Long toolInvocationId;

    private Long runId;

    private String requestId;

    private String requestSource;

    private String sessionId;

    private String toolCallId;

    private String toolName;

    private Integer status;

    private String errorMsg;

    private ToolStructuredOutput structuredOutput;
}
