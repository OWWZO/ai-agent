package org.wwz.ai.domain.agent.ledger.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * deep_search 单个查询结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeepSearchQueryResult {

    private String query;

    private List<DeepSearchDoc> docs = new ArrayList<>();

    /**
     * 统一工厂，避免运行时依赖 Lombok Builder 内部类。
     */
    public static DeepSearchQueryResult of(String query, List<DeepSearchDoc> docs) {
        return new DeepSearchQueryResult(query, docs == null ? new ArrayList<>() : new ArrayList<>(docs));
    }
}
