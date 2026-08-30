package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;
import org.wwz.ai.domain.agent.runtime.llm.ContextUsagePayload;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话历史详情聚合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationHistoryDetail {

    private String sessionId;

    private String title;

    private Integer status;

    private Boolean deepThink;

    private Integer runCount;

    private Integer finishedRunCount;

    private Integer failedRunCount;

    private LocalDateTime startedAt;

    private LocalDateTime lastActiveAt;

    @Builder.Default
    private List<ConversationRunDetail> runs = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationRunDetail {

        private String requestId;

        private Integer status;

        private String queryText;

        private String finalSummaryText;

        private LocalDateTime startedAt;

        private LocalDateTime finishedAt;

        /** 本轮最后一次 LLM 调用的上下文占用快照。 */
        private ContextUsagePayload contextUsage;

        @Builder.Default
        private List<GptProcessResult> replayFrames = new ArrayList<>();
    }
}
