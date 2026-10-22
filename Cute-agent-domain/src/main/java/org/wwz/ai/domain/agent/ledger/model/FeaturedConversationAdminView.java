package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 精品对话管理列表项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationAdminView {

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
