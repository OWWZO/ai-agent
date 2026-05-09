package org.wwz.ai.trigger.http.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 访客 bootstrap 响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitorBootstrapRespVO {

    private String visitorId;

    private String username;

    private boolean named;
}
