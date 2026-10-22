package org.wwz.ai.domain.auth.repository;

import org.wwz.ai.domain.auth.entity.UserAuthSession;

import java.time.LocalDateTime;

/**
 * Refresh-session persistence port.
 */
public interface IUserAuthSessionRepository {

    UserAuthSession findBySessionId(String sessionId);

    UserAuthSession findByRefreshTokenHash(String refreshTokenHash);

    UserAuthSession save(UserAuthSession session);

    /**
     * Atomically rotates a refresh token so a token cannot be replayed after use.
     */
    boolean rotate(String sessionId,
                   String currentRefreshTokenHash,
                   String nextRefreshTokenHash,
                   LocalDateTime lastSeenAt,
                   LocalDateTime expiresAt);

    boolean revokeByRefreshTokenHash(String refreshTokenHash, LocalDateTime revokedAt);

    int revokeByUserId(String userId, LocalDateTime revokedAt);

    int revokeByUserIdExcept(String userId, String retainedSessionId, LocalDateTime revokedAt);
}
