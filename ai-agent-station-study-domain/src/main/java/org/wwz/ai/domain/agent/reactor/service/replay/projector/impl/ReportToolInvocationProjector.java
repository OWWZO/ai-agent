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
 * report_tool projector。
 */
public class ReportToolInvocationProjector extends AbstractToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return "report_tool".equals(toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        JsonNode root = readJson(invocation == null ? null : invocation.getOutputJson());
        String logicalType = normalizeFileType(root.path("fileType").asText());
        String data = root.path("data").asText("");
        if (StringUtils.isBlank(data) && invocation != null) {
            data = StringUtils.defaultString(invocation.getLlmObservation());
        }

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("isFinal", true);
        resultMap.put("data", data);
        resultMap.put("codeOutput", data);
        resultMap.put("fileInfo", mergeFileInfo(root.path("fileInfo"), artifacts));

        return List.of(buildTaskEvent(
                state,
                invocation,
                logicalType,
                buildStructuredToolResponse(invocation, logicalType, resultMap),
                buildArtifactRefs(artifacts)
        ));
    }

    private String normalizeFileType(String fileType) {
        if ("html".equals(fileType) || "markdown".equals(fileType) || "ppt".equals(fileType)) {
            return fileType;
        }
        return "markdown";
    }
}
