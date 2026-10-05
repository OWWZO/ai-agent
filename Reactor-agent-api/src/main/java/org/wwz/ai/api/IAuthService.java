package org.wwz.ai.api;

import org.wwz.ai.api.dto.AuthAccountResponseDTO;
import org.wwz.ai.api.dto.AuthChangePasswordRequestDTO;
import org.wwz.ai.api.dto.AuthLoginRequestDTO;
import org.wwz.ai.api.dto.AuthRegisterRequestDTO;
import org.wwz.ai.api.dto.AuthTokenResponseDTO;
import org.wwz.ai.api.response.Response;

/**
 * Public account authentication contract.
 */
public interface IAuthService {

    /**
     * Register a public account and create its first login session.
     */
    Response<AuthTokenResponseDTO> register(AuthRegisterRequestDTO request);

    /**
     * Authenticate an account and create an access/refresh token pair.
     */
    Response<AuthTokenResponseDTO> login(AuthLoginRequestDTO request);

    /**
     * Rotate a refresh session and issue a new token pair.
     */
    Response<AuthTokenResponseDTO> refresh(String refreshToken);

    /**
     * Revoke the refresh session represented by the request.
     */
    Response<Boolean> logout(String refreshToken);

    /**
     * Revoke every refresh session owned by the account.
     */
    Response<Boolean> logoutAll(String userId);

    /**
     * Return the enabled account represented by the authenticated request.
     */
    Response<AuthAccountResponseDTO> me(String userId);

    /**
     * Change the password and revoke refresh sessions other than the current one.
     */
    Response<Boolean> changePassword(String userId,
                                     String currentSessionId,
                                     AuthChangePasswordRequestDTO request);
}
