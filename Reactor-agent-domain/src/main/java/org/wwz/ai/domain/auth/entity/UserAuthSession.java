package org.wwz.ai.domain.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Server-side refresh session. The raw refresh token is never part of this model.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAuthSession {

    public static final long ABSOLUTE_LIFETIME_DAYS = 365;

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

    public boolean isUsableAt(LocalDateTime now) {
        return revokedAt == null
                && createdAt != null
                && expiresAt != null
                && now.isBefore(expiresAt)
                && now.isBefore(absoluteExpiresAt());
    }

    public LocalDateTime slideExpiryAt(LocalDateTime now, long slidingDays) {
        LocalDateTime slidingExpiry = now.plusDays(slidingDays);
        LocalDateTime absoluteExpiry = absoluteExpiresAt();
        if (slidingExpiry.isBefore(absoluteExpiry)) {
            return slidingExpiry;
        }
        return absoluteExpiry;
    }

    private LocalDateTime absoluteExpiresAt() {
        return createdAt.plusDays(ABSOLUTE_LIFETIME_DAYS);
    }

    public boolean belongsTo(String userId) {
        return this.userId != null && this.userId.equals(userId);
    }
}
