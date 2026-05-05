package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * direct tool call 或 reader 查询后的统一输出视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolOutputView {

    private String toolName;

    private String requestId;

    private String requestSource;

    private String sessionId;

    private String toolCallId;

    private Integer status;

    private String errorMsg;

    private LocalDateTime createdAt;

    private ToolStructuredOutput structuredOutput;
}
