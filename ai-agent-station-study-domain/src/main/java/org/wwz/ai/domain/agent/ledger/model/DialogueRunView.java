package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * run 查询视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogueRunView {

    private Long id;

    private String runUid;

    private String requestId;

    private String sessionId;

    private String visitorId;

    private String entryAgent;

    private Integer status;

    private String queryText;

    private String finalSummaryText;

    private Integer llmCallCount;

    private Integer toolCallCount;

    private Integer artifactCount;

    private Integer promptTokensTotal;

    private Integer completionTokensTotal;

    private Integer totalTokensTotal;

    private String errorCode;

    private String errorMsg;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long durationMs;

    private LocalDateTime createTime;

    private List<ArtifactView> artifactSummaries;
}
