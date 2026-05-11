package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.CodeInterpreterToolOutput;

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
        CodeInterpreterToolOutput output = invocation != null && invocation.getStructuredOutput() instanceof CodeInterpreterToolOutput structuredOutput
                ? structuredOutput
                : null;
        String codeOutput = output == null ? "" : StringUtils.defaultString(output.getCodeOutput());
        if (StringUtils.isBlank(codeOutput) && invocation != null) {
            codeOutput = StringUtils.defaultString(invocation.getLlmObservation());
        }

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("isFinal", true);
        resultMap.put("codeOutput", codeOutput);
        resultMap.put("data", codeOutput);
        if (StringUtils.isNotBlank(output == null ? null : output.getContent())) {
            resultMap.put("content", output.getContent());
        }
        if (StringUtils.isNotBlank(output == null ? null : output.getCode())) {
            resultMap.put("code", output.getCode());
        }
        if (StringUtils.isNotBlank(output == null ? null : output.getExplain())) {
            resultMap.put("explain", output.getExplain());
        }
        resultMap.put("fileInfo", mergeFileRefs(output == null ? null : output.getFileRefs(), artifacts));

        return List.of(buildTaskEvent(
                state,
                invocation,
                "code",
                buildStructuredToolResponse(invocation, "code", resultMap),
                buildArtifactRefs(artifacts)
        ));
    }
}
