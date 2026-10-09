package org.wwz.ai.trigger.http.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.auth.IAuthApplicationService;
import org.wwz.ai.application.auth.JwtTokenService;
import org.wwz.ai.types.agent.user.UserRequestContext;
import org.wwz.ai.types.enums.ResponseCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Standalone access-token filter. The application bootstrap may register it
 * after the legacy placeholder filter is removed.
 */
public final class AuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATION_ATTRIBUTE = AuthenticationFilter.class.getName() + ".principal";

    private final IAuthApplicationService authApplicationService;
    private final ObjectMapper objectMapper;

    public AuthenticationFilter(IAuthApplicationService authApplicationService, ObjectMapper objectMapper) {
        this.authApplicationService = authApplicationService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "/api/auth/register".equals(path)
                || "/api/auth/login".equals(path)
                || "/api/auth/refresh".equals(path)
                || "/api/auth/logout".equals(path)
                || "/web/health".equals(path)
                || path.startsWith("/actuator/health")
                || "/api/agent/featured-conversations".equals(path)
                || "/api/agent/featured-conversations/".equals(path)
                || "/api/agent/featured-conversations/home".equals(path)
                // Relay handshakes authenticate with the pairing token in the WebSocket interceptor.
                || "/api/agent/browser/relay".equals(path)
                || "/api/agent/browser/relay/".equals(path)
                || path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        JwtTokenService.AuthenticatedAccount principal = authApplicationService.verifyAccessToken(
                resolveBearerToken(request));
        if (principal == null) {
            writeUnauthorized(response);
            return;
        }
        if (isAdminPath(path) && !"ADMIN".equalsIgnoreCase(principal.role())) {
            writeForbidden(response);
            return;
        }

        String previousUserId = UserRequestContext.currentUserId();
        request.setAttribute(AUTHENTICATION_ATTRIBUTE, principal);
        UserRequestContext.bind(principal.userId());
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousUserId == null) {
                UserRequestContext.clear();
            } else {
                UserRequestContext.bind(previousUserId);
            }
        }
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.length() < 7
                || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private boolean isAdminPath(String path) {
        return path.startsWith("/api/v1/admin/");
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Response.builder()
                .code(ResponseCode.LOGIN_FAILED.getCode())
                .info("Authentication is required")
                .build());
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Response.builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info("当前账号无权访问该接口")
                .build());
    }
}
