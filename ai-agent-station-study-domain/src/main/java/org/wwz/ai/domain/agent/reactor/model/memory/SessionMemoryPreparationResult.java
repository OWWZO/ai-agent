package org.wwz.ai.domain.agent.reactor.model.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 请求前会话记忆准备结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMemoryPreparationResult {

    private SessionMemoryDecisionType decisionType;
    private SessionWorkingMemory workingMemory;
    private Long snapshotVersionId;
    private Integer estimatedTokens;
    private Integer postCompactionTokens;
    private Integer failureCount;
    private String reason;
    private String rejectReason;

    public boolean shouldReject() {
        return SessionMemoryDecisionType.REJECTED == decisionType;
    }
}
