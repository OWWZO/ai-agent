package org.wwz.ai.domain.agent.reactor.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SOP召回请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SopRecallRequest {
    
    private String requestId;
    private String query;
}
