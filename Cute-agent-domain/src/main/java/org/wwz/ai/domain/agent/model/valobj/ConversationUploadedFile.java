package org.wwz.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话附件上传结果（领域值对象，不依赖 infrastructure DTO）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationUploadedFile {
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
