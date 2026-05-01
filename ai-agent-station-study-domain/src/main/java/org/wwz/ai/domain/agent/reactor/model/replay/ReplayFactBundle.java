package org.wwz.ai.domain.agent.reactor.model.replay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.reactor.model.ledger.ArtifactView;
import org.wwz.ai.domain.agent.reactor.model.ledger.ToolInvocationView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史回放所需的最小事实集合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayFactBundle implements Serializable {
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private List<ToolInvocationView> toolInvocations = new ArrayList<>();

    @Builder.Default
    private List<ArtifactView> artifacts = new ArrayList<>();
}
