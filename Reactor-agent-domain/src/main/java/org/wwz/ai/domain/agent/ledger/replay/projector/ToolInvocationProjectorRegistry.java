package org.wwz.ai.domain.agent.ledger.replay.projector;

import lombok.RequiredArgsConstructor;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;

import java.util.List;

/**
 * 工具调用历史回放投影器注册表。
 * <p>按 tool_name 选择专用 projector，并在没有匹配实现时使用兜底 projector。</p>
 */
@RequiredArgsConstructor
public class ToolInvocationProjectorRegistry {

    private final List<ToolInvocationProjector> projectors;
    private final ToolInvocationProjector defaultProjector;

    /**
     * 孤儿工具（无关联 LLM）：每个 invocation 单独开组，避免无序串组。
     */
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        return project(invocation, artifacts, state, false);
    }

    /**
     * 历史回放分组：
     * 1. reuseCurrentTaskGroup=true：与 live SSE 一致，复用当前 taskId（同 LLM 轮次工具批量共用）；
     * 2. reuseCurrentTaskGroup=false：独立开组（孤儿工具默认路径）。
     * planning 工具由 projector 自行 renew 计划步骤，不受此开关影响。
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
