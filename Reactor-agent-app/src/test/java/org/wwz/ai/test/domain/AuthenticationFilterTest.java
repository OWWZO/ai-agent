package org.wwz.ai.test.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.wwz.ai.application.auth.IAuthApplicationService;
import org.wwz.ai.application.auth.JwtTokenService;
import org.wwz.ai.trigger.http.auth.AuthenticationFilter;
import org.wwz.ai.types.agent.user.UserRequestContext;

import java.time.Instant;

public class AuthenticationFilterTest {

    @Test
    public void protectedRequestWithoutAccessTokenReturnsUnauthorized() throws Exception {
        IAuthApplicationService authService = Mockito.mock(IAuthApplicationService.class);
        AuthenticationFilter filter = new AuthenticationFilter(authService, new ObjectMapper());
        MockHttpServletRequest request = request("/api/agent/conversation/sessions");
        request.setParameter("access_token", "query-token");
        request.addHeader("Cookie", "reactor_refresh_token=cookie-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> Assert.fail("unauthorized request reached controller");

        filter.doFilter(request, response, chain);

        Assert.assertEquals(401, response.getStatus());
        Assert.assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        String body = response.getContentAsString();
        Assert.assertTrue(body.contains("\"code\":\"0003\""));
        Assert.assertTrue(body.contains("\"info\":\"Authentication is required\""));
        Mockito.verify(authService).verifyAccessToken(null);
    }

    @Test
    public void authEndpointRemainsAnonymous() throws Exception {
        IAuthApplicationService authService = Mockito.mock(IAuthApplicationService.class);
        AuthenticationFilter filter = new AuthenticationFilter(authService, new ObjectMapper());
        MockHttpServletRequest request = request("/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletResponse[] reached = new MockHttpServletResponse[1];

        filter.doFilter(request, response, (ignoredRequest, servletResponse) ->
                reached[0] = (MockHttpServletResponse) servletResponse);

        Assert.assertSame(response, reached[0]);
        Mockito.verifyNoInteractions(authService);
    }

    @Test
    public void browserRelayHandshakeRemainsAnonymousToJwtFilter() throws Exception {
        IAuthApplicationService authService = Mockito.mock(IAuthApplicationService.class);
        AuthenticationFilter filter = new AuthenticationFilter(authService, new ObjectMapper());
        MockHttpServletRequest request = request("/api/agent/browser/relay");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletResponse[] reached = new MockHttpServletResponse[1];

        filter.doFilter(request, response, (ignoredRequest, servletResponse) ->
                reached[0] = (MockHttpServletResponse) servletResponse);

        Assert.assertSame(response, reached[0]);
        Mockito.verifyNoInteractions(authService);
    }

    @Test
    public void validAccessTokenBindsAndClearsUserContext() throws Exception {
        IAuthApplicationService authService = Mockito.mock(IAuthApplicationService.class);
        Mockito.when(authService.verifyAccessToken("access-token"))
                .thenReturn(new JwtTokenService.AuthenticatedAccount(
                        "user-1", "session-1", "USER", Instant.now(), Instant.now().plusSeconds(900)));
        AuthenticationFilter filter = new AuthenticationFilter(authService, new ObjectMapper());
        MockHttpServletRequest request = request("/api/agent/conversation/sessions");
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserRequestContext.clear();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                Assert.assertEquals("user-1", UserRequestContext.currentUserId()));

        Assert.assertNull(UserRequestContext.currentUserId());
    }

    @Test
    public void regularUserCannotAccessAdminEndpoint() throws Exception {
        IAuthApplicationService authService = Mockito.mock(IAuthApplicationService.class);
        Mockito.when(authService.verifyAccessToken("access-token"))
                .thenReturn(new JwtTokenService.AuthenticatedAccount(
                        "user-1", "session-1", "USER", Instant.now(), Instant.now().plusSeconds(900)));
        AuthenticationFilter filter = new AuthenticationFilter(authService, new ObjectMapper());
        MockHttpServletRequest request = request("/api/v1/admin/ai-client/query-all");
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                Assert.fail("regular user reached admin controller"));

        Assert.assertEquals(403, response.getStatus());
    }

    @Test
    public void regularUserCanAccessCatalogEndpoint() throws Exception {
        IAuthApplicationService authService = Mockito.mock(IAuthApplicationService.class);
        Mockito.when(authService.verifyAccessToken("access-token"))
                .thenReturn(new JwtTokenService.AuthenticatedAccount(
                        "user-1", "session-1", "USER", Instant.now(), Instant.now().plusSeconds(900)));
        AuthenticationFilter filter = new AuthenticationFilter(authService, new ObjectMapper());
        MockHttpServletRequest request = request("/api/v1/catalog/models");
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                Assert.assertEquals("user-1", UserRequestContext.currentUserId()));

        Assert.assertEquals(200, response.getStatus());
        Assert.assertNull(UserRequestContext.currentUserId());
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }
}
