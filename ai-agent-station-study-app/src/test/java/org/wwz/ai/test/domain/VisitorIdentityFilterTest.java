package org.wwz.ai.test.domain;

import jakarta.servlet.FilterChain;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.wwz.ai.application.agent.visitor.AnonymousVisitorApplicationService;
import org.wwz.ai.application.agent.visitor.model.AnonymousVisitorIdentity;
import org.wwz.ai.trigger.http.visitor.VisitorIdentityFilter;
import org.wwz.ai.types.agent.config.AgentExecutorProperties;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 匿名访客过滤器测试。
 */
public class VisitorIdentityFilterTest {

    @Test
    public void shouldIssueCookieAndBindContextForFirstVisitorRequest() throws Exception {
        AnonymousVisitorApplicationService service = Mockito.mock(AnonymousVisitorApplicationService.class);
        Mockito.when(service.resolveOrCreate(Mockito.isNull(), Mockito.any(), Mockito.any()))
                .thenReturn(AnonymousVisitorIdentity.builder()
                        .visitorId("visitor-001")
                        .rawToken("token-001")
                        .newlyCreated(true)
                        .build());
        VisitorIdentityFilter filter = new VisitorIdentityFilter(service, buildCookieProperties(false, "Lax"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/web/api/v1/gpt/queryAgentStreamIncr");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> visitorSeenInChain = new AtomicReference<>();

        filter.doFilter(request, response, captureVisitorChain(visitorSeenInChain));

        Assert.assertEquals("visitor-001", visitorSeenInChain.get());
        Assert.assertNull(VisitorRequestContext.currentVisitorId());
        String setCookie = response.getHeader("Set-Cookie");
        Assert.assertNotNull(setCookie);
        Assert.assertTrue(setCookie.contains("ai_agent_visitor_token=token-001"));
        Assert.assertTrue(setCookie.contains("HttpOnly"));
        Assert.assertTrue(setCookie.contains("Path=/"));
        Assert.assertTrue(setCookie.contains("SameSite=Lax"));
    }

    @Test
    public void shouldRotateInvalidTokenAndRewriteCookie() throws Exception {
        AnonymousVisitorApplicationService service = Mockito.mock(AnonymousVisitorApplicationService.class);
        Mockito.when(service.resolveOrCreate(Mockito.eq("stale-token"), Mockito.any(), Mockito.any()))
                .thenReturn(AnonymousVisitorIdentity.builder()
                        .visitorId("visitor-002")
                        .rawToken("fresh-token")
                        .newlyCreated(true)
                        .build());
        VisitorIdentityFilter filter = new VisitorIdentityFilter(service, buildCookieProperties(true, "None"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/conversation/sessions");
        request.setCookies(new jakarta.servlet.http.Cookie("ai_agent_visitor_token", "stale-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, captureVisitorChain(new AtomicReference<>()));

        String setCookie = response.getHeader("Set-Cookie");
        Assert.assertNotNull(setCookie);
        Assert.assertTrue(setCookie.contains("ai_agent_visitor_token=fresh-token"));
        Assert.assertTrue(setCookie.contains("Secure"));
        Assert.assertTrue(setCookie.contains("SameSite=None"));
    }

    @Test
    public void shouldProtectVisitorBootstrapEndpoint() throws Exception {
        AnonymousVisitorApplicationService service = Mockito.mock(AnonymousVisitorApplicationService.class);
        Mockito.when(service.resolveOrCreate(Mockito.isNull(), Mockito.any(), Mockito.any()))
                .thenReturn(AnonymousVisitorIdentity.builder()
                        .visitorId("visitor-003")
                        .rawToken("token-003")
                        .newlyCreated(true)
                        .build());
        VisitorIdentityFilter filter = new VisitorIdentityFilter(service, buildCookieProperties(false, "Lax"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/visitor/bootstrap");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> visitorSeenInChain = new AtomicReference<>();

        filter.doFilter(request, response, captureVisitorChain(visitorSeenInChain));

        Assert.assertEquals("visitor-003", visitorSeenInChain.get());
        Assert.assertTrue(response.getHeader("Set-Cookie").contains("ai_agent_visitor_token=token-003"));
    }

    private FilterChain captureVisitorChain(AtomicReference<String> visitorSeenInChain) {
        return (request, response) -> visitorSeenInChain.set(VisitorRequestContext.currentVisitorId());
    }

    private AgentExecutorProperties.VisitorCookie buildCookieProperties(boolean secure, String sameSite) {
        AgentExecutorProperties.VisitorCookie cookie = new AgentExecutorProperties.VisitorCookie();
        cookie.setName("ai_agent_visitor_token");
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setSameSite(sameSite);
        cookie.setPath("/");
        cookie.setMaxAgeDays(365L);
        return cookie;
    }
}
