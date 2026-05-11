package org.wwz.ai.trigger.http.visitor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.filter.OncePerRequestFilter;
import org.wwz.ai.application.agent.visitor.AnonymousVisitorApplicationService;
import org.wwz.ai.application.agent.visitor.model.AnonymousVisitorIdentity;
import org.wwz.ai.types.agent.config.AgentExecutorProperties;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import java.io.IOException;
import java.time.Duration;

/**
 * 匿名访客身份过滤器。
 */
public class VisitorIdentityFilter extends OncePerRequestFilter {

    private final AnonymousVisitorApplicationService anonymousVisitorApplicationService;
    private final AgentExecutorProperties.VisitorCookie visitorCookieProperties;

    public VisitorIdentityFilter(AnonymousVisitorApplicationService anonymousVisitorApplicationService,
                                 AgentExecutorProperties.VisitorCookie visitorCookieProperties) {
        this.anonymousVisitorApplicationService = anonymousVisitorApplicationService;
        this.visitorCookieProperties = visitorCookieProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(StringUtils.startsWith(path, "/web/api/v1/gpt/queryAgentStreamIncr")
                || StringUtils.startsWith(path, "/1/web/api/v1/gpt/queryAgentStreamIncr")
                || StringUtils.startsWith(path, "/api/agent/visitor")
                || StringUtils.startsWith(path, "/api/agent/conversation/sessions")
                || StringUtils.startsWith(path, "/api/agent/file"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AnonymousVisitorIdentity identity = anonymousVisitorApplicationService.resolveOrCreate(
                readCookieValue(request, visitorCookieProperties.getName()),
                request.getHeader("User-Agent"),
                resolveClientIp(request)
        );
        VisitorRequestContext.bind(identity.getVisitorId());
        if (identity.isNewlyCreated()) {
            response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(identity.getRawToken()).toString());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            VisitorRequestContext.clear();
        }
    }

    private ResponseCookie buildCookie(String rawToken) {
        return ResponseCookie.from(visitorCookieProperties.getName(), rawToken)
                .httpOnly(visitorCookieProperties.isHttpOnly())
                .secure(visitorCookieProperties.isSecure())
                .sameSite(visitorCookieProperties.getSameSite())
                .path(visitorCookieProperties.getPath())
                .maxAge(Duration.ofDays(visitorCookieProperties.getMaxAgeDays()))
                .build();
    }

    private String readCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0 || StringUtils.isBlank(cookieName)) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie != null && StringUtils.equals(cookieName, cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
