package org.wwz.ai.trigger.http.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 近期会话摘要返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSessionRespVO {

    private String sessionId;

    private String title;

    private String status;

    private String latestQueryText;

    private Integer runCount;

    private Integer finishedRunCount;

    private Integer failedRunCount;

    private LocalDateTime startedAt;

    private LocalDateTime lastActiveAt;
}
