package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话主表 upsert 命令。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogueSessionUpsertRecord {

    private String sessionId;

    private String visitorId;

    private String title;

    private Integer status;

    private String latestRequestId;

    private String latestQueryText;

    private String latestSummaryText;

    private Integer runCount;

    private Integer finishedRunCount;

    private Integer failedRunCount;

    private LocalDateTime startedAt;

    private LocalDateTime lastActiveAt;
}
