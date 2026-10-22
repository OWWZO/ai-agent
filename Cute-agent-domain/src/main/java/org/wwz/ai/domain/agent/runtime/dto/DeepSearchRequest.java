package org.wwz.ai.domain.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DeepSearch 工具请求模型，描述搜索问题、会话上下文和执行选项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepSearchRequest {
    private String request_id;
    private String query;
    private String report_file_name;
    private String erp;
    private String agent_id;
    private Map<String, Object> optional_configs;
    private Map<String, Object> src_configs;
    private String scene_type;
    private Boolean stream;
    private Boolean content_stream;
}
