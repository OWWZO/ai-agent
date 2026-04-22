package org.wwz.ai.trigger.http.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentFileUploadRespVO {
    private String name;
    private String url;
    private String type;
    private Long size;
    private String downloadUrl;
    private String previewUrl;
    private String resourceKey;
    private String mimeType;
    private String originFileName;
}
