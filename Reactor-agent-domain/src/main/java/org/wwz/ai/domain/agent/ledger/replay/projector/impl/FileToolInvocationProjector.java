package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamAccumulator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * file_tool 旧账本行的历史回放投影器。
 * <p>
 * 实时链路已改用 workspace_* 工具，本投影器只负责把旧账本里的 file_tool 事实恢复成
 * file 事件和 artifact 引用，避免旧会话回放退化成普通 tool_result。
 */
public class FileToolInvocationProjector extends AbstractToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return "file_tool".equals(toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              AgentStreamAccumulator state) {
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
