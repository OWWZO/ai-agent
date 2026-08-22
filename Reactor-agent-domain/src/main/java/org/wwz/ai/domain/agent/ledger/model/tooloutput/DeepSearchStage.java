package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * deep_search 单阶段快照。
 */
@Data
@NoArgsConstructor
public class DeepSearchStage {

    private String stage;

    private List<String> queries = new ArrayList<>();

    private List<DeepSearchQueryResult> results = new ArrayList<>();

    private String answer;

    private String chapterId;

    private String chapterTitle;

    private String chapterContent;

    private Integer chapterOrder;

    private String chapterSummary;

    /**
     * extend 阶段工厂。
     */
    public static DeepSearchStage extend(List<String> queries) {
        DeepSearchStage stage = new DeepSearchStage();
        stage.setStage("extend");
        stage.setQueries(queries == null ? new ArrayList<>() : new ArrayList<>(queries));
        return stage;
    }

    /**
     * search 阶段工厂。
     */
    public static DeepSearchStage search(List<DeepSearchQueryResult> results) {
        DeepSearchStage stage = new DeepSearchStage();
        stage.setStage("search");
        stage.setResults(results == null ? new ArrayList<>() : new ArrayList<>(results));
        return stage;
    }

    /**
     * chapter_summary 阶段工厂。
     */
    public static DeepSearchStage chapterSummary(String chapterId,
                                                  String chapterTitle,
                                                  String chapterContent,
                                                  Integer chapterOrder,
                                                 String chapterSummary,
                                                 List<String> queries,
                                                 List<DeepSearchQueryResult> results) {
        DeepSearchStage stage = new DeepSearchStage();
        stage.setStage("chapter_summary");
        stage.setChapterId(chapterId);
        stage.setChapterTitle(chapterTitle);
        stage.setChapterContent(chapterContent);
        stage.setChapterOrder(chapterOrder);
        stage.setChapterSummary(chapterSummary);
        stage.setAnswer(chapterSummary);
        stage.setQueries(queries == null ? new ArrayList<>() : new ArrayList<>(queries));
        stage.setResults(results == null ? new ArrayList<>() : new ArrayList<>(results));
        return stage;
    }

    /**
     * report 阶段工厂。
     */
    public static DeepSearchStage report(String answer) {
        DeepSearchStage stage = new DeepSearchStage();
        stage.setStage("report");
        stage.setAnswer(answer);
        return stage;
    }
}
