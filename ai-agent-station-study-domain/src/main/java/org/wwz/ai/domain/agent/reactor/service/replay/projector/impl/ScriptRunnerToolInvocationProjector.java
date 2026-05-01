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
 * script_runner_tool projector。
 */
public class ScriptRunnerToolInvocationProjector extends AbstractToolInvocationProjector {

    @Override
    public boolean supports(String toolName) {
        return "script_runner_tool".equals(toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        JsonNode root = readJson(invocation == null ? null : invocation.getOutputJson());
        Map<String, Object> toolResult = new LinkedHashMap<>();
        toolResult.put("toolName", invocation == null ? null : invocation.getToolName());
        toolResult.put("toolParam", invocation == null ? Map.of() : readMap(invocation.getInputJson()));
        toolResult.put("toolResult", buildDisplayText(root, artifacts));

        return List.of(buildTaskEvent(
                state,
                invocation,
                "tool_result",
                buildToolResultResponse(invocation, toolResult),
                buildArtifactRefs(artifacts)
        ));
    }

    private String buildDisplayText(JsonNode root, List<ArtifactView> artifacts) {
        StringBuilder result = new StringBuilder();
        result.append("技能：").append(root.path("skillName").asText("")).append("\n");
        result.append("脚本：").append(root.path("scriptName").asText("")).append("\n");
        result.append("运行时：").append(root.path("runtime").asText("")).append("\n");
        result.append("是否成功：").append(root.path("success").asBoolean(false)).append("\n");
        result.append("退出码：").append(root.path("exitCode").asInt(-1)).append("\n");
        result.append("摘要：").append(root.path("summary").asText("")).append("\n");
        result.append("stdout:\n").append(root.path("stdout").asText("")).append("\n");
        result.append("stderr:\n").append(root.path("stderr").asText("")).append("\n");
        result.append("产出文件：\n");

        List<Map<String, Object>> fileInfo = mergeFileInfo(root.path("fileInfo"), artifacts);
        if (fileInfo.isEmpty()) {
            result.append("- （无）\n");
        } else {
            for (Map<String, Object> item : fileInfo) {
                String fileName = String.valueOf(item.getOrDefault("fileName", ""));
                String url = StringUtils.defaultIfBlank(
                        String.valueOf(item.getOrDefault("domainUrl", "")),
                        String.valueOf(item.getOrDefault("downloadUrl", ""))
                );
                result.append("- ").append(fileName).append(" | ").append(url).append("\n");
            }
        }
        return result.toString();
    }
}
