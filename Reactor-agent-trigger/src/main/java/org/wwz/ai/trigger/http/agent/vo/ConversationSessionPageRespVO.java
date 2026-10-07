package org.wwz.ai.trigger.http.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会话列表分页响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSessionPageRespVO {

    private List<ConversationSessionRespVO> sessions;

    private String nextCursor;

    private boolean hasMore;
}
