package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * script_runner_tool 终态结构化输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptRunnerToolOutput implements ToolStructuredOutput {

    private String skillName;

    private String scriptName;

    private String runtime;

    private Boolean success;

    private Integer exitCode;

    private String stdout;

    private String stderr;

    private String summary;

    @Builder.Default
    private List<ToolFileRef> fileRefs = new ArrayList<>();

    @Override
    public String getToolName() {
        return ToolOutputNames.SCRIPT_RUNNER;
    }
}
