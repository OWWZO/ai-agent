package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ScriptRunnerToolOutput;

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
        ScriptRunnerToolOutput output = invocation != null && invocation.getStructuredOutput() instanceof ScriptRunnerToolOutput structuredOutput
                ? structuredOutput
                : null;
        Map<String, Object> toolResult = new LinkedHashMap<>();
        toolResult.put("toolName", invocation == null ? null : invocation.getToolName());
        toolResult.put("toolParam", invocation == null ? Map.of() : readMap(invocation.getInputJson()));
        toolResult.put("toolResult", buildDisplayText(output, artifacts));

        return List.of(buildTaskEvent(
                state,
                invocation,
                "tool_result",
                buildToolResultResponse(invocation, toolResult),
                buildArtifactRefs(artifacts)
        ));
    }

    private String buildDisplayText(ScriptRunnerToolOutput output, List<ArtifactView> artifacts) {
        StringBuilder result = new StringBuilder();
        result.append("技能：").append(output == null ? "" : StringUtils.defaultString(output.getSkillName())).append("\n");
        result.append("脚本：").append(output == null ? "" : StringUtils.defaultString(output.getScriptName())).append("\n");
        result.append("运行时：").append(output == null ? "" : StringUtils.defaultString(output.getRuntime())).append("\n");
        result.append("是否成功：").append(output != null && Boolean.TRUE.equals(output.getSuccess())).append("\n");
        result.append("退出码：").append(output == null || output.getExitCode() == null ? -1 : output.getExitCode()).append("\n");
        result.append("摘要：").append(output == null ? "" : StringUtils.defaultString(output.getSummary())).append("\n");
        result.append("stdout:\n").append(output == null ? "" : StringUtils.defaultString(output.getStdout())).append("\n");
        result.append("stderr:\n").append(output == null ? "" : StringUtils.defaultString(output.getStderr())).append("\n");
        result.append("产出文件：\n");

        List<Map<String, Object>> fileInfo = mergeFileRefs(output == null ? null : output.getFileRefs(), artifacts);
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
