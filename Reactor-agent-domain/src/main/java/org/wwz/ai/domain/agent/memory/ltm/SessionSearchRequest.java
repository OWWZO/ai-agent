package org.wwz.ai.domain.agent.memory.ltm;

import lombok.Builder;
import lombok.Data;

/**
 * session_search 的统一请求。模式由参数推断，不向模型暴露额外的 mode 字段。
 */
@Data
@Builder
public class SessionSearchRequest {

    private String sessionId;
    private String currentSessionId;
    private String visitorId;
    private String query;
    /** Null = default. Explicit 0 or negative is a validation error. */
    private Integer limit;
    private String scope;
    /** All modes. Comma-separated roles; default user,assistant (hides TOOL). */
    private String roleFilter;
    private Long aroundMessageId;
    /** Scroll only. Null = default. Explicit 0 or negative is a validation error. */
    private Integer window;
}
