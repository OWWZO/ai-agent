package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 创建 run 的命令对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogueRunStartRecord {

    private String runUid;

    private String requestId;

    private String sessionId;

    private String visitorId;

    private String entryAgent;

    private String queryText;

    private LocalDateTime startedAt;
}
