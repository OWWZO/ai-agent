package org.wwz.ai.domain.agent.reactor.service.replay.projector.impl;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.model.ledger.ArtifactView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ImageGenerationToolOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * image_generation_tool projector。
 */
public class ImageGenerationToolInvocationProjector extends AbstractToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return "image_generation_tool".equals(toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        ImageGenerationToolOutput output = invocation != null && invocation.getStructuredOutput() instanceof ImageGenerationToolOutput structuredOutput
                ? structuredOutput
                : null;
        List<ProjectedReplayEvent> events = new ArrayList<>();
        List<Map<String, Object>> mergedFileInfo = mergeFileRefs(output == null ? null : output.getFileRefs(), artifacts);

        if (!mergedFileInfo.isEmpty()) {
            Map<String, Object> fileResult = new LinkedHashMap<>();
            fileResult.put("command", "生成图片");
            fileResult.put("fileInfo", mergedFileInfo);
            events.add(buildTaskEvent(
                    state,
                    invocation,
                    "file",
                    buildStructuredToolResponse(invocation, "file", fileResult),
                    buildArtifactRefs(artifacts)
            ));
        }

        String summary = output == null ? "" : StringUtils.defaultString(output.getSummary());
        if (StringUtils.isBlank(summary) && invocation != null) {
            summary = StringUtils.defaultString(invocation.getLlmObservation());
        }
        Map<String, Object> toolResult = new LinkedHashMap<>();
        toolResult.put("toolName", invocation == null ? null : invocation.getToolName());
        toolResult.put("toolParam", invocation == null ? Map.of() : readMap(invocation.getInputJson()));
        toolResult.put("toolResult", summary);
        events.add(buildTaskEvent(
                state,
                invocation,
                "tool_result",
                buildToolResultResponse(invocation, toolResult),
                List.of()
        ));
        return events;
    }
}
