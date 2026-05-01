package org.wwz.ai.domain.agent.reactor.service.replay.projector.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.model.ledger.ArtifactView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.replay.ProjectedReplayEvent;

import java.util.ArrayList;
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
        JsonNode root = readJson(invocation == null ? null : invocation.getOutputJson());
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("command", translateCommand(root.path("command").asText()));
        resultMap.put("fileInfo", mergeFileInfo(root.path("fileInfo"), artifacts));
        if (StringUtils.isNotBlank(root.path("contentStorageMode").asText())) {
            resultMap.put("contentStorageMode", root.path("contentStorageMode").asText());
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
