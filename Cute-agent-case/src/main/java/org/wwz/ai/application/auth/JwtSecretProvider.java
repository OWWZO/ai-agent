package org.wwz.ai.application.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Resolves the JWT signing secret without requiring local development setup.
 */
@Component
public class JwtSecretProvider {

    private static final int SECRET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String configuredSecret;
    private final Path secretFile;

    @Autowired
    public JwtSecretProvider(@Value("${auth.jwt.secret:}") String configuredSecret) {
        this(configuredSecret, defaultSecretFile());
    }

    public JwtSecretProvider(String configuredSecret, Path secretFile) {
        this.configuredSecret = configuredSecret;
        this.secretFile = secretFile.toAbsolutePath().normalize();
    }

    public synchronized String resolve() {
        String explicitSecret = normalize(configuredSecret);
        if (explicitSecret != null) {
            return explicitSecret;
        }

        try {
            if (Files.exists(secretFile)) {
                return readSecret();
            }

            Path parent = secretFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            String generatedSecret = generateSecret();
            try {
                Files.writeString(
                        secretFile,
                        generatedSecret + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE
                );
                return generatedSecret;
            } catch (FileAlreadyExistsException ignored) {
                return readSecret();
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to load or create JWT secret at " + secretFile
                            + "; set REACTOR_AUTH_JWT_SECRET or make the runtime data directory writable",
                    e
            );
        }
    }

    private String readSecret() throws IOException {
        String secret = normalize(Files.readString(secretFile, StandardCharsets.UTF_8));
        if (secret == null) {
            throw new IOException("JWT secret file is empty: " + secretFile);
        }
        return secret;
    }

    private String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Path defaultSecretFile() {
        return Path.of(System.getProperty("user.dir", "."), "data", "auth.jwt.secret")
                .toAbsolutePath()
                .normalize();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
