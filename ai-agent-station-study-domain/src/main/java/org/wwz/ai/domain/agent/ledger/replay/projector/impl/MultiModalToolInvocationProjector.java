package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.MultimodalAgentToolOutput;

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
        MultimodalAgentToolOutput output = invocation != null && invocation.getStructuredOutput() instanceof MultimodalAgentToolOutput structuredOutput
                ? structuredOutput
                : null;
        String markdown = output == null ? "" : StringUtils.defaultString(output.getMarkdownContent());
        if (StringUtils.isBlank(markdown) && invocation != null) {
            markdown = StringUtils.defaultString(invocation.getLlmObservation());
        }

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("isFinal", true);
        resultMap.put("data", markdown);
        resultMap.put("fileInfo", mergeFileRefs(output == null ? null : output.getFileRefs(), artifacts));
        if (StringUtils.isNotBlank(output == null ? null : output.getSummary())) {
            resultMap.put("summary", output.getSummary());
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
