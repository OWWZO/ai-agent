package org.wwz.ai.application.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Password hashing policy for registered accounts.
 */
@Component
public class PasswordPolicy {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final BCryptPasswordEncoder encoder;

    public PasswordPolicy() {
        this(new BCryptPasswordEncoder());
    }

    PasswordPolicy(BCryptPasswordEncoder encoder) {
        this.encoder = encoder;
    }

    public String encode(String rawPassword) {
        validate(rawPassword);
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        try {
            validate(rawPassword);
            return encoder.matches(rawPassword, passwordHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Password exceeds BCrypt's 72-byte limit");
        }
    }
}
