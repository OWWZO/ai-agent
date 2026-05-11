package org.wwz.ai.domain.agent.runtime.artifact;

import lombok.Builder;
import lombok.Value;
import org.wwz.ai.domain.agent.runtime.dto.File;

/**
 * 工具调用与生成文件之间的显式绑定关系。
 */
@Value
@Builder
public class ToolArtifactBinding {
    ToolArtifactSource source;
    File file;

    public boolean isInternalFile() {
        return file != null && Boolean.TRUE.equals(file.getIsInternalFile());
    }
}
