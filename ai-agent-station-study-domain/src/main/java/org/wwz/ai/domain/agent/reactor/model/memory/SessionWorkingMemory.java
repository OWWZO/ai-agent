package org.wwz.ai.domain.agent.reactor.model.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;

import java.util.ArrayList;
import java.util.List;

/**
 * 请求级工作记忆聚合
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionWorkingMemory {

    private Long conversationId;
    private String sessionId;
    private Integer agentType;
    private String summaryText;

    @Builder.Default
    private List<SessionMemoryFact> facts = new ArrayList<>();

    @Builder.Default
    private List<SessionTurnMemory> recentTurns = new ArrayList<>();

    @Builder.Default
    private List<FileInformation> restoredFiles = new ArrayList<>();

    private String historyDialogue;
    private Integer boundarySortOrder;
    private Integer estimatedTokens;
    private Boolean needsCompaction;
}
