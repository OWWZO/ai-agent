package org.wwz.ai.domain.agent.reactor.model.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单次执行明细聚合视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRunDetail {

    private DialogueRunView run;

    private List<LlmInvocationView> llmInvocations;

    private List<ToolInvocationView> toolInvocations;

    private List<ArtifactView> artifacts;
}
