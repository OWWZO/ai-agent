package org.wwz.ai.application.agent.visitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 匿名访客解析结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnonymousVisitorIdentity {

    private String visitorId;

    private String rawToken;

    /**
     * 新建访客或无效 token 换新时为 true。
     */
    private boolean newlyCreated;
}
