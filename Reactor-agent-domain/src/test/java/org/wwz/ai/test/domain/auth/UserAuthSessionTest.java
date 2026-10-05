package org.wwz.ai.test.domain.auth;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.auth.entity.UserAuthSession;

import java.time.LocalDateTime;

public class UserAuthSessionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Test
    public void expiryIsExclusiveAndSlidingExpiryNeverPassesAbsoluteDeadline() {
        UserAuthSession session = UserAuthSession.builder()
                .sessionId("session-1")
                .userId("user-1")
                .createdAt(NOW.minusDays(355))
                .expiresAt(NOW.plusDays(30))
                .build();

        Assert.assertTrue(session.isUsableAt(NOW.plusDays(9)));
        Assert.assertFalse(session.isUsableAt(NOW.plusDays(10)));
        Assert.assertEquals(NOW.plusDays(10), session.slideExpiryAt(NOW, 30));
    }
}
