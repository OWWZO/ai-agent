package org.wwz.ai.application.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.wwz.ai.api.IAuthService;
import org.wwz.ai.api.dto.AuthAccountResponseDTO;
import org.wwz.ai.api.dto.AuthChangePasswordRequestDTO;
import org.wwz.ai.api.dto.AuthLoginRequestDTO;
import org.wwz.ai.api.dto.AuthRegisterRequestDTO;
import org.wwz.ai.api.dto.AuthTokenResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.domain.auth.entity.UserAccount;
import org.wwz.ai.domain.auth.entity.UserAuthSession;
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
public class AuthApplicationService implements IAuthService {

    public static final String DEFAULT_ROLE = "USER";
    public static final String ADMIN_ROLE = "ADMIN";
    public static final long REFRESH_SLIDING_DAYS = 30;
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
    public Response<AuthTokenResponseDTO> register(AuthRegisterRequestDTO request) {
        return registerWithRefreshToken(request).response();
    }

    /**
     * HTTP adapters use this result to put the raw refresh token in a cookie.
     * It is intentionally separate from the JSON DTO.
     */
    @Transactional
    public IssuedAuthToken registerWithRefreshToken(AuthRegisterRequestDTO request) {
        if (request == null) {
            return tokenFailure(ResponseCode.ILLEGAL_PARAMETER, "Registration request is required");
        }

        String loginName = normalizeLoginName(request.getLoginName());
        String nickname = normalize(request.getNickname());
        if (loginName == null || request.getPassword() == null || request.getPassword().isBlank()
                || nickname == null) {
            return tokenFailure(ResponseCode.ILLEGAL_PARAMETER, "Login name, password and nickname are required");
        }
        if (accountRepository.findByLoginName(loginName) != null) {
            return tokenFailure(ResponseCode.ILLEGAL_PARAMETER, "Login name already exists");
        }

        String passwordHash;
        try {
            passwordHash = passwordPolicy.encode(request.getPassword());
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
        } catch (DataIntegrityViolationException e) {
            return tokenFailure(ResponseCode.ILLEGAL_PARAMETER, "Login name already exists");
        }
    }

    @Override
    @Transactional
    public Response<AuthTokenResponseDTO> login(AuthLoginRequestDTO request) {
        return loginWithRefreshToken(request).response();
    }

    @Transactional
    public IssuedAuthToken loginWithRefreshToken(AuthLoginRequestDTO request) {
        if (request == null) {
            return tokenFailure(ResponseCode.LOGIN_FAILED, "Invalid credentials");
        }

        String loginName = normalizeLoginName(
                request.getLoginName() == null ? request.getAccount() : request.getLoginName()
        );
        UserAccount account = loginName == null ? null : accountRepository.findByLoginName(loginName);
        if (account == null
                || !account.isActive()
                || !passwordPolicy.matches(request.getPassword(), account.getPasswordHash())) {
            return tokenFailure(ResponseCode.LOGIN_FAILED, "Invalid credentials");
        }

        LocalDateTime loginAt = now();
        accountRepository.updateLastLoginAt(account.getUserId(), loginAt);
        account.setLastLoginAt(loginAt);
        return issueTokenPair(account);
    }

    @Override
    @Transactional
    public Response<AuthTokenResponseDTO> refresh(String refreshToken) {
        return refreshWithRefreshToken(refreshToken).response();
    }

    @Transactional
    public IssuedAuthToken refreshWithRefreshToken(String refreshToken) {
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
        return success(AuthTokenResponseDTO.builder()
                .tokenType(TOKEN_TYPE)
                .accessToken(accessToken.value())
                .expiresIn(accessToken.expiresIn())
                .user(toAccountResponse(account))
                .build(), nextRefreshToken);
    }

    @Override
    @Transactional
    public Response<Boolean> logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authSessionRepository.revokeByRefreshTokenHash(hashRefreshToken(refreshToken), now());
        }
        return success(true);
    }

    @Override
    @Transactional
    public Response<Boolean> logoutAll(String userId) {
        UserAccount account = enabledAccount(userId);
        if (account == null) {
            return failure(ResponseCode.LOGIN_FAILED, "Authentication is required");
        }
        authSessionRepository.revokeByUserId(account.getUserId(), now());
        return success(true);
    }

    @Override
    @Transactional(readOnly = true)
    public Response<AuthAccountResponseDTO> me(String userId) {
        UserAccount account = enabledAccount(userId);
        if (account == null) {
            return failure(ResponseCode.LOGIN_FAILED, "Authentication is required");
        }
        return success(toAccountResponse(account));
    }

    @Override
    @Transactional
    public Response<Boolean> changePassword(String userId,
                                             String currentSessionId,
                                             AuthChangePasswordRequestDTO request) {
        UserAccount account = enabledAccount(userId);
        if (account == null || request == null
                || !passwordPolicy.matches(request.getOldPassword(), account.getPasswordHash())) {
            return failure(ResponseCode.LOGIN_FAILED, "Current password is incorrect");
        }

        String newPasswordHash;
        try {
            newPasswordHash = passwordPolicy.encode(request.getNewPassword());
        } catch (IllegalArgumentException e) {
            return failure(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        }
        if (!accountRepository.updatePassword(account.getUserId(), newPasswordHash, now())) {
            return failure(ResponseCode.UN_ERROR, "Password update failed");
        }

        String retainedSessionId = normalize(currentSessionId);
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
        return success(AuthTokenResponseDTO.builder()
                .tokenType(TOKEN_TYPE)
                .accessToken(accessToken.value())
                .expiresIn(accessToken.expiresIn())
                .user(toAccountResponse(account))
                .build(), refreshToken);
    }

    private UserAccount enabledAccount(String userId) {
        String normalizedUserId = normalize(userId);
        if (normalizedUserId == null) {
            return null;
        }
        UserAccount account = accountRepository.findByUserId(normalizedUserId);
        return account != null && account.isActive() ? account : null;
    }

    private AuthAccountResponseDTO toAccountResponse(UserAccount account) {
        return AuthAccountResponseDTO.builder()
                .userId(account.getUserId())
                .account(account.getLoginName())
                .loginName(account.getLoginName())
                .nickname(account.getNickname())
                .role(account.getRole())
                .build();
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
        return new IssuedAuthToken(failureResponse(code, info), null);
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private IssuedAuthToken success(AuthTokenResponseDTO data, String refreshToken) {
        return new IssuedAuthToken(success(data), refreshToken);
    }

    private <T> Response<T> failure(ResponseCode code, String info) {
        return failureResponse(code, info);
    }

    private <T> Response<T> failureResponse(ResponseCode code, String info) {
        return Response.<T>builder()
                .code(code.getCode())
                .info(info == null || info.isBlank() ? code.getInfo() : info)
                .data(null)
                .build();
    }

    public record IssuedAuthToken(Response<AuthTokenResponseDTO> response, String refreshToken) {
    }
}
