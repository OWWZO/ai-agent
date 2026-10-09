package org.wwz.ai.test.application.auth;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.wwz.ai.api.dto.AuthChangePasswordRequestDTO;
import org.wwz.ai.api.dto.AuthLoginRequestDTO;
import org.wwz.ai.api.dto.AuthRegisterRequestDTO;
import org.wwz.ai.api.dto.AuthTokenResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.auth.IAuthApplicationService;
import org.wwz.ai.application.auth.JwtTokenService;
import org.wwz.ai.application.auth.command.AuthChangePasswordCommand;
import org.wwz.ai.application.auth.command.AuthLoginCommand;
import org.wwz.ai.application.auth.command.AuthRegisterCommand;
import org.wwz.ai.application.auth.result.AuthAccountResult;
import org.wwz.ai.application.auth.result.AuthResult;
import org.wwz.ai.application.auth.result.AuthTokenResult;
import org.wwz.ai.application.auth.result.IssuedAuthToken;
import org.wwz.ai.trigger.http.auth.AuthController;
import org.wwz.ai.trigger.http.auth.AuthenticationFilter;
import org.wwz.ai.trigger.http.auth.mapper.AuthHttpMapper;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.Instant;

public class AuthControllerTest {

    private final IAuthApplicationService authService = Mockito.mock(IAuthApplicationService.class);
    private final AuthController controller = new AuthController(authService, new AuthHttpMapper(), true, "Strict");

    @Test
    public void registerMapsApplicationResultAndPreservesRefreshCookieContract() {
        AuthTokenResult token = new AuthTokenResult("Bearer", "access-token", 900,
                new AuthAccountResult("user-1", "alice", "Alice", "USER"));
        Mockito.when(authService.register(Mockito.any(AuthRegisterCommand.class)))
                .thenReturn(new IssuedAuthToken(AuthResult.success(token), "raw-refresh-token"));

        ResponseEntity<Response<AuthTokenResponseDTO>> response = controller.register(
                AuthRegisterRequestDTO.builder().loginName(" Alice ").password("secret").nickname("Alice").build());

        Assert.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assert.assertEquals("0000", response.getBody().getCode());
        Assert.assertEquals("成功", response.getBody().getInfo());
        Assert.assertEquals("access-token", response.getBody().getData().getAccessToken());
        Assert.assertEquals("alice", response.getBody().getData().getUser().getAccount());
        Assert.assertEquals("alice", response.getBody().getData().getUser().getLoginName());

        ArgumentCaptor<AuthRegisterCommand> command = ArgumentCaptor.forClass(AuthRegisterCommand.class);
        Mockito.verify(authService).register(command.capture());
        Assert.assertEquals(new AuthRegisterCommand(" Alice ", "secret", "Alice"), command.getValue());

        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        Assert.assertTrue(cookie.contains("reactor_refresh_token=raw-refresh-token"));
        Assert.assertTrue(cookie.contains("Max-Age=2592000"));
        Assert.assertTrue(cookie.contains("Path=/"));
        Assert.assertTrue(cookie.contains("HttpOnly"));
        Assert.assertTrue(cookie.contains("Secure"));
        Assert.assertTrue(cookie.contains("SameSite=Strict"));
    }

