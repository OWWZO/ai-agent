package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A page of recent conversation sessions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSessionPage {

    private List<DialogueSessionView> sessions;

    private String nextCursor;

    private boolean hasMore;
}
