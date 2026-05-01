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
 * code_interpreter projector。
 */
public class CodeInterpreterToolInvocationProjector extends AbstractToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return "code_interpreter".equals(toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        JsonNode root = readJson(invocation == null ? null : invocation.getOutputJson());
        String codeOutput = root.path("codeOutput").asText("");
        if (StringUtils.isBlank(codeOutput) && invocation != null) {
            codeOutput = StringUtils.defaultString(invocation.getLlmObservation());
        }

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("isFinal", true);
        resultMap.put("codeOutput", codeOutput);
        resultMap.put("data", codeOutput);
        if (StringUtils.isNotBlank(root.path("content").asText())) {
            resultMap.put("content", root.path("content").asText());
        }
        if (StringUtils.isNotBlank(root.path("code").asText())) {
            resultMap.put("code", root.path("code").asText());
        }
        if (StringUtils.isNotBlank(root.path("explain").asText())) {
            resultMap.put("explain", root.path("explain").asText());
        }
        resultMap.put("fileInfo", mergeFileInfo(root.path("fileInfo"), artifacts));

        return List.of(buildTaskEvent(
                state,
                invocation,
                "code",
                buildStructuredToolResponse(invocation, "code", resultMap),
                buildArtifactRefs(artifacts)
        ));
    }
}
