package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.application.agent.query.GptQueryCommand;
import org.wwz.ai.application.agent.query.mapper.AgentExecutionCommandMapper;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.types.agent.user.UserRequestContext;

/**
 * 主聊天请求 userId 透传测试。
 */
public class AgentQueryServiceUserIdPropagationTest {

    @Test
    public void shouldPropagateUserIdentityIntoInternalAgentRequest() {
        AgentExecutionCommandMapper mapper = mapper();
        GptQueryCommand request = GptQueryCommand.builder()
                .traceId("trace-user-001")
                .sessionId("session-user-001")
                .requestId("req-user-001")
                .query("帮我生成总结")
                .deepThink(0)
                .build();

        Assert.assertNull(request.getUser());
        UserRequestContext.bind("user-001");
        try {
            AgentExecutionCommand agentRequest = mapper.toExecutionCommand(request, "user-001");
            Assert.assertNotNull(agentRequest);
            Assert.assertEquals("user-001", agentRequest.getUserId());
            Assert.assertNull(agentRequest.getErp());
            Assert.assertFalse(Boolean.TRUE.equals(agentRequest.getForcePlanMode()));
        } finally {
            UserRequestContext.clear();
        }
    }

    @Test
    public void shouldPropagateForcePlanMode() {
        AgentExecutionCommandMapper mapper = mapper();
        GptQueryCommand request = GptQueryCommand.builder()
                .traceId("trace-plan-001")
                .sessionId("session-plan-001")
                .requestId("req-plan-001")
                .query("重新规划")
                .deepThink(1)
                .forcePlanMode(true)
                .user("reactor")
                .build();

        AgentExecutionCommand agentRequest = mapper.toExecutionCommand(request, null);
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

    private AgentExecutionCommandMapper mapper() {
        AgentExecutionCommandMapper mapper = new AgentExecutionCommandMapper();
        ReflectionTestUtils.setField(mapper, "reactorConfig", buildReactorConfig());
        return mapper;
    }
}
