package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.FileToolOutput;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * file_tool projector。
 */
public class FileToolInvocationProjector extends AbstractToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return "file_tool".equals(toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        FileToolOutput output = invocation != null && invocation.getStructuredOutput() instanceof FileToolOutput structuredOutput
                ? structuredOutput
                : null;
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("command", translateCommand(output == null ? null : output.getCommand()));
        resultMap.put("fileInfo", mergeFileRefs(output == null ? null : output.getFileRefs(), artifacts));
        if (StringUtils.isNotBlank(output == null ? null : output.getPrimaryFileName())) {
            resultMap.put("primaryFileName", output.getPrimaryFileName());
        }
        if (StringUtils.isNotBlank(output == null ? null : output.getPreviewUrl())) {
            resultMap.put("previewUrl", output.getPreviewUrl());
        }
        if (StringUtils.isNotBlank(output == null ? null : output.getDownloadUrl())) {
            resultMap.put("downloadUrl", output.getDownloadUrl());
        }
        return List.of(buildTaskEvent(
                state,
                invocation,
                "file",
                buildStructuredToolResponse(invocation, "file", resultMap),
                buildArtifactRefs(artifacts)
        ));
    }

    private String translateCommand(String command) {
        if ("get".equals(command) || "读取文件".equals(command)) {
            return "读取文件";
        }
        if ("upload".equals(command) || "写入文件".equals(command)) {
            return "写入文件";
        }
        return StringUtils.defaultIfBlank(command, "文件操作");
    }
}
