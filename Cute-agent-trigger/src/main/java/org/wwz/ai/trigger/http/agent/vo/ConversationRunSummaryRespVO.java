package org.wwz.ai.trigger.http.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Run summary without replay or rich output fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRunSummaryRespVO {

    private String requestId;

    private String entryAgent;

    private String status;

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
