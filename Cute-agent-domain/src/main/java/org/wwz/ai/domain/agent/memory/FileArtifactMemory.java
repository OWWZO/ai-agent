package org.wwz.ai.domain.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单次工具调用关联的文件元信息。
 * 本期只保留文件账本事实，不读取文件正文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileArtifactMemory {

    private Long artifactId;

    private Long runId;

    private String requestId;

    private Long toolInvocationId;

    private String toolCallId;

    private String artifactRole;

    private String fileName;

    private String storageKey;

    private String downloadUrl;

    private String previewUrl;

    private String mimeType;

    private Long fileSize;
}
