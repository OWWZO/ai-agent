package org.wwz.ai.domain.agent.reactor.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * deep_search 单个查询结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepSearchQueryResult {

    private String query;

    @Builder.Default
    private List<DeepSearchDoc> docs = new ArrayList<>();
}
