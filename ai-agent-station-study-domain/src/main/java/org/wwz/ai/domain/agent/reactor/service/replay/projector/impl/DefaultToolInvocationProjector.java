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
 * 默认 fallback projector。
 */
public class DefaultToolInvocationProjector extends AbstractToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return false;
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        JsonNode root = readJson(invocation == null ? null : invocation.getOutputJson());
        String resultType = root.path("resultType").asText();
        String text = root.path("data").path("text").asText("");
        if ("error".equals(resultType)) {
            text = root.path("data").path("message").asText(text);
        }
        if (StringUtils.isBlank(text) && invocation != null) {
            text = StringUtils.defaultIfBlank(invocation.getLlmObservation(), invocation.getErrorMsg());
        }

        Map<String, Object> toolResult = new LinkedHashMap<>();
        toolResult.put("toolName", invocation == null ? null : invocation.getToolName());
        toolResult.put("toolParam", invocation == null ? Map.of() : readMap(invocation.getInputJson()));
        toolResult.put("toolResult", text);

        return List.of(buildTaskEvent(
                state,
                invocation,
                "tool_result",
                buildToolResultResponse(invocation, toolResult),
                buildArtifactRefs(artifacts)
        ));
    }
}
