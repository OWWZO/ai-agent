package org.wwz.ai.trigger.http.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.runtime.llm.ContextUsagePayload;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Full replay for one request/run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRunReplayRespVO {

    private String runUid;

    private String requestId;

    private String sessionId;

    private String entryAgent;

    private String status;

    private String queryText;

    private String finalSummaryText;

    private Integer llmCallCount;

    private Integer toolCallCount;

    private Integer artifactCount;

    private String errorCode;

    private String errorMsg;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long durationMs;

    private ContextUsagePayload contextUsage;

    @Builder.Default
    private List<AgentStreamResponseVO> replayFrames = new ArrayList<>();
}
