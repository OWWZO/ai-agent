package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.memory.ltm.SessionSearchService;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.SessionSearchTool;

import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class SessionSearchToolTest {

    @Test
    public void delegatesWithVisitorAndDefaultUserScope() {
        AtomicReference<String> capturedVisitor = new AtomicReference<>();
        AtomicReference<String> capturedScope = new AtomicReference<>();
        SessionSearchService stub = (sessionId, visitorId, query, limit, scope) -> {
            capturedVisitor.set(visitorId);
            capturedScope.set(scope);
            return "session_search hits (1) scope=" + scope + " visitor=" + visitorId
                    + ":\n- sessionId=" + sessionId + " query: " + query;
        };
        AgentContext ctx = AgentContext.builder()
                .sessionId("sess-1")
                .ltmOwner(LtmOwner.visitor("visitor-abc"))
                .runtimeDependencies(ReactorRuntimeDependencies.builder()
                        .sessionSearchService(stub)
                        .build())
                .build();
        SessionSearchTool tool = new SessionSearchTool();
        tool.setAgentContext(ctx);
        Object out = tool.execute(Map.of("query", "小猫", "limit", 5));
        Assert.assertTrue(out instanceof ToolResultPayload);
        ToolResultPayload payload = (ToolResultPayload) out;
        Assert.assertFalse(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(payload.getLlmData() instanceof Map<?, ?>);
        Map<?, ?> data = (Map<?, ?>) payload.getLlmData();
        Assert.assertEquals("session_search", data.get("tool"));
        Assert.assertEquals(Boolean.TRUE, data.get("ok"));
        Assert.assertTrue(String.valueOf(data.get("result")).contains("小猫"));
        Assert.assertEquals("visitor-abc", capturedVisitor.get());
        Assert.assertEquals("user", capturedScope.get());
    }

    @Test
    public void unavailableSearchReturnsStructuredFailure() {
        SessionSearchTool tool = new SessionSearchTool();
        tool.setAgentContext(AgentContext.builder().sessionId("sess-1").build());

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("query", "小猫"));

        Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(payload.getLlmData() instanceof Map<?, ?>);
        Map<?, ?> detail = (Map<?, ?>) payload.getLlmData();
        Assert.assertEquals("session_search", detail.get("tool"));
        Assert.assertEquals("小猫", detail.get("query"));
    }

    @Test
    public void respectsSessionScope() {
        AtomicReference<String> capturedScope = new AtomicReference<>();
        SessionSearchService stub = (sessionId, visitorId, query, limit, scope) -> {
            capturedScope.set(scope);
            return "ok";
        };
        AgentContext ctx = AgentContext.builder()
                .sessionId("sess-1")
                .ltmOwner(LtmOwner.visitor("v1"))
                .runtimeDependencies(ReactorRuntimeDependencies.builder()
                        .sessionSearchService(stub)
                        .build())
                .build();
        SessionSearchTool tool = new SessionSearchTool();
        tool.setAgentContext(ctx);
        tool.execute(Map.of("query", "x", "scope", "session"));
        Assert.assertEquals("session", capturedScope.get());
    }

    @Test
    public void queryIsOptionalForBrowseAndScrollParametersAreExposed() {
        Map<String, Object> params = new SessionSearchTool().toParams();
        Assert.assertEquals(List.of(), params.get("required"));
        Map<?, ?> properties = (Map<?, ?>) params.get("properties");
        Assert.assertTrue(properties.containsKey("session_id"));
        Assert.assertTrue(properties.containsKey("around_message_id"));
        Assert.assertTrue(properties.containsKey("window"));
        Assert.assertTrue(properties.containsKey("role_filter"));
    }

    @Test
    public void forwardsRoleFilterOnDiscoverRequest() {
        AtomicReference<String> capturedRoleFilter = new AtomicReference<>();
        SessionSearchService stub = new SessionSearchService() {
            @Override
            public String search(org.wwz.ai.domain.agent.memory.ltm.SessionSearchRequest request) {
                capturedRoleFilter.set(request.getRoleFilter());
                return "{\"success\":true,\"mode\":\"discover\",\"results\":[],\"count\":0}";
            }

            @Override
            public String search(String sessionId, String visitorId, String query, int limit, String scope) {
                return search(org.wwz.ai.domain.agent.memory.ltm.SessionSearchRequest.builder()
                        .currentSessionId(sessionId)
                        .visitorId(visitorId)
                        .query(query)
                        .limit(limit)
                        .scope(scope)
                        .build());
            }
        };
        AgentContext ctx = AgentContext.builder()
                .sessionId("sess-1")
                .ltmOwner(LtmOwner.visitor("v1"))
                .runtimeDependencies(ReactorRuntimeDependencies.builder()
                        .sessionSearchService(stub)
                        .build())
                .build();
        SessionSearchTool tool = new SessionSearchTool();
        tool.setAgentContext(ctx);
        tool.execute(Map.of("query", "GenUI", "role_filter", "user,assistant,tool"));
        Assert.assertEquals("user,assistant,tool", capturedRoleFilter.get());
    }

    @Test
    public void exposesStructuredServiceResultAtToolTopLevel() {
        SessionSearchService stub = (sessionId, visitorId, query, limit, scope) ->
                "{\"success\":true,\"mode\":\"browse\",\"results\":[],\"count\":0}";
        AgentContext ctx = AgentContext.builder()
                .sessionId("sess-1")
                .runtimeDependencies(ReactorRuntimeDependencies.builder()
                        .sessionSearchService(stub)
                        .build())
                .build();
        SessionSearchTool tool = new SessionSearchTool();
        tool.setAgentContext(ctx);

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of());
        Map<?, ?> data = (Map<?, ?>) payload.getLlmData();
        Assert.assertEquals("browse", data.get("mode"));
        Assert.assertEquals(0, data.get("count"));
        Assert.assertFalse(data.containsKey("result"));
    }
}
