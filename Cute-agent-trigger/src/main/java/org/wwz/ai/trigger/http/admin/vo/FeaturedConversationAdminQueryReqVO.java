package org.wwz.ai.trigger.http.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 精品对话管理端查询请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedConversationAdminQueryReqVO {

    private String status;

    private String sessionId;

    private String title;

    @Builder.Default
    private int pageNo = 1;

    @Builder.Default
    private int pageSize = 10;
}
