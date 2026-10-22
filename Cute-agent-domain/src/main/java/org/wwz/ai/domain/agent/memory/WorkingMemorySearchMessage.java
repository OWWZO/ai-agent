package org.wwz.ai.domain.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Working-memory history search row. The row id is immutable because compaction
 * appends a new projection and invalidates the old one instead of updating it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkingMemorySearchMessage {

    private Long id;
    private String sessionId;
    private String memoryScope;
    private Long turnId;
    private String requestId;
    private String originMessageKey;
    private Long runId;
    private Integer turnSeq;
    private Integer seqNo;
    private String role;
    private String content;
    private String reasoningContent;
    private String toolCallId;
    private String toolCallsJson;
    private String messageKind;
    private Integer turnStatus;
    private Double searchScore;

    /** Stable row anchor for callers; originMessageKey is the cross-projection identity. */
    public String stableKey() {
        return String.valueOf(sessionId) + ":" + String.valueOf(requestId) + ":" +
                String.valueOf(seqNo);
    }
}
