package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.memory.ltm.SessionSearchService;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.common.SessionSearchTool;

import java.util.Map;
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
        Assert.assertTrue(String.valueOf(out).contains("小猫"));
        Assert.assertEquals("visitor-abc", capturedVisitor.get());
        Assert.assertEquals("user", capturedScope.get());
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
}
