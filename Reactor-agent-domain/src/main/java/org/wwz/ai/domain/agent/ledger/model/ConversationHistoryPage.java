package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight, keyset-paged session history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationHistoryPage {

    private String sessionId;

    private String title;

    private Integer status;

    private Boolean deepThink;

    private String latestRequestId;

    private String latestQueryPreview;

    private String latestSummaryPreview;

    private Integer runCount;

    private Integer finishedRunCount;

    private Integer failedRunCount;

    private LocalDateTime startedAt;

    private LocalDateTime lastActiveAt;

    @Builder.Default
    private List<ConversationRunSummary> runs = new ArrayList<>();

    private String nextCursor;

    @Builder.Default
    private boolean hasMore = false;
}
