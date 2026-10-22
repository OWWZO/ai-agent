package org.wwz.ai.trigger.http.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 精品对话管理端新增或更新请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationAdminUpsertReqVO {

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
