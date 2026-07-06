package org.wwz.ai.trigger.http.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 精品对话详情响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationDetailRespVO {

    private String featuredId;

    private String sessionId;

    private String title;

    private String summary;

    private String coverUrl;

    private List<String> tags;

    private String status;

    private LocalDateTime publishedAt;

    private LocalDateTime contentLastActiveAt;

    private Boolean contentAvailable;

    private String contentUnavailableReason;

    private ConversationHistoryDetailRespVO historyDetail;
}
