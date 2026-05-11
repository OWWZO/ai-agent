package org.wwz.ai.trigger.http.agent.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArtifactReferenceRespVO {
    private String artifactType;
    private String displayName;
    private String resourceKey;
    private String downloadUrl;
    private String previewUrl;
    private Long fileSize;
    private String mimeType;
    private Boolean missing;
    private String missingReason;
}
