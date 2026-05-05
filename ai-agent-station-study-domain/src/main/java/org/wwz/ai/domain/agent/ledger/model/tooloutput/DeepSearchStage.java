package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * deep_search 单阶段快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeepSearchStage {

    private String stage;

    private List<String> queries = new ArrayList<>();

    private List<DeepSearchQueryResult> results = new ArrayList<>();

    private String answer;

    /**
     * extend 阶段工厂。
     */
    public static DeepSearchStage extend(List<String> queries) {
        return new DeepSearchStage("extend", queries == null ? new ArrayList<>() : new ArrayList<>(queries), new ArrayList<>(), null);
    }

    /**
     * search 阶段工厂。
     */
    public static DeepSearchStage search(List<DeepSearchQueryResult> results) {
        return new DeepSearchStage("search", new ArrayList<>(), results == null ? new ArrayList<>() : new ArrayList<>(results), null);
    }

    /**
     * report 阶段工厂。
     */
    public static DeepSearchStage report(String answer) {
        return new DeepSearchStage("report", new ArrayList<>(), new ArrayList<>(), answer);
    }
}
