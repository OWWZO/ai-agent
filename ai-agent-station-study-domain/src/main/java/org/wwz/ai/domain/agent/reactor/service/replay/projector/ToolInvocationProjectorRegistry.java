package org.wwz.ai.domain.agent.reactor.service.replay.projector;

import lombok.RequiredArgsConstructor;
import org.wwz.ai.domain.agent.reactor.model.ledger.ArtifactView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.replay.ProjectedReplayEvent;

import java.util.List;

/**
 * 按 tool_name 分发 projector。
 */
@RequiredArgsConstructor
public class ToolInvocationProjectorRegistry {

    private final List<ToolInvocationProjector> projectors;
    private final ToolInvocationProjector defaultProjector;

    /**
     * 每个 invocation 单独开启一个任务组，避免历史投影串组。
     */
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        state.renewTaskId();
        for (ToolInvocationProjector projector : projectors) {
            if (projector != null && projector.supports(invocation == null ? null : invocation.getToolName())) {
                return projector.project(invocation, artifacts, state);
            }
        }
        return defaultProjector.project(invocation, artifacts, state);
    }
}
