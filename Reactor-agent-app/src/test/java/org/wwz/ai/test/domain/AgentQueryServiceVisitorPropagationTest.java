package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.runtime.GptQueryAgentRequestFactory;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

/**
 * 主聊天请求 visitor 透传测试。
 */
public class AgentQueryServiceVisitorPropagationTest {

    @Test
    public void shouldPropagateVisitorIdentityIntoInternalAgentRequest() {
        GptQueryAgentRequestFactory factory = new GptQueryAgentRequestFactory(buildReactorConfig());
        GptQueryReq request = GptQueryReq.builder()
                .traceId("trace-visitor-001")
                .sessionId("session-visitor-001")
                .requestId("req-visitor-001")
                .query("帮我生成总结")
                .deepThink(0)
                .user("reactor")
                .build();

        VisitorRequestContext.bind("visitor-001");
        try {
            AgentRequest agentRequest = factory.build(request);
            Assert.assertNotNull(agentRequest);
            Assert.assertEquals("visitor-001", agentRequest.getVisitorId());
            Assert.assertFalse(Boolean.TRUE.equals(agentRequest.getForcePlanMode()));
        } finally {
            VisitorRequestContext.clear();
        }
    }

    @Test
    public void shouldPropagateForcePlanMode() {
        GptQueryAgentRequestFactory factory = new GptQueryAgentRequestFactory(buildReactorConfig());
        GptQueryReq request = GptQueryReq.builder()
                .traceId("trace-plan-001")
                .sessionId("session-plan-001")
                .requestId("req-plan-001")
                .query("重新规划")
                .deepThink(1)
                .forcePlanMode(true)
                .user("reactor")
                .build();

        AgentRequest agentRequest = factory.build(request);
        Assert.assertTrue(Boolean.TRUE.equals(agentRequest.getForcePlanMode()));
        Assert.assertEquals(Integer.valueOf(3), agentRequest.getAgentType());
    }

    private ReactorConfig buildReactorConfig() {
        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "reactorBasePrompt", "react-base-prompt");
        ReflectionTestUtils.setField(reactorConfig, "sseClientReadTimeout", 300);
        ReflectionTestUtils.setField(reactorConfig, "sseClientConnectTimeout", 60);
        return reactorConfig;
    }
}
