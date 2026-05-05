package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * data_analysis 终态结构化输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataAnalysisToolOutput implements ToolStructuredOutput {

    private String task;

    private String summary;

    private String content;

    @Builder.Default
    private List<ToolFileRef> fileRefs = new ArrayList<>();

    @Override
    public String getToolName() {
        return ToolOutputNames.DATA_ANALYSIS;
    }
}
