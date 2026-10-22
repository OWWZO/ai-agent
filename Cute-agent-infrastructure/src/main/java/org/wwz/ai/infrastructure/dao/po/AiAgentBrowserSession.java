package org.wwz.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent Browser session mapping table PO.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiAgentBrowserSession {

    private Long id;

    private String ownerKey;

    private String agentSessionId;

    private String agentBrowserName;

    private LocalDateTime lastUsedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
