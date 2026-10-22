package org.wwz.ai.test.application.auth;

import com.auth0.jwt.JWT;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.auth.AuthApplicationService;
import org.wwz.ai.application.auth.JwtTokenService;
import org.wwz.ai.application.auth.PasswordPolicy;
import org.wwz.ai.application.auth.command.AuthChangePasswordCommand;
import org.wwz.ai.application.auth.command.AuthLoginCommand;
import org.wwz.ai.application.auth.command.AuthLogoutAllCommand;
import org.wwz.ai.application.auth.command.AuthLogoutCommand;
import org.wwz.ai.application.auth.command.AuthMeCommand;
import org.wwz.ai.application.auth.command.AuthRefreshCommand;
import org.wwz.ai.application.auth.command.AuthRegisterCommand;
import org.wwz.ai.application.auth.result.IssuedAuthToken;
import org.wwz.ai.domain.auth.entity.UserAccount;
import org.wwz.ai.domain.auth.entity.UserAuthSession;
import org.wwz.ai.domain.auth.repository.IUserAccountRepository;
import org.wwz.ai.domain.auth.repository.IUserAuthSessionRepository;
import org.wwz.ai.types.enums.ResponseCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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

        IssuedAuthToken login = service.login(
                new AuthLoginCommand(" Alice ", null, "secret"));

        Assert.assertEquals(ResponseCode.SUCCESS, login.result().code());
        Assert.assertNotNull(login.refreshToken());
        Assert.assertEquals("user-uuid-1", login.result().data().user().userId());
        Assert.assertEquals("user-uuid-1", JWT.decode(login.result().data().accessToken()).getSubject());
        String sessionId = JWT.decode(login.result().data().accessToken()).getClaim("sid").asString();
        Assert.assertNotNull(sessionId);
        UUID.fromString(sessionId);
        Assert.assertEquals("USER", JWT.decode(login.result().data().accessToken()).getClaim("role").asString());
        Assert.assertEquals(FIXED_INSTANT.plusSeconds(900),
                JWT.decode(login.result().data().accessToken()).getExpiresAt().toInstant());
        UserAuthSession session = sessions.findBySessionId(sessionId);
        Assert.assertNotEquals(login.refreshToken(), session.getRefreshTokenHash());
        Assert.assertEquals(64, session.getRefreshTokenHash().length());
        Assert.assertEquals(FIXED_TIME.plusDays(AuthApplicationService.REFRESH_ABSOLUTE_DAYS),
                session.getCreatedAt().plusDays(365));

        sessions.rejectNextRotation = true;
        IssuedAuthToken racedRefresh = service.refresh(new AuthRefreshCommand(login.refreshToken()));
        Assert.assertEquals(ResponseCode.LOGIN_FAILED, racedRefresh.result().code());
        Assert.assertEquals("Refresh token was already used", racedRefresh.result().info());

        IssuedAuthToken refresh = service.refresh(new AuthRefreshCommand(login.refreshToken()));
        Assert.assertEquals(ResponseCode.SUCCESS, refresh.result().code());
        Assert.assertNotEquals(login.refreshToken(), refresh.refreshToken());
        IssuedAuthToken reusedToken = service.refresh(new AuthRefreshCommand(login.refreshToken()));
        Assert.assertEquals(ResponseCode.LOGIN_FAILED, reusedToken.result().code());
        Assert.assertEquals("Invalid or expired refresh token", reusedToken.result().info());
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

        IssuedAuthToken disabledLogin = service.login(new AuthLoginCommand(null, "disabled", "secret"));
        Assert.assertEquals(ResponseCode.LOGIN_FAILED, disabledLogin.result().code());
        Assert.assertEquals("Invalid credentials", disabledLogin.result().info());

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
        Assert.assertEquals(ResponseCode.LOGIN_FAILED,
                service.login(new AuthLoginCommand("deleted", null, "secret")).result().code());
    }

    @Test
    public void registrationMapsDuplicateAndPasswordPolicyFailures() {
        PasswordPolicy passwordPolicy = new PasswordPolicy();
        AuthApplicationService service = newService(
                new InMemoryAccounts(),
                new InMemorySessions(),
                passwordPolicy,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));

        IssuedAuthToken registered = service.register(new AuthRegisterCommand(" Alice ", "secret", " Alice "));
        Assert.assertEquals(ResponseCode.SUCCESS, registered.result().code());
        Assert.assertEquals("alice", registered.result().data().user().loginName());
        Assert.assertNotNull(registered.refreshToken());

        IssuedAuthToken duplicate = service.register(new AuthRegisterCommand("alice", "secret", "Alice"));
        Assert.assertEquals(ResponseCode.ILLEGAL_PARAMETER, duplicate.result().code());
        Assert.assertEquals("Login name already exists", duplicate.result().info());
        Assert.assertNull(duplicate.refreshToken());

        IssuedAuthToken raceDuplicate = newService(
                new InMemoryAccounts(true),
                new InMemorySessions(),
                passwordPolicy,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC))
                .register(new AuthRegisterCommand("bob", "secret", "Bob"));
        Assert.assertEquals(ResponseCode.ILLEGAL_PARAMETER, raceDuplicate.result().code());
        Assert.assertEquals("Login name already exists", raceDuplicate.result().info());

        IssuedAuthToken invalidPassword = service.register(
                new AuthRegisterCommand("bob", "x".repeat(73), "Bob"));
        Assert.assertEquals(ResponseCode.ILLEGAL_PARAMETER, invalidPassword.result().code());
        Assert.assertEquals("Password exceeds BCrypt's 72-byte limit", invalidPassword.result().info());
    }

    @Test
    public void mePasswordChangeAndLogoutAllKeepOnlyTheCurrentSession() {
        PasswordPolicy passwordPolicy = new PasswordPolicy();
        UserAccount account = UserAccount.builder()
                .userId("user-2")
                .loginName("alice")
                .passwordHash(passwordPolicy.encode("old-secret"))
                .nickname("Alice")
                .role("USER")
                .status(UserAccount.STATUS_ACTIVE)
                .deleted(0)
                .build();
        UserAuthSession current = session("current-session", "user-2");
        UserAuthSession other = session("other-session", "user-2");
        InMemorySessions sessions = new InMemorySessions(current, other);
        AuthApplicationService service = newService(
                new InMemoryAccounts(account),
                sessions,
                passwordPolicy,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));

        Assert.assertEquals(ResponseCode.SUCCESS, service.me(new AuthMeCommand("user-2")).code());
        Assert.assertEquals("alice", service.me(new AuthMeCommand("user-2")).data().loginName());

        Assert.assertEquals(ResponseCode.SUCCESS, service.changePassword(new AuthChangePasswordCommand(
                "user-2", "current-session", "old-secret", "new-secret")).code());
        Assert.assertTrue(passwordPolicy.matches("new-secret", account.getPasswordHash()));
        Assert.assertNull(current.getRevokedAt());
        Assert.assertEquals(FIXED_TIME, other.getRevokedAt());

        Assert.assertEquals(ResponseCode.SUCCESS, service.logoutAll(new AuthLogoutAllCommand("user-2")).code());
        Assert.assertEquals(FIXED_TIME, current.getRevokedAt());
        Assert.assertEquals(ResponseCode.LOGIN_FAILED, service.me(new AuthMeCommand("missing-user")).code());
    }

    @Test
    public void logoutRevokesTheSessionMatchingTheRefreshToken() throws NoSuchAlgorithmException {
        String refreshToken = "refresh-token-to-revoke";
        String refreshTokenHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        UserAuthSession session = UserAuthSession.builder()
                .sessionId("session-to-revoke")
                .userId("user-3")
                .refreshTokenHash(refreshTokenHash)
                .createdAt(FIXED_TIME)
                .expiresAt(FIXED_TIME.plusDays(30))
                .build();
        AuthApplicationService service = newService(
                new InMemoryAccounts(),
                new InMemorySessions(session),
                new PasswordPolicy(),
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));

        Assert.assertEquals(ResponseCode.SUCCESS,
                service.logout(new AuthLogoutCommand(refreshToken)).code());
        Assert.assertEquals(FIXED_TIME, session.getRevokedAt());
        Assert.assertEquals(ResponseCode.LOGIN_FAILED,
                service.refresh(new AuthRefreshCommand(refreshToken)).result().code());
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
        private final boolean duplicateOnSave;

        private InMemoryAccounts(UserAccount... accounts) {
            this(false, accounts);
        }

        private InMemoryAccounts(boolean duplicateOnSave, UserAccount... accounts) {
            this.duplicateOnSave = duplicateOnSave;
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
            if (duplicateOnSave) {
                throw new org.wwz.ai.domain.auth.exception.LoginNameAlreadyExistsException();
            }
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

        private final List<UserAuthSession> sessions = new ArrayList<>();
        private boolean rejectNextRotation;

        private InMemorySessions() {
        }

        private InMemorySessions(UserAuthSession... sessions) {
            this.sessions.addAll(List.of(sessions));
        }

        @Override
        public UserAuthSession findBySessionId(String sessionId) {
            return sessions.stream()
                    .filter(value -> Objects.equals(value.getSessionId(), sessionId))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public UserAuthSession findByRefreshTokenHash(String refreshTokenHash) {
            return sessions.stream()
                    .filter(value -> Objects.equals(value.getRefreshTokenHash(), refreshTokenHash))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public UserAuthSession save(UserAuthSession value) {
            sessions.add(value);
            return value;
        }

        @Override
        public boolean rotate(String sessionId,
                              String currentRefreshTokenHash,
                              String nextRefreshTokenHash,
                              LocalDateTime lastSeenAt,
                              LocalDateTime expiresAt) {
            if (rejectNextRotation) {
                rejectNextRotation = false;
                return false;
            }
            UserAuthSession session = findBySessionId(sessionId);
            if (session == null
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
            UserAuthSession session = findByRefreshTokenHash(refreshTokenHash);
            if (session == null || session.getRevokedAt() != null) {
                return false;
            }
            session.setRevokedAt(revokedAt);
            return true;
        }

        @Override
        public int revokeByUserId(String userId, LocalDateTime revokedAt) {
            return sessions.stream()
                    .filter(session -> Objects.equals(session.getUserId(), userId) && session.getRevokedAt() == null)
                    .map(session -> {
                        session.setRevokedAt(revokedAt);
                        return session;
                    })
                    .mapToInt(ignored -> 1)
                    .sum();
        }

        @Override
        public int revokeByUserIdExcept(String userId, String retainedSessionId, LocalDateTime revokedAt) {
            return sessions.stream()
                    .filter(session -> Objects.equals(session.getUserId(), userId)
                            && !Objects.equals(session.getSessionId(), retainedSessionId)
                            && session.getRevokedAt() == null)
                    .map(session -> {
                        session.setRevokedAt(revokedAt);
                        return session;
                    })
                    .mapToInt(ignored -> 1)
                    .sum();
        }
    }

    private static UserAuthSession session(String sessionId, String userId) {
        return UserAuthSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .createdAt(FIXED_TIME)
                .expiresAt(FIXED_TIME.plusDays(30))
                .build();
    }
}
