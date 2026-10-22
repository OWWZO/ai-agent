package org.wwz.ai.test.application.auth;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.auth.JwtSecretProvider;

import java.nio.file.Files;
import java.nio.file.Path;

public class JwtSecretProviderTest {

    @Test
    public void generatesAndPersistsSecretWhenNoExplicitSecretIsConfigured() throws Exception {
        Path directory = Files.createTempDirectory("reactor-jwt-secret-");
        Path secretFile = directory.resolve("auth.jwt.secret");
        try {
            JwtSecretProvider first = new JwtSecretProvider("", secretFile);

            String generated = first.resolve();
            String loaded = new JwtSecretProvider("", secretFile).resolve();

            Assert.assertFalse(generated.isBlank());
            Assert.assertTrue(Files.exists(secretFile));
            Assert.assertEquals(generated, loaded);
        } finally {
            Files.deleteIfExists(secretFile);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void explicitSecretTakesPrecedence() throws Exception {
        JwtSecretProvider provider = new JwtSecretProvider("configured-secret");

        Assert.assertEquals("configured-secret", provider.resolve());
    }
}
