package org.wwz.ai.domain.agent.reactor.model.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 延期保留的数据 Agent 聊天请求契约。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataAgentChatReq {
    private String content;
    private String traceId;
}
