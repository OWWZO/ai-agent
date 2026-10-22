package org.wwz.ai.application.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.wwz.ai.application.auth.command.AuthChangePasswordCommand;
import org.wwz.ai.application.auth.command.AuthLoginCommand;
import org.wwz.ai.application.auth.command.AuthLogoutAllCommand;
import org.wwz.ai.application.auth.command.AuthLogoutCommand;
import org.wwz.ai.application.auth.command.AuthMeCommand;
import org.wwz.ai.application.auth.command.AuthRefreshCommand;
import org.wwz.ai.application.auth.command.AuthRegisterCommand;
import org.wwz.ai.application.auth.result.AuthAccountResult;
import org.wwz.ai.application.auth.result.AuthResult;
import org.wwz.ai.application.auth.result.AuthTokenResult;
import org.wwz.ai.application.auth.result.IssuedAuthToken;
import org.wwz.ai.domain.auth.entity.UserAccount;
import org.wwz.ai.domain.auth.entity.UserAuthSession;
import org.wwz.ai.domain.auth.exception.LoginNameAlreadyExistsException;
import org.wwz.ai.domain.auth.repository.IUserAccountRepository;
import org.wwz.ai.domain.auth.repository.IUserAuthSessionRepository;
import org.wwz.ai.types.enums.ResponseCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Account registration, login and refresh-session orchestration.
 */
@Service
@RequiredArgsConstructor
public class AuthApplicationService implements IAuthApplicationService {

    public static final String DEFAULT_ROLE = "USER";
    public static final String ADMIN_ROLE = "ADMIN";
    public static final long REFRESH_SLIDING_DAYS = IAuthApplicationService.REFRESH_SLIDING_DAYS;
    public static final long REFRESH_ABSOLUTE_DAYS = UserAuthSession.ABSOLUTE_LIFETIME_DAYS;

    private static final String TOKEN_TYPE = "Bearer";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final IUserAccountRepository accountRepository;
    private final IUserAuthSessionRepository authSessionRepository;
    private final PasswordPolicy passwordPolicy;
    private final JwtTokenService jwtTokenService;
    private final Clock clock;

    @Override
    @Transactional
    public IssuedAuthToken register(AuthRegisterCommand command) {
        if (command == null) {
            return tokenFailure(ResponseCode.ILLEGAL_PARAMETER, "Registration request is required");
        }

        String loginName = normalizeLoginName(command.loginName());
        String nickname = normalize(command.nickname());
        if (loginName == null || command.password() == null || command.password().isBlank()
                || nickname == null) {
            return tokenFailure(ResponseCode.ILLEGAL_PARAMETER, "Login name, password and nickname are required");
        }
        if (accountRepository.findByLoginName(loginName) != null) {
            return tokenFailure(ResponseCode.ILLEGAL_PARAMETER, "Login name already exists");
        }

        String passwordHash;
        try {
            passwordHash = passwordPolicy.encode(command.password());
        } catch (IllegalArgumentException e) {
            return tokenFailure(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }

        LocalDateTime registeredAt = now();
        UserAccount account = UserAccount.builder()
                .userId(UUID.randomUUID().toString())
                .tokenDigest(newIdentityDigest())
                .status(UserAccount.STATUS_ACTIVE)
                .firstSeenAt(registeredAt)
                .lastSeenAt(registeredAt)
                .deleted(0)
                .loginName(loginName)
                .passwordHash(passwordHash)
                .nickname(nickname)
                .role(DEFAULT_ROLE)
                .createTime(registeredAt)
                .updateTime(registeredAt)
                .build();
        try {
            UserAccount saved = accountRepository.save(account);
            return issueTokenPair(saved);
        } catch (LoginNameAlreadyExistsException e) {
            return tokenFailure(ResponseCode.ILLEGAL_PARAMETER, "Login name already exists");
        }
    }

    @Override
    @Transactional
    public IssuedAuthToken login(AuthLoginCommand command) {
        if (command == null) {
            return tokenFailure(ResponseCode.LOGIN_FAILED, "Invalid credentials");
        }

        String loginName = normalizeLoginName(
                command.loginName() == null ? command.account() : command.loginName()
        );
        UserAccount account = loginName == null ? null : accountRepository.findByLoginName(loginName);
        if (account == null
                || !account.isActive()
                || !passwordPolicy.matches(command.password(), account.getPasswordHash())) {
            return tokenFailure(ResponseCode.LOGIN_FAILED, "Invalid credentials");
        }

        LocalDateTime loginAt = now();
        accountRepository.updateLastLoginAt(account.getUserId(), loginAt);
        account.setLastLoginAt(loginAt);
        return issueTokenPair(account);
    }

    @Override
    @Transactional
    public IssuedAuthToken refresh(AuthRefreshCommand command) {
        String refreshToken = command == null ? null : command.refreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            return tokenFailure(ResponseCode.LOGIN_FAILED, "Invalid refresh token");
        }

        String currentTokenHash = hashRefreshToken(refreshToken);
        UserAuthSession session = authSessionRepository.findByRefreshTokenHash(currentTokenHash);
        LocalDateTime refreshedAt = now();
        if (session == null || !session.isUsableAt(refreshedAt)) {
            return tokenFailure(ResponseCode.LOGIN_FAILED, "Invalid or expired refresh token");
        }

        UserAccount account = accountRepository.findByUserId(session.getUserId());
        if (account == null || !account.isActive()) {
            return tokenFailure(ResponseCode.LOGIN_FAILED, "Invalid or expired refresh token");
        }

        String nextRefreshToken = newRefreshToken();
        LocalDateTime nextExpiry = session.slideExpiryAt(refreshedAt, REFRESH_SLIDING_DAYS);
        if (!authSessionRepository.rotate(
                session.getSessionId(),
                currentTokenHash,
                hashRefreshToken(nextRefreshToken),
                refreshedAt,
                nextExpiry)) {
            return tokenFailure(ResponseCode.LOGIN_FAILED, "Refresh token was already used");
        }

        JwtTokenService.IssuedAccessToken accessToken = jwtTokenService.issue(account, session.getSessionId());
        return success(new AuthTokenResult(
                TOKEN_TYPE,
                accessToken.value(),
                accessToken.expiresIn(),
                toAccountResult(account)), nextRefreshToken);
    }

