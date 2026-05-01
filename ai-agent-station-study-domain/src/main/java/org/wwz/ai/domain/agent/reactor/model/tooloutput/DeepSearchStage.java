package org.wwz.ai.domain.agent.reactor.model.tooloutput;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * deep_search 单阶段快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepSearchStage {

    private String stage;

    @Builder.Default
    private List<String> queries = new ArrayList<>();

    @Builder.Default
    private List<DeepSearchQueryResult> results = new ArrayList<>();

    private String answer;
}
