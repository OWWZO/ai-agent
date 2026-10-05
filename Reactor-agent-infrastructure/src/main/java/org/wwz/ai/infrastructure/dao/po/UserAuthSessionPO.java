package org.wwz.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Persistence object for user_auth_session.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAuthSessionPO {

    private String sessionId;

    private String userId;

    private String refreshTokenHash;

    private String deviceName;

    private String ip;

    private String userAgent;

    private LocalDateTime expiresAt;

    private LocalDateTime lastSeenAt;

    private LocalDateTime revokedAt;

    private LocalDateTime createdAt;
}
