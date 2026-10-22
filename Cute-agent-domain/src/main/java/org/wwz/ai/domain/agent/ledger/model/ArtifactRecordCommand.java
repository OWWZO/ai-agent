package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 写入输入/输出产物的命令对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactRecordCommand {

    private Long runId;

    private String requestId;

    private Long toolInvocationId;

    private String toolCallId;

    private String artifactRole;

    private String visibility;

    private String sourceType;

    private String sourceName;

    private String fileName;

    private String storageKey;

    private String downloadUrl;

    private String previewUrl;

    private String mimeType;

    private Long fileSize;

    private String fileHash;

    private String metadataJson;
}
