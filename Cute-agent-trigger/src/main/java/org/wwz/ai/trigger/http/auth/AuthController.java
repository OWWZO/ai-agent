package org.wwz.ai.trigger.http.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.dto.AuthAccountResponseDTO;
import org.wwz.ai.api.dto.AuthChangePasswordRequestDTO;
import org.wwz.ai.api.dto.AuthLoginRequestDTO;
import org.wwz.ai.api.dto.AuthRegisterRequestDTO;
import org.wwz.ai.api.dto.AuthTokenResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.auth.IAuthApplicationService;
import org.wwz.ai.application.auth.JwtTokenService;
import org.wwz.ai.application.auth.result.IssuedAuthToken;
import org.wwz.ai.trigger.http.auth.mapper.AuthHttpMapper;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.Duration;

/**
 * Public account authentication endpoints.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String REFRESH_COOKIE_NAME = "reactor_refresh_token";

    private final IAuthApplicationService authApplicationService;
    private final AuthHttpMapper authHttpMapper;
    private final boolean secureCookie;
    private final String sameSite;

    public AuthController(IAuthApplicationService authApplicationService,
                          AuthHttpMapper authHttpMapper,
                          @Value("${auth.refresh-cookie.secure:false}") boolean secureCookie,
                          @Value("${auth.refresh-cookie.same-site:Lax}") String sameSite) {
        this.authApplicationService = authApplicationService;
        this.authHttpMapper = authHttpMapper;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
    }

    @PostMapping("/register")
    public ResponseEntity<Response<AuthTokenResponseDTO>> register(
            @RequestBody(required = false) AuthRegisterRequestDTO request) {
        IssuedAuthToken result = authApplicationService.register(authHttpMapper.toRegisterCommand(request));
        return withRefreshCookie(authHttpMapper.toTokenResponse(result.result()), result.refreshToken());
    }

    @PostMapping("/login")
    public ResponseEntity<Response<AuthTokenResponseDTO>> login(
            @RequestBody(required = false) AuthLoginRequestDTO request) {
        IssuedAuthToken result = authApplicationService.login(authHttpMapper.toLoginCommand(request));
        return withRefreshCookie(authHttpMapper.toTokenResponse(result.result()), result.refreshToken());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Response<AuthTokenResponseDTO>> refresh(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        IssuedAuthToken result = authApplicationService.refresh(authHttpMapper.toRefreshCommand(refreshToken));
        return withRefreshCookie(authHttpMapper.toTokenResponse(result.result()), result.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Response<Boolean>> logout(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        return withClearedRefreshCookie(authHttpMapper.toBooleanResponse(
                authApplicationService.logout(authHttpMapper.toLogoutCommand(refreshToken))));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Response<Boolean>> logoutAll(HttpServletRequest request) {
        JwtTokenService.AuthenticatedAccount principal = principal(request);
        if (principal == null) {
            return unauthorized();
        }
        return withClearedRefreshCookie(authHttpMapper.toBooleanResponse(
                authApplicationService.logoutAll(authHttpMapper.toLogoutAllCommand(principal.userId()))));
    }

    @GetMapping("/me")
    public ResponseEntity<Response<AuthAccountResponseDTO>> me(HttpServletRequest request) {
        JwtTokenService.AuthenticatedAccount principal = principal(request);
        if (principal == null) {
            return unauthorized();
        }
        return response(authHttpMapper.toAccountResponse(
                authApplicationService.me(authHttpMapper.toMeCommand(principal.userId()))));
    }

    @PutMapping("/password")
    public ResponseEntity<Response<Boolean>> changePassword(
            HttpServletRequest request,
            @RequestBody(required = false) AuthChangePasswordRequestDTO body) {
        JwtTokenService.AuthenticatedAccount principal = principal(request);
        if (principal == null) {
            return unauthorized();
        }
        return response(authHttpMapper.toBooleanResponse(authApplicationService.changePassword(
                authHttpMapper.toChangePasswordCommand(
                        principal.userId(), String.valueOf(principal.sessionId()), body))));
    }

    private JwtTokenService.AuthenticatedAccount principal(HttpServletRequest request) {
        Object value = request.getAttribute(AuthenticationFilter.AUTHENTICATION_ATTRIBUTE);
        return value instanceof JwtTokenService.AuthenticatedAccount account ? account : null;
    }

    private ResponseEntity<Response<AuthTokenResponseDTO>> withRefreshCookie(
            Response<AuthTokenResponseDTO> result,
            String refreshToken) {
        ResponseEntity.BodyBuilder builder = statusFor(result);
        if (isSuccess(result) && refreshToken != null) {
            builder.header(HttpHeaders.SET_COOKIE,
                    refreshCookie(refreshToken, Duration.ofDays(IAuthApplicationService.REFRESH_SLIDING_DAYS)).toString());
        }
        return builder.body(result);
    }

    private ResponseEntity<Response<Boolean>> withClearedRefreshCookie(Response<Boolean> result) {
        return statusFor(result)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(null, Duration.ZERO).toString())
                .body(result);
    }

    private <T> ResponseEntity<Response<T>> response(Response<T> result) {
        return statusFor(result).body(result);
    }

    private <T> ResponseEntity<Response<T>> unauthorized() {
        Response<T> body = Response.<T>builder()
                .code(ResponseCode.LOGIN_FAILED.getCode())
                .info("Authentication is required")
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    private <T> ResponseEntity.BodyBuilder statusFor(Response<T> result) {
        if (result != null && ResponseCode.SUCCESS.getCode().equals(result.getCode())) {
            return ResponseEntity.ok();
        }
        if (result != null && ResponseCode.LOGIN_FAILED.getCode().equals(result.getCode())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST);
    }

    private boolean isSuccess(Response<?> result) {
        return result != null && ResponseCode.SUCCESS.getCode().equals(result.getCode());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value == null ? "" : value)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .sameSite(sameSite)
                .maxAge(maxAge)
                .build();
    }
}
