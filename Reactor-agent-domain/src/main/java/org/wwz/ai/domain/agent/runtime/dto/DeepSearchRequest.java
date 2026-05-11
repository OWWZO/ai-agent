package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepSearchRequest {
    private String request_id;
    private String query;
    private String erp;
    private String agent_id;
    private Map<String, Object> optional_configs;
    private Map<String, Object> src_configs;
    private String scene_type;
    private Boolean stream;
    private Boolean content_stream;
}
