package org.wwz.ai.trigger.http.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 精品对话管理端响应项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationAdminRespVO {

    private String featuredId;

    private String sessionId;

    private String title;

    private String summary;

    private List<String> tags;

    private String coverUrl;

    private Integer sortOrder;

    private String status;

    private LocalDateTime publishedAt;

    private LocalDateTime updatedAt;
}
