package org.wwz.ai.trigger.http.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 精品对话卡片响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationCardRespVO {

    private String featuredId;

    private String sessionId;

    private String title;

    private String summary;

    private String coverUrl;

    private List<String> tags;

    private LocalDateTime publishedAt;

    private LocalDateTime contentLastActiveAt;
}
