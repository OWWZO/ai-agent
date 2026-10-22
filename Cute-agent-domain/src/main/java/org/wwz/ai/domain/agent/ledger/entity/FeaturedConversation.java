package org.wwz.ai.domain.agent.ledger.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 精品对话发布实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversation {

    private Long id;

    private String featuredId;

    private String sessionId;

    private String title;

    private String summary;

    private String coverResourceKey;

    private String coverUrl;

    private List<String> tags;

    private Integer sortOrder;

    private String status;

    private String publishedBy;

    private LocalDateTime publishedAt;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
