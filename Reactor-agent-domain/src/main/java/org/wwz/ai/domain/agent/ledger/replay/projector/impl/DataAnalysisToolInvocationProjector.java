package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.DataAnalysisToolOutput;

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
        DataAnalysisToolOutput output = invocation != null && invocation.getStructuredOutput() instanceof DataAnalysisToolOutput structuredOutput
                ? structuredOutput
                : null;
        String data = output == null ? "" : StringUtils.defaultString(output.getContent());
        if (StringUtils.isBlank(data) && invocation != null) {
            data = StringUtils.defaultString(invocation.getLlmObservation());
        }

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("isFinal", true);
        resultMap.put("task", output == null ? "" : StringUtils.defaultString(output.getTask()));
        resultMap.put("data", data);
        resultMap.put("fileInfo", mergeFileRefs(output == null ? null : output.getFileRefs(), artifacts));

        return List.of(buildTaskEvent(
                state,
                invocation,
                "data_analysis",
                buildStructuredToolResponse(invocation, "data_analysis", resultMap),
                buildArtifactRefs(artifacts)
        ));
    }
}
