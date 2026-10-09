package org.wwz.ai.domain.agent.ledger.replay.projector;

import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamAccumulator;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;

import java.util.List;

/**
 * 单工具账本投影器。
 */
public interface ToolInvocationProjector {

    /**
     * 是否支持该工具。
     */
    boolean supports(String toolName);

    /**
     * 把工具调用事实投影成前端可消费 eventData。
     */
    List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                       List<ArtifactView> artifacts,
                                       AgentStreamAccumulator state);
}
