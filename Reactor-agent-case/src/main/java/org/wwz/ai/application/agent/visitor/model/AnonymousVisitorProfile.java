package org.wwz.ai.application.agent.visitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前匿名访客最小状态视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnonymousVisitorProfile {

    private String visitorId;

    private String username;

    private boolean named;
}
