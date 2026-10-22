package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 结束 run 的命令对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogueRunFinishRecord {

    private Long runId;

    private String requestId;

    private Integer status;

    private String finalSummaryText;

    private String errorCode;

    private String errorMsg;

    private LocalDateTime finishedAt;
}
