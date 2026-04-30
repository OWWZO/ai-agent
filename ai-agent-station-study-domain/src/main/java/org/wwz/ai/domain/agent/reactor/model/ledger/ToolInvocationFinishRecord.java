package org.wwz.ai.domain.agent.reactor.model.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 完成工具调用的命令对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInvocationFinishRecord {

    private Long toolInvocationId;

    private String requestId;

    private String toolCallId;

    private Integer status;

    private String outputText;

    private String outputJson;

    private String errorMsg;

    private LocalDateTime finishedAt;
}
