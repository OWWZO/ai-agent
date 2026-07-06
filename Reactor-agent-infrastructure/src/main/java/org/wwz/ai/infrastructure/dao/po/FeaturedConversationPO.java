package org.wwz.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 精品对话持久化对象。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationPO {

    private Long id;

    private String featuredId;

    private String sessionId;

    private String title;

    private String summary;

    private String coverResourceKey;

    private String coverUrl;

    private String tagsJson;

    private Integer sortOrder;

    private String status;

    private String publishedBy;

    private LocalDateTime publishedAt;

    private String updatedBy;

    private LocalDateTime updatedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
