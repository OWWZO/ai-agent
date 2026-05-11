package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ReportToolOutput;

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
        ReportToolOutput output = invocation != null && invocation.getStructuredOutput() instanceof ReportToolOutput structuredOutput
                ? structuredOutput
                : null;
        String logicalType = normalizeFileType(output == null ? null : output.getFileType());
        String data = output == null ? "" : StringUtils.defaultString(output.getContent());
        if (StringUtils.isBlank(data) && invocation != null) {
            data = StringUtils.defaultString(invocation.getLlmObservation());
        }

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("isFinal", true);
        resultMap.put("data", data);
        resultMap.put("codeOutput", data);
        resultMap.put("fileInfo", mergeFileRefs(output == null ? null : output.getFileRefs(), artifacts));

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
