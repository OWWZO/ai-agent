package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteStreamRequest;
import org.wwz.ai.domain.agent.runtime.AgentQueryServiceImpl;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import java.util.Map;

/**
 * AgentQueryService visitor 透传测试。
 */
public class AgentQueryServiceVisitorPropagationTest {

    @Test
    public void shouldPropagateVisitorIdentityIntoInternalAgentRequest() {
        AgentQueryServiceImpl service = new AgentQueryServiceImpl(buildReactorConfig(), Map.of(), null);
        GptQueryReq request = GptQueryReq.builder()
                .traceId("trace-visitor-001")
                .sessionId("session-visitor-001")
                .requestId("req-visitor-001")
                .query("帮我生成总结")
                .deepThink(0)
                .outputStyle("html")
                .user("reactor")
                .build();

        VisitorRequestContext.bind("visitor-001");
        try {
            AgentRequest agentRequest = ReflectionTestUtils.invokeMethod(service, "buildAgentRequest", request);
            Assert.assertNotNull(agentRequest);
            Assert.assertEquals("visitor-001", agentRequest.getVisitorId());

            RemoteStreamRequest remoteRequest = ReflectionTestUtils.invokeMethod(service, "buildRemoteRequest", agentRequest);
            Assert.assertNotNull(remoteRequest);
            Assert.assertTrue(remoteRequest.getBody().contains("\"visitorId\":\"visitor-001\""));
        } finally {
            VisitorRequestContext.clear();
        }
    }

    private ReactorConfig buildReactorConfig() {
        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "reactorBasePrompt", "react-base-prompt");
        ReflectionTestUtils.setField(reactorConfig, "reactorSopPrompt", "plan-sop-prompt");
        ReflectionTestUtils.setField(reactorConfig, "sseClientReadTimeout", 300);
        ReflectionTestUtils.setField(reactorConfig, "sseClientConnectTimeout", 60);
        return reactorConfig;
    }
}
