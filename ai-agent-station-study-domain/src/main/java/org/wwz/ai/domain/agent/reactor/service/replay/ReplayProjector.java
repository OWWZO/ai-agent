package org.wwz.ai.domain.agent.reactor.service.replay;

import lombok.RequiredArgsConstructor;
import org.wwz.ai.domain.agent.reactor.model.ledger.ArtifactView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.reactor.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.reactor.model.replay.ReplayFactBundle;
import org.wwz.ai.domain.agent.reactor.service.replay.projector.ToolInvocationProjectorRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史回放共享投影入口。
 * 这里只负责遍历顺序与 artifact 归组，具体工具解析全部委托给 registry。
 */
@RequiredArgsConstructor
public class ReplayProjector {

    private final ToolInvocationProjectorRegistry toolInvocationProjectorRegistry;

    public List<ProjectedReplayEvent> projectHistory(ReplayFactBundle bundle) {
        if (bundle == null || bundle.getToolInvocations() == null || bundle.getToolInvocations().isEmpty()) {
            return List.of();
        }
        Map<Long, List<ArtifactView>> artifactsByInvocationId = groupArtifacts(bundle.getArtifacts());
        EventResult state = new EventResult();
        List<ProjectedReplayEvent> events = new ArrayList<>();
        for (ToolInvocationView invocation : bundle.getToolInvocations()) {
            if (invocation == null) {
                continue;
            }
            List<ArtifactView> artifacts = artifactsByInvocationId.getOrDefault(invocation.getId(), List.of());
            events.addAll(toolInvocationProjectorRegistry.project(invocation, artifacts, state));
        }
        return events;
    }

    private Map<Long, List<ArtifactView>> groupArtifacts(List<ArtifactView> artifacts) {
        Map<Long, List<ArtifactView>> result = new LinkedHashMap<>();
        if (artifacts == null) {
            return result;
        }
        for (ArtifactView artifact : artifacts) {
            if (artifact == null || artifact.getToolInvocationId() == null) {
                continue;
            }
            result.computeIfAbsent(artifact.getToolInvocationId(), key -> new ArrayList<>()).add(artifact);
        }
        return result;
    }
}