    @Override
    @Transactional
    public AuthResult<Boolean> logout(AuthLogoutCommand command) {
        String refreshToken = command == null ? null : command.refreshToken();
        if (refreshToken != null && !refreshToken.isBlank()) {
            authSessionRepository.revokeByRefreshTokenHash(hashRefreshToken(refreshToken), now());
        }
        return success(true);
    }

    @Override
    @Transactional
    public AuthResult<Boolean> logoutAll(AuthLogoutAllCommand command) {
        UserAccount account = enabledAccount(command == null ? null : command.userId());
        if (account == null) {
            return failure(ResponseCode.LOGIN_FAILED, "Authentication is required");
        }
        authSessionRepository.revokeByUserId(account.getUserId(), now());
        return success(true);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResult<AuthAccountResult> me(AuthMeCommand command) {
        UserAccount account = enabledAccount(command == null ? null : command.userId());
        if (account == null) {
            return failure(ResponseCode.LOGIN_FAILED, "Authentication is required");
        }
        return success(toAccountResult(account));
    }

    @Override
    @Transactional
    public AuthResult<Boolean> changePassword(AuthChangePasswordCommand command) {
        UserAccount account = enabledAccount(command == null ? null : command.userId());
        if (account == null || command == null
                || !passwordPolicy.matches(command.oldPassword(), account.getPasswordHash())) {
            return failure(ResponseCode.LOGIN_FAILED, "Current password is incorrect");
        }

        String newPasswordHash;
        try {
            newPasswordHash = passwordPolicy.encode(command.newPassword());
        } catch (IllegalArgumentException e) {
            return failure(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
        if (!accountRepository.updatePassword(account.getUserId(), newPasswordHash, now())) {
            return failure(ResponseCode.UN_ERROR, "Password update failed");
        }

        String retainedSessionId = normalize(command.currentSessionId());
        if (retainedSessionId != null) {
            UserAuthSession currentSession = authSessionRepository.findBySessionId(retainedSessionId);
            if (currentSession == null || !currentSession.belongsTo(account.getUserId())) {
                retainedSessionId = null;
            }
        }
        if (retainedSessionId == null) {
            authSessionRepository.revokeByUserId(account.getUserId(), now());
        } else {
            authSessionRepository.revokeByUserIdExcept(account.getUserId(), retainedSessionId, now());
        }
        return success(true);
    }

    /**
     * Verifies the JWT and its server-side session/account state for a filter.
     */
    @Transactional(readOnly = true)
    public JwtTokenService.AuthenticatedAccount verifyAccessToken(String accessToken) {
        JwtTokenService.AuthenticatedAccount authenticated = jwtTokenService.verify(accessToken);
        if (authenticated == null) {
            return null;
        }
        UserAccount account = accountRepository.findByUserId(authenticated.userId());
        UserAuthSession session = authSessionRepository.findBySessionId(authenticated.sessionId());
        if (account == null || !account.isActive()
                || session == null
                || !session.belongsTo(account.getUserId())
                || !session.isUsableAt(now())) {
            return null;
        }
        return authenticated;
    }

    private IssuedAuthToken issueTokenPair(UserAccount account) {
        LocalDateTime createdAt = now();
        String refreshToken = newRefreshToken();
        UserAuthSession session = authSessionRepository.save(UserAuthSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .userId(account.getUserId())
                .refreshTokenHash(hashRefreshToken(refreshToken))
                .deviceName(null)
                .ip(null)
                .userAgent(null)
                .expiresAt(createdAt.plusDays(REFRESH_SLIDING_DAYS))
                .lastSeenAt(createdAt)
                .createdAt(createdAt)
                .build());

        JwtTokenService.IssuedAccessToken accessToken = jwtTokenService.issue(account, session.getSessionId());
        return success(new AuthTokenResult(
                TOKEN_TYPE,
                accessToken.value(),
                accessToken.expiresIn(),
                toAccountResult(account)), refreshToken);
    }

    private UserAccount enabledAccount(String userId) {
        String normalizedUserId = normalize(userId);
        if (normalizedUserId == null) {
            return null;
        }
        UserAccount account = accountRepository.findByUserId(normalizedUserId);
        return account != null && account.isActive() ? account : null;
    }

    private AuthAccountResult toAccountResult(UserAccount account) {
        return new AuthAccountResult(
                account.getUserId(),
                account.getLoginName(),
                account.getNickname(),
                account.getRole());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashRefreshToken(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String normalizeLoginName(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String newIdentityDigest() {
        return hashRefreshToken(UUID.randomUUID().toString());
    }

    private IssuedAuthToken tokenFailure(ResponseCode code, String info) {
        return new IssuedAuthToken(failure(code, info), null);
    }

    private <T> AuthResult<T> success(T data) {
        return AuthResult.success(data);
    }

    private IssuedAuthToken success(AuthTokenResult data, String refreshToken) {
        return new IssuedAuthToken(success(data), refreshToken);
    }

    private <T> AuthResult<T> failure(ResponseCode code, String info) {
        return AuthResult.failure(code, info);
    }
}
