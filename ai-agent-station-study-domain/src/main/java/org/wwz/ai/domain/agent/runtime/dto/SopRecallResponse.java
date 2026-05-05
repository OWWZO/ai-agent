package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SOP召回响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SopRecallResponse {
    
    private Integer code;
    private SopRecallResult data;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SopRecallResult {
        private String sop_mode;
        private String choosed_sop_string;
    }
}
