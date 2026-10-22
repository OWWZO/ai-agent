package org.wwz.ai.trigger.http.auth.mapper;

import org.springframework.stereotype.Component;
import org.wwz.ai.api.dto.AuthAccountResponseDTO;
import org.wwz.ai.api.dto.AuthChangePasswordRequestDTO;
import org.wwz.ai.api.dto.AuthLoginRequestDTO;
import org.wwz.ai.api.dto.AuthRegisterRequestDTO;
import org.wwz.ai.api.dto.AuthTokenResponseDTO;
import org.wwz.ai.api.response.Response;
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

import java.util.function.Function;

/** Auth HTTP DTO 与应用命令/结果之间的显式转换。 */
@Component
public class AuthHttpMapper {

    public AuthRegisterCommand toRegisterCommand(AuthRegisterRequestDTO request) {
        return request == null ? null : new AuthRegisterCommand(
                request.getLoginName(), request.getPassword(), request.getNickname());
    }

    public AuthLoginCommand toLoginCommand(AuthLoginRequestDTO request) {
        return request == null ? null : new AuthLoginCommand(
                request.getLoginName(), request.getAccount(), request.getPassword());
    }

    public AuthRefreshCommand toRefreshCommand(String refreshToken) {
        return new AuthRefreshCommand(refreshToken);
    }

    public AuthLogoutCommand toLogoutCommand(String refreshToken) {
        return new AuthLogoutCommand(refreshToken);
    }

    public AuthLogoutAllCommand toLogoutAllCommand(String userId) {
        return new AuthLogoutAllCommand(userId);
    }

    public AuthMeCommand toMeCommand(String userId) {
        return new AuthMeCommand(userId);
    }

    public AuthChangePasswordCommand toChangePasswordCommand(String userId,
                                                             String currentSessionId,
                                                             AuthChangePasswordRequestDTO request) {
        return request == null ? null : new AuthChangePasswordCommand(
                userId, currentSessionId, request.getOldPassword(), request.getNewPassword());
    }

    public Response<AuthTokenResponseDTO> toTokenResponse(AuthResult<AuthTokenResult> result) {
        return response(result, this::toTokenResponse);
    }

    public Response<AuthAccountResponseDTO> toAccountResponse(AuthResult<AuthAccountResult> result) {
        return response(result, this::toAccountResponse);
    }

    public Response<Boolean> toBooleanResponse(AuthResult<Boolean> result) {
        return response(result, value -> value);
    }

    private AuthTokenResponseDTO toTokenResponse(AuthTokenResult result) {
        if (result == null) {
            return null;
        }
        return AuthTokenResponseDTO.builder()
                .tokenType(result.tokenType())
                .accessToken(result.accessToken())
                .expiresIn(result.expiresIn())
                .user(toAccountResponse(result.user()))
                .build();
    }

    private AuthAccountResponseDTO toAccountResponse(AuthAccountResult result) {
        if (result == null) {
            return null;
        }
        return AuthAccountResponseDTO.builder()
                .userId(result.userId())
                .account(result.loginName())
                .loginName(result.loginName())
                .nickname(result.nickname())
                .role(result.role())
                .build();
    }

    private <T, R> Response<R> response(AuthResult<T> result, Function<T, R> mapper) {
        return Response.<R>builder()
                .code(result.code().getCode())
                .info(result.info())
                .data(mapper.apply(result.data()))
                .build();
    }
}
