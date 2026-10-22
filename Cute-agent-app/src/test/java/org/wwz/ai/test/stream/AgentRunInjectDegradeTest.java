package org.wwz.ai.test.stream;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.agent.run.AgentRunInjectApplicationService;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.cancel.ActiveAgentRunRegistry;

import java.util.Map;

public class AgentRunInjectDegradeTest {

    @Test
    public void missingRunDegradesToNewRun() {
        AgentRunInjectApplicationService service =
                new AgentRunInjectApplicationService(new ActiveAgentRunRegistry());
        Map<String, Object> result = service.inject("sess-1", "req-gone", "下一句");
        Assert.assertEquals(false, result.get("accepted"));
        Assert.assertEquals("new_run", result.get("mode"));
    }

    @Test
    public void turnClosedDegradesToNewRun() {
        ActiveAgentRunRegistry registry = new ActiveAgentRunRegistry();
        registry.begin("req-closed", "sess-1", "user-1");
        AgentContext context = AgentContext.builder()
                .requestId("req-closed")
                .sessionId("sess-1")
                .build();
        context.markTurnClosed();
        registry.bindContext("req-closed", context);

        AgentRunInjectApplicationService service = new AgentRunInjectApplicationService(registry);
        Map<String, Object> result = service.inject("sess-1", "req-closed", "下一句");
        Assert.assertEquals(false, result.get("accepted"));
        Assert.assertEquals("new_run", result.get("mode"));
    }
}
