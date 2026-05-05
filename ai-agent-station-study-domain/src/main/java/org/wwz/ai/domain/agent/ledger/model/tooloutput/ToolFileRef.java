package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一文件引用快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolFileRef {

    private String fileName;

    private String downloadUrl;

    private String previewUrl;

    private String ossUrl;

    private String domainUrl;

    private Long fileSize;

    private String mimeType;
}
