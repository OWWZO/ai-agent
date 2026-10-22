package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * deep_search 终态结构化输出。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeepSearchToolOutput implements ToolStructuredOutput {

    private String query;

    private String answerSummary;

    private List<DeepSearchStage> stages = new ArrayList<>();

    private List<DeepSearchChapter> chapters = new ArrayList<>();

    /**
     * 统一工厂，避免运行时依赖 Lombok Builder 内部类。
     */
    public static DeepSearchToolOutput of(String query, String answerSummary, List<DeepSearchStage> stages) {
        return of(query, answerSummary, stages, null);
    }

    public static DeepSearchToolOutput of(String query,
                                          String answerSummary,
                                          List<DeepSearchStage> stages,
                                          List<DeepSearchChapter> chapters) {
        return new DeepSearchToolOutput(
                query,
                answerSummary,
                stages == null ? new ArrayList<>() : new ArrayList<>(stages),
                chapters == null ? new ArrayList<>() : new ArrayList<>(chapters)
        );
    }

    @Override
    public String getToolName() {
        return ToolOutputNames.DEEP_SEARCH;
    }
}
