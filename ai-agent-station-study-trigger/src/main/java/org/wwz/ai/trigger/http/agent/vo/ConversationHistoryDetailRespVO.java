package org.wwz.ai.trigger.http.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.reactor.model.response.GptProcessResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话历史详情返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationHistoryDetailRespVO {

    private String sessionId;

    private String title;

    private String status;

    private String outputStyle;

    private Boolean deepThink;

    private ConversationRoleRespVO role;

    private Integer runCount;

    private Integer finishedRunCount;

    private Integer failedRunCount;

    private LocalDateTime startedAt;

    private LocalDateTime lastActiveAt;

    @Builder.Default
    private List<RunDetailRespVO> runs = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunDetailRespVO {

        private String requestId;

        private String status;

        private String queryText;

        private String finalSummaryText;

        private LocalDateTime startedAt;

        private LocalDateTime finishedAt;

        @Builder.Default
        private List<GptProcessResult> replayFrames = new ArrayList<>();
    }
}
