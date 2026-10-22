package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamResult;
import org.wwz.ai.domain.agent.runtime.llm.ContextUsagePayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Full replay for one execution run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRunReplay {

    private DialogueRunView run;

    private ContextUsagePayload contextUsage;

    @Builder.Default
    private List<AgentStreamResult> replayFrames = new ArrayList<>();
}
