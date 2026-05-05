package org.wwz.ai.domain.agent.ledger.replay.projector;

import lombok.RequiredArgsConstructor;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;

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
        return project(invocation, artifacts, state, false);
    }

    /**
     * 历史回放有两种分组模式：
     * 1. 独立工具回放：每个 invocation 单独开启一个任务组；
     * 2. 与上一条 thought 绑定：复用当前任务组，保证“先思考、后工具”紧邻展示。
     */
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state,
                                              boolean reuseCurrentTaskGroup) {
        if (!reuseCurrentTaskGroup && !supportsPlannerTaskGrouping(invocation)) {
            state.renewTaskId();
        }
        for (ToolInvocationProjector projector : projectors) {
            if (projector != null && projector.supports(invocation == null ? null : invocation.getToolName())) {
                return projector.project(invocation, artifacts, state);
            }
        }
        return defaultProjector.project(invocation, artifacts, state);
    }

    private boolean supportsPlannerTaskGrouping(ToolInvocationView invocation) {
        return invocation != null && "planning".equals(invocation.getToolName());
    }
}
