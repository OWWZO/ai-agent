package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Run summary used by history pages.
 *
 * <p>The query is a preview; the final answer is complete. Rich tool output,
 * artifacts and replay frames are loaded by the single-run replay endpoint.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRunSummary {

    private String requestId;

    private String entryAgent;

    private Integer status;

    private String queryPreview;

    private String finalSummaryPreview;

    private String finalSummaryText;

    private Integer llmCallCount;

    private Integer toolCallCount;

    private Integer artifactCount;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long durationMs;

    private Boolean hasReplay;
}
