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

    private String userId;

    /**
     * 会话类型（落库为 code）。为空时由 SQL 默认值兜底为 {@code conversation}。
     */
    private String sessionKind;

    /** 父会话ID，仅审计与授权穿透；子会话不继承父工作记忆。 */
    private String parentSessionId;

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
