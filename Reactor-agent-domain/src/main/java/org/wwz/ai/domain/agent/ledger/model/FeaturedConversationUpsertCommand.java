package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 精品对话新增或更新命令。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationUpsertCommand {

    private String featuredId;

    private String sessionId;

    private String title;

    private String summary;

    private String coverResourceKey;

    private String coverUrl;

    private List<String> tags;

    private Integer sortOrder;

    private String operator;
}
