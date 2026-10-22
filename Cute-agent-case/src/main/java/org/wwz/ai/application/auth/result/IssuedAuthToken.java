package org.wwz.ai.application.auth.result;

public record IssuedAuthToken(AuthResult<AuthTokenResult> result, String refreshToken) {
}
