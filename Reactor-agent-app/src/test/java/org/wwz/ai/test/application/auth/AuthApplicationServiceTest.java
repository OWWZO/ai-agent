package org.wwz.ai.test.application.auth;

import com.auth0.jwt.JWT;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.api.dto.AuthLoginRequestDTO;
import org.wwz.ai.application.auth.AuthApplicationService;
import org.wwz.ai.application.auth.JwtTokenService;
import org.wwz.ai.application.auth.PasswordPolicy;
import org.wwz.ai.domain.auth.entity.UserAccount;
import org.wwz.ai.domain.auth.entity.UserAuthSession;
import org.wwz.ai.domain.auth.repository.IUserAccountRepository;
import org.wwz.ai.domain.auth.repository.IUserAuthSessionRepository;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

public class AuthApplicationServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Test
    public void loginIssuesBoundJwtAndRefreshRotationRejectsReplay() {
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        PasswordPolicy passwordPolicy = new PasswordPolicy();
        UserAccount account = UserAccount.builder()
                .id(11L)
                .userId("user-uuid-1")
                .loginName("alice")
                .passwordHash(passwordPolicy.encode("secret"))
                .role("USER")
                .status(UserAccount.STATUS_ACTIVE)
                .deleted(0)
                .build();
        InMemoryAccounts accounts = new InMemoryAccounts(account);
        InMemorySessions sessions = new InMemorySessions();
        AuthApplicationService service = newService(accounts, sessions, passwordPolicy, clock);

        AuthApplicationService.IssuedAuthToken login = service.loginWithRefreshToken(
                AuthLoginRequestDTO.builder().loginName(" Alice ").password("secret").build());

        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), login.response().getCode());
        Assert.assertNotNull(login.refreshToken());
        Assert.assertEquals("user-uuid-1", login.response().getData().getUser().getUserId());
        Assert.assertEquals("user-uuid-1", JWT.decode(login.response().getData().getAccessToken()).getSubject());
        String sessionId = JWT.decode(login.response().getData().getAccessToken()).getClaim("sid").asString();
        Assert.assertNotNull(sessionId);
        UUID.fromString(sessionId);
        Assert.assertEquals("USER", JWT.decode(login.response().getData().getAccessToken()).getClaim("role").asString());
        Assert.assertEquals(FIXED_INSTANT.plusSeconds(900),
                JWT.decode(login.response().getData().getAccessToken()).getExpiresAt().toInstant());
        Assert.assertNotEquals(login.refreshToken(), sessions.session.getRefreshTokenHash());
        Assert.assertEquals(64, sessions.session.getRefreshTokenHash().length());
        Assert.assertEquals(FIXED_TIME.plusDays(AuthApplicationService.REFRESH_ABSOLUTE_DAYS),
                sessions.session.getCreatedAt().plusDays(365));

        AuthApplicationService.IssuedAuthToken refresh = service.refreshWithRefreshToken(login.refreshToken());
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), refresh.response().getCode());
        Assert.assertNotEquals(login.refreshToken(), refresh.refreshToken());
        Assert.assertEquals(ResponseCode.LOGIN_FAILED.getCode(),
                service.refreshWithRefreshToken(login.refreshToken()).response().getCode());
    }

    @Test
    public void disabledOrDeletedAccountCannotLogin() {
        PasswordPolicy passwordPolicy = new PasswordPolicy();
        UserAccount disabled = UserAccount.builder()
                .id(1L)
                .userId("disabled-user")
                .loginName("disabled")
                .passwordHash(passwordPolicy.encode("secret"))
                .role("USER")
                .status(UserAccount.STATUS_DISABLED)
                .deleted(0)
                .build();
        AuthApplicationService service = newService(
                new InMemoryAccounts(disabled),
                new InMemorySessions(),
                passwordPolicy,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));

        Assert.assertEquals(ResponseCode.LOGIN_FAILED.getCode(),
                service.login(AuthLoginRequestDTO.builder().account("disabled").password("secret").build()).getCode());

        UserAccount deleted = UserAccount.builder()
                .id(2L)
                .userId("deleted-user")
                .loginName("deleted")
                .passwordHash(passwordPolicy.encode("secret"))
                .role("USER")
                .status(UserAccount.STATUS_ACTIVE)
                .deleted(1)
                .build();
        service = newService(
                new InMemoryAccounts(deleted),
                new InMemorySessions(),
                passwordPolicy,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
        Assert.assertEquals(ResponseCode.LOGIN_FAILED.getCode(),
                service.login(AuthLoginRequestDTO.builder().loginName("deleted").password("secret").build()).getCode());
    }

    @Test
    public void accessTokenRequiresSessionForSameUserId() {
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        PasswordPolicy passwordPolicy = new PasswordPolicy();
        UserAccount account = UserAccount.builder()
                .userId("account-user")
                .loginName("alice")
                .passwordHash(passwordPolicy.encode("secret"))
                .role("USER")
                .status(UserAccount.STATUS_ACTIVE)
                .deleted(0)
                .build();
        InMemorySessions sessions = new InMemorySessions(UserAuthSession.builder()
                .sessionId("session-1")
                .userId("different-user")
                .expiresAt(FIXED_TIME.plusDays(30))
                .createdAt(FIXED_TIME)
                .build());
        JwtTokenService jwtTokenService = new JwtTokenService(
                "test-secret-test-secret-test-secret", "reactor-test", Duration.ofMinutes(15), clock);
        String accessToken = jwtTokenService.issue(account, "session-1").value();

        AuthApplicationService service = new AuthApplicationService(
                new InMemoryAccounts(account),
                sessions,
                passwordPolicy,
                jwtTokenService,
                clock);

        Assert.assertNull(service.verifyAccessToken(accessToken));
    }

    private AuthApplicationService newService(InMemoryAccounts accounts,
                                              InMemorySessions sessions,
                                              PasswordPolicy passwordPolicy,
                                              Clock clock) {
        return new AuthApplicationService(
                accounts,
                sessions,
                passwordPolicy,
                new JwtTokenService("test-secret-test-secret-test-secret", "reactor-test", Duration.ofMinutes(15), clock),
                clock);
    }

    private static final class InMemoryAccounts implements IUserAccountRepository {

        private UserAccount account;

        private InMemoryAccounts(UserAccount... accounts) {
            if (accounts.length > 0) {
                this.account = accounts[0];
            }
        }

        @Override
        public UserAccount findById(Long id) {
            return account != null && Objects.equals(account.getId(), id) && isVisible() ? account : null;
        }

        @Override
        public UserAccount findByUserId(String userId) {
            return account != null && Objects.equals(account.getUserId(), userId) && isVisible() ? account : null;
        }

        @Override
        public UserAccount findByLoginName(String loginName) {
            return account != null && Objects.equals(account.getLoginName(), loginName) && isVisible() ? account : null;
        }

        @Override
        public UserAccount save(UserAccount value) {
            if (value.getId() == null) {
                value.setId(11L);
            }
            account = value;
            return value;
        }

        @Override
        public boolean updatePassword(String userId, String passwordHash, LocalDateTime updatedAt) {
            if (findByUserId(userId) == null) {
                return false;
            }
            account.setPasswordHash(passwordHash);
            account.setUpdateTime(updatedAt);
            return true;
        }

        @Override
        public boolean updateLastLoginAt(String userId, LocalDateTime lastLoginAt) {
            if (findByUserId(userId) == null) {
                return false;
            }
            account.setLastLoginAt(lastLoginAt);
            return true;
        }

        private boolean isVisible() {
            return account != null && Integer.valueOf(0).equals(account.getDeleted());
        }
    }

    private static final class InMemorySessions implements IUserAuthSessionRepository {

        private UserAuthSession session;

        private InMemorySessions() {
        }

        private InMemorySessions(UserAuthSession session) {
            this.session = session;
        }

        @Override
        public UserAuthSession findBySessionId(String sessionId) {
            return session != null && Objects.equals(session.getSessionId(), sessionId) ? session : null;
        }

        @Override
        public UserAuthSession findByRefreshTokenHash(String refreshTokenHash) {
            return session != null && Objects.equals(session.getRefreshTokenHash(), refreshTokenHash) ? session : null;
        }

        @Override
        public UserAuthSession save(UserAuthSession value) {
            session = value;
            return value;
        }

        @Override
        public boolean rotate(String sessionId,
                              String currentRefreshTokenHash,
                              String nextRefreshTokenHash,
                              LocalDateTime lastSeenAt,
                              LocalDateTime expiresAt) {
            if (findBySessionId(sessionId) == null
                    || !Objects.equals(session.getRefreshTokenHash(), currentRefreshTokenHash)
                    || !session.isUsableAt(lastSeenAt)) {
                return false;
            }
            session.setRefreshTokenHash(nextRefreshTokenHash);
            session.setLastSeenAt(lastSeenAt);
            session.setExpiresAt(expiresAt);
            return true;
        }

        @Override
        public boolean revokeByRefreshTokenHash(String refreshTokenHash, LocalDateTime revokedAt) {
            if (findByRefreshTokenHash(refreshTokenHash) == null || session.getRevokedAt() != null) {
                return false;
            }
            session.setRevokedAt(revokedAt);
            return true;
        }

        @Override
        public int revokeByUserId(String userId, LocalDateTime revokedAt) {
            if (session != null && Objects.equals(session.getUserId(), userId) && session.getRevokedAt() == null) {
                session.setRevokedAt(revokedAt);
                return 1;
            }
            return 0;
        }

        @Override
        public int revokeByUserIdExcept(String userId, String retainedSessionId, LocalDateTime revokedAt) {
            if (session != null
                    && Objects.equals(session.getUserId(), userId)
                    && !Objects.equals(session.getSessionId(), retainedSessionId)
                    && session.getRevokedAt() == null) {
                session.setRevokedAt(revokedAt);
                return 1;
            }
            return 0;
        }
    }
}
