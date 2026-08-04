package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.CanvasPublishToolOutput;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.runtime.tool.common.canvas.CanvasToolNames;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * canvas_publish projector — replay as HTML preview task.
 */
/**
 * Canvas 发布工具的历史回放投影器，将发布结果转换为前端可展示的画布事件。
 */
public class CanvasPublishToolInvocationProjector extends AbstractToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return CanvasToolNames.CANVAS_PUBLISH.equals(toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        CanvasPublishToolOutput output = invocation != null
                && invocation.getStructuredOutput() instanceof CanvasPublishToolOutput structured
                ? structured
                : null;

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("isFinal", true);
        resultMap.put("fileType", "html");
        resultMap.put("command", "发布画布");
        if (output != null && StringUtils.isNotBlank(output.getTitle())) {
            resultMap.put("title", output.getTitle());
        }
        if (output != null && StringUtils.isNotBlank(output.getPrimaryFileName())) {
            resultMap.put("primaryFileName", output.getPrimaryFileName());
        }
        if (output != null && StringUtils.isNotBlank(output.getPreviewUrl())) {
            resultMap.put("previewUrl", output.getPreviewUrl());
        }
        if (output != null && StringUtils.isNotBlank(output.getDownloadUrl())) {
            resultMap.put("downloadUrl", output.getDownloadUrl());
        }
        resultMap.put("fileInfo", mergeFileRefs(output == null ? null : output.getFileRefs(), artifacts));

        return List.of(buildTaskEvent(
                state,
                invocation,
                "html",
                buildStructuredToolResponse(invocation, "html", resultMap),
                buildArtifactRefs(artifacts)
        ));
    }
}
