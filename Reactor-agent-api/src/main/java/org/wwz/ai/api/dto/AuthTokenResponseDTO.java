package org.wwz.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Access-token response. The refresh token is deliberately omitted; the HTTP
 * adapter writes it to an HttpOnly cookie.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthTokenResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tokenType;

    private String accessToken;

    private long expiresIn;

    private AuthAccountResponseDTO user;
}
