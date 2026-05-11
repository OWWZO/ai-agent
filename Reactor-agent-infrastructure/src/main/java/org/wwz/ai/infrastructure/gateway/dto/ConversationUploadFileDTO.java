package org.wwz.ai.infrastructure.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前端对话附件上传后的稳定文件信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationUploadFileDTO {
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
