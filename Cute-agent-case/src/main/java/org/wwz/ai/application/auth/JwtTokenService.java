package org.wwz.ai.application.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.auth.entity.UserAccount;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Access-token signer and verifier.
 */
@Component
public class JwtTokenService {

    private static final String ACCESS_TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String SESSION_ID_CLAIM = "sid";

    private final String secret;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Clock clock;

    @Autowired
    public JwtTokenService(JwtSecretProvider secretProvider,
                           @Value("${auth.jwt.issuer:reactor}") String issuer,
                           @Value("${auth.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds,
                           Clock clock) {
        this(secretProvider.resolve(), issuer, Duration.ofSeconds(Math.max(1, accessTokenTtlSeconds)), clock);
    }

    public JwtTokenService(String secret, String issuer, Duration accessTokenTtl, Clock clock) {
        this.secret = secret;
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
        this.clock = clock;
    }

    public IssuedAccessToken issue(UserAccount account, String sessionId) {
        if (account == null || account.getUserId() == null || account.getUserId().isBlank()) {
            throw new IllegalArgumentException("Account userId is required for JWT issuance");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session id is required for JWT issuance");
        }

        Algorithm algorithm = algorithm();
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        String token = JWT.create()
                .withIssuer(issuer)
                .withSubject(account.getUserId())
                .withClaim(SESSION_ID_CLAIM, sessionId)
                .withClaim("role", account.getRole())
                .withClaim(ACCESS_TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .withIssuedAt(Date.from(issuedAt))
                .withExpiresAt(Date.from(expiresAt))
                .sign(algorithm);
        return new IssuedAccessToken(token, expiresAt, accessTokenTtl.toSeconds());
    }

    public AuthenticatedAccount verify(String accessToken) {
        if (accessToken == null || accessToken.isBlank() || secret == null || secret.isBlank()) {
            return null;
        }
        try {
            JWTVerifier.BaseVerification verification = (JWTVerifier.BaseVerification) JWT.require(algorithm());
            verification.withIssuer(issuer);
            verification.withClaim(ACCESS_TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE);
            JWTVerifier verifier = verification.build(clock);
            DecodedJWT jwt = verifier.verify(accessToken);
            String userId = jwt.getSubject();
            String sessionIdClaim = jwt.getClaim(SESSION_ID_CLAIM).asString();
            String role = jwt.getClaim("role").asString();
            Date issuedAt = jwt.getIssuedAt();
            Date expiresAt = jwt.getExpiresAt();
            if (userId == null || userId.isBlank()
                    || sessionIdClaim == null || sessionIdClaim.isBlank()
                    || role == null || role.isBlank()
                    || issuedAt == null || expiresAt == null) {
                return null;
            }
            return new AuthenticatedAccount(
                    userId,
                    sessionIdClaim,
                    role,
                    issuedAt.toInstant(),
                    expiresAt.toInstant()
            );
        } catch (JWTVerificationException | IllegalArgumentException e) {
            return null;
        }
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    private Algorithm algorithm() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("auth.jwt.secret must be configured");
        }
        return Algorithm.HMAC256(secret);
    }

    public record IssuedAccessToken(String value, Instant expiresAt, long expiresIn) {
    }

    public record AuthenticatedAccount(String userId,
                                       String sessionId,
                                       String role,
                                       Instant issuedAt,
                                       Instant expiresAt) {
    }
}
