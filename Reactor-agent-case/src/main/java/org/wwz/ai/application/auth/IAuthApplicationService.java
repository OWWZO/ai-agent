package org.wwz.ai.application.auth;

import org.wwz.ai.application.auth.command.AuthChangePasswordCommand;
import org.wwz.ai.application.auth.command.AuthLoginCommand;
import org.wwz.ai.application.auth.command.AuthLogoutAllCommand;
import org.wwz.ai.application.auth.command.AuthLogoutCommand;
import org.wwz.ai.application.auth.command.AuthMeCommand;
import org.wwz.ai.application.auth.command.AuthRefreshCommand;
import org.wwz.ai.application.auth.command.AuthRegisterCommand;
import org.wwz.ai.application.auth.result.AuthAccountResult;
import org.wwz.ai.application.auth.result.AuthResult;
import org.wwz.ai.application.auth.result.IssuedAuthToken;
import org.wwz.ai.application.auth.JwtTokenService.AuthenticatedAccount;

public interface IAuthApplicationService {

    long REFRESH_SLIDING_DAYS = 30;

    IssuedAuthToken register(AuthRegisterCommand command);

    IssuedAuthToken login(AuthLoginCommand command);

    IssuedAuthToken refresh(AuthRefreshCommand command);

    AuthResult<Boolean> logout(AuthLogoutCommand command);

    AuthResult<Boolean> logoutAll(AuthLogoutAllCommand command);

    AuthResult<AuthAccountResult> me(AuthMeCommand command);

    AuthResult<Boolean> changePassword(AuthChangePasswordCommand command);

    AuthenticatedAccount verifyAccessToken(String accessToken);
}
