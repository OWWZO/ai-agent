package org.wwz.ai.infrastructure.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前端对话附件上传后的稳定文件信息。
 * <p>不使用 Lombok {@code @Builder}：避免运行时缺失 {@code $Builder} 内部类导致上传失败。</p>
 */
@Data
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

    public static ConversationUploadFileDTO of(String name,
                                               String url,
                                               String type,
                                               Long size,
                                               String downloadUrl,
                                               String previewUrl,
                                               String resourceKey,
                                               String mimeType,
                                               String originFileName) {
        ConversationUploadFileDTO dto = new ConversationUploadFileDTO();
        dto.setName(name);
        dto.setUrl(url);
        dto.setType(type);
        dto.setSize(size);
        dto.setDownloadUrl(downloadUrl);
        dto.setPreviewUrl(previewUrl);
        dto.setResourceKey(resourceKey);
        dto.setMimeType(mimeType);
        dto.setOriginFileName(originFileName);
        return dto;
    }
}
