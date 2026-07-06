package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 精品对话管理查询条件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationQueryCondition {

    private String status;

    private String sessionId;

    private String title;

    private int offset;

    private int limit;
}
