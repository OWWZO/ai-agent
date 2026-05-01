package org.wwz.ai.domain.agent.reactor.service.replay.projector.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.model.ledger.ArtifactView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.replay.ProjectedReplayEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * multimodalagent_tool projector。
 */
public class MultiModalToolInvocationProjector extends AbstractToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return "multimodalagent_tool".equals(toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        JsonNode root = readJson(invocation == null ? null : invocation.getOutputJson());
        String markdown = root.path("markdown").asText("");
        if (StringUtils.isBlank(markdown) && invocation != null) {
            markdown = StringUtils.defaultString(invocation.getLlmObservation());
        }

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("isFinal", true);
        resultMap.put("data", markdown);
        resultMap.put("fileInfo", mergeFileInfo(root.path("fileInfo"), artifacts));
        if (StringUtils.isNotBlank(root.path("summary").asText())) {
            resultMap.put("summary", root.path("summary").asText());
        }

        return List.of(buildTaskEvent(
                state,
                invocation,
                "markdown",
                buildStructuredToolResponse(invocation, "markdown", resultMap),
                buildArtifactRefs(artifacts)
        ));
    }
}
