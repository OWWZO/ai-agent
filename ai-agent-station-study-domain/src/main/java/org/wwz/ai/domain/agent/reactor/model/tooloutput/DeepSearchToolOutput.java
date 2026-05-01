package org.wwz.ai.domain.agent.reactor.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * deep_search 终态结构化输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepSearchToolOutput implements ToolStructuredOutput {

    private String query;

    private String answerSummary;

    @Builder.Default
    private List<DeepSearchStage> stages = new ArrayList<>();

    @Override
    public String getToolName() {
        return ToolOutputNames.DEEP_SEARCH;
    }
}
