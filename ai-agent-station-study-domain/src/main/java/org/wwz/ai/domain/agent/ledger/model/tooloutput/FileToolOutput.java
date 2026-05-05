package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * file_tool 终态结构化输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileToolOutput implements ToolStructuredOutput {

    private String command;

    private String primaryFileName;

    private String previewUrl;

    private String downloadUrl;

    @Builder.Default
    private List<ToolFileRef> fileRefs = new ArrayList<>();

    @Override
    public String getToolName() {
        return ToolOutputNames.FILE_TOOL;
    }
}
