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
 * data_analysis projector。
 */
public class DataAnalysisToolInvocationProjector extends AbstractToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return "data_analysis".equals(toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        JsonNode root = readJson(invocation == null ? null : invocation.getOutputJson());
        String data = root.path("data").asText("");
        if (StringUtils.isBlank(data) && invocation != null) {
            data = StringUtils.defaultString(invocation.getLlmObservation());
        }

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("isFinal", true);
        resultMap.put("task", root.path("task").asText(""));
        resultMap.put("data", data);
        resultMap.put("fileInfo", mergeFileInfo(root.path("fileInfo"), artifacts));

        return List.of(buildTaskEvent(
                state,
                invocation,
                "data_analysis",
                buildStructuredToolResponse(invocation, "data_analysis", resultMap),
                buildArtifactRefs(artifacts)
        ));
    }
}
