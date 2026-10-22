package org.wwz.ai.trigger.http.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Session history summary page.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationHistoryPageRespVO {

    private String sessionId;

    private String title;

    private String status;

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
    private List<ConversationRunSummaryRespVO> runs = new ArrayList<>();

    private String nextCursor;

    @Builder.Default
    private boolean hasMore = false;
}
