package org.wwz.ai.application.auth.result;

public record AuthTokenResult(String tokenType,
                              String accessToken,
                              long expiresIn,
                              AuthAccountResult user) {
}
