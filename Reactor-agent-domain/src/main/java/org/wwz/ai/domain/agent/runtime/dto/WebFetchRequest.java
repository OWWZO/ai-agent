package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * web_fetch 工具请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebFetchRequest {
    private String requestId;
    private String url;
    private Integer timeoutSeconds;
}