    @Test
    public void failuresKeepHttpStatusAndDoNotSetOrClearRefreshCookie() {
        Mockito.when(authService.register(Mockito.any(AuthRegisterCommand.class)))
                .thenReturn(new IssuedAuthToken(
                        AuthResult.failure(ResponseCode.ILLEGAL_PARAMETER, "Login name already exists"), null));
        ResponseEntity<Response<AuthTokenResponseDTO>> registration = controller.register(
                AuthRegisterRequestDTO.builder().loginName("alice").password("secret").nickname("Alice").build());
        Assert.assertEquals(HttpStatus.BAD_REQUEST, registration.getStatusCode());
        Assert.assertEquals("0002", registration.getBody().getCode());
        Assert.assertEquals("Login name already exists", registration.getBody().getInfo());
        Assert.assertNull(registration.getBody().getData());
        Assert.assertNull(registration.getHeaders().getFirst(HttpHeaders.SET_COOKIE));

        Mockito.when(authService.refresh(Mockito.any()))
                .thenReturn(new IssuedAuthToken(
                        AuthResult.failure(ResponseCode.LOGIN_FAILED, "Refresh token was already used"), null));
        ResponseEntity<Response<AuthTokenResponseDTO>> refresh = controller.refresh("used-refresh-token");
        Assert.assertEquals(HttpStatus.UNAUTHORIZED, refresh.getStatusCode());
        Assert.assertEquals("0003", refresh.getBody().getCode());
        Assert.assertEquals("Refresh token was already used", refresh.getBody().getInfo());
        Assert.assertNull(refresh.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    }

    @Test
    public void successfulRefreshSetsTheRotatedCookie() {
        AuthTokenResult token = new AuthTokenResult("Bearer", "next-access-token", 900,
                new AuthAccountResult("user-1", "alice", "Alice", "USER"));
        Mockito.when(authService.refresh(Mockito.any()))
                .thenReturn(new IssuedAuthToken(AuthResult.success(token), "next-refresh-token"));

        ResponseEntity<Response<AuthTokenResponseDTO>> response = controller.refresh("old-refresh-token");

        Assert.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assert.assertEquals("next-access-token", response.getBody().getData().getAccessToken());
        Assert.assertTrue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)
                .contains("reactor_refresh_token=next-refresh-token"));
    }

    @Test
    public void logoutClearsCookieAndUnauthorizedProtectedEndpointsKeepTheirResponse() {
        Mockito.when(authService.logout(Mockito.any()))
                .thenReturn(AuthResult.success(true));
        ResponseEntity<Response<Boolean>> logout = controller.logout("raw-refresh-token");
        Assert.assertEquals(HttpStatus.OK, logout.getStatusCode());
        Assert.assertTrue(logout.getBody().getData());
        assertClearedCookie(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE));

        ResponseEntity<Response<Boolean>> unauthenticated = controller.logoutAll(new MockHttpServletRequest());
        Assert.assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());
        Assert.assertEquals("0003", unauthenticated.getBody().getCode());
        Assert.assertEquals("Authentication is required", unauthenticated.getBody().getInfo());
        Assert.assertNull(unauthenticated.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        Mockito.verify(authService, Mockito.never()).logoutAll(Mockito.any());

        Mockito.when(authService.logoutAll(Mockito.any()))
                .thenReturn(AuthResult.failure(ResponseCode.LOGIN_FAILED, "Authentication is required"));
        ResponseEntity<Response<Boolean>> logoutAll = controller.logoutAll(authenticatedRequest());
        Assert.assertEquals(HttpStatus.UNAUTHORIZED, logoutAll.getStatusCode());
        assertClearedCookie(logoutAll.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    }

    @Test
    public void authenticatedPasswordCommandCarriesCurrentSessionId() {
        Mockito.when(authService.changePassword(Mockito.any(AuthChangePasswordCommand.class)))
                .thenReturn(AuthResult.success(true));
        MockHttpServletRequest request = authenticatedRequest();

        ResponseEntity<Response<Boolean>> response = controller.changePassword(request,
                AuthChangePasswordRequestDTO.builder().oldPassword("old-secret").newPassword("new-secret").build());

        Assert.assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<AuthChangePasswordCommand> command = ArgumentCaptor.forClass(AuthChangePasswordCommand.class);
        Mockito.verify(authService).changePassword(command.capture());
        Assert.assertEquals(new AuthChangePasswordCommand("user-1", "session-1", "old-secret", "new-secret"),
                command.getValue());
    }

    @Test
    public void loginMapperPreservesBothSupportedLoginNameFields() {
        AuthLoginCommand command = new AuthHttpMapper().toLoginCommand(
                AuthLoginRequestDTO.builder().loginName("preferred").account("legacy-alias").password("secret").build());

        Assert.assertEquals(new AuthLoginCommand("preferred", "legacy-alias", "secret"), command);
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthenticationFilter.AUTHENTICATION_ATTRIBUTE,
                new JwtTokenService.AuthenticatedAccount(
                        "user-1", "session-1", "USER", Instant.now(), Instant.now().plusSeconds(900)));
        return request;
    }

    private void assertClearedCookie(String cookie) {
        Assert.assertTrue(cookie.contains("reactor_refresh_token=;"));
        Assert.assertTrue(cookie.contains("Max-Age=0"));
        Assert.assertTrue(cookie.contains("Path=/"));
        Assert.assertTrue(cookie.contains("HttpOnly"));
        Assert.assertTrue(cookie.contains("Secure"));
        Assert.assertTrue(cookie.contains("SameSite=Strict"));
    }
}
