package org.wwz.ai.trigger.http.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 匿名访客首次命名请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitorNamingReqVO {

    private String username;
}
