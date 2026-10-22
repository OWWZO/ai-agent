package org.wwz.ai.infrastructure.adapter.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.auth.entity.UserAuthSession;
import org.wwz.ai.domain.auth.repository.IUserAuthSessionRepository;
import org.wwz.ai.infrastructure.dao.IUserAuthSessionDao;
import org.wwz.ai.infrastructure.dao.po.UserAuthSessionPO;

import java.time.LocalDateTime;

/**
 * MyBatis adapter for refresh-session persistence.
 */
@Repository
@RequiredArgsConstructor
public class UserAuthSessionRepository implements IUserAuthSessionRepository {

    private final IUserAuthSessionDao userAuthSessionDao;

    @Override
    public UserAuthSession findBySessionId(String sessionId) {
        UserAuthSessionPO session = userAuthSessionDao.queryBySessionId(sessionId);
        return session == null ? null : toDomain(session);
    }

    @Override
    public UserAuthSession findByRefreshTokenHash(String refreshTokenHash) {
        UserAuthSessionPO session = userAuthSessionDao.queryByRefreshTokenHash(refreshTokenHash);
        return session == null ? null : toDomain(session);
    }

    @Override
    public UserAuthSession save(UserAuthSession session) {
        UserAuthSessionPO po = toPO(session);
        userAuthSessionDao.insert(po);
        return session;
    }

    @Override
    public boolean rotate(String sessionId,
                          String currentRefreshTokenHash,
                          String nextRefreshTokenHash,
                          LocalDateTime lastSeenAt,
                          LocalDateTime expiresAt) {
        return userAuthSessionDao.rotate(
                sessionId,
                currentRefreshTokenHash,
                nextRefreshTokenHash,
                lastSeenAt,
                expiresAt) > 0;
    }

    @Override
    public boolean revokeByRefreshTokenHash(String refreshTokenHash, LocalDateTime revokedAt) {
        return userAuthSessionDao.revokeByRefreshTokenHash(refreshTokenHash, revokedAt) > 0;
    }

    @Override
    public int revokeByUserId(String userId, LocalDateTime revokedAt) {
        return userAuthSessionDao.revokeByUserId(userId, revokedAt);
    }

    @Override
    public int revokeByUserIdExcept(String userId, String retainedSessionId, LocalDateTime revokedAt) {
        return userAuthSessionDao.revokeByUserIdExcept(userId, retainedSessionId, revokedAt);
    }

    private UserAuthSession toDomain(UserAuthSessionPO po) {
        return UserAuthSession.builder()
                .sessionId(po.getSessionId())
                .userId(po.getUserId())
                .refreshTokenHash(po.getRefreshTokenHash())
                .deviceName(po.getDeviceName())
                .ip(po.getIp())
                .userAgent(po.getUserAgent())
                .expiresAt(po.getExpiresAt())
                .lastSeenAt(po.getLastSeenAt())
                .revokedAt(po.getRevokedAt())
                .createdAt(po.getCreatedAt())
                .build();
    }

    private UserAuthSessionPO toPO(UserAuthSession session) {
        return UserAuthSessionPO.builder()
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
                .refreshTokenHash(session.getRefreshTokenHash())
                .deviceName(session.getDeviceName())
                .ip(session.getIp())
                .userAgent(session.getUserAgent())
                .expiresAt(session.getExpiresAt())
                .lastSeenAt(session.getLastSeenAt())
                .revokedAt(session.getRevokedAt())
                .createdAt(session.getCreatedAt())
                .build();
    }
}
