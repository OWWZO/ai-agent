package org.wwz.ai.test.application.agent;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.agent.stream.AgentSessionStreamFrame;
import org.wwz.ai.application.agent.stream.AgentStreamFrameMapper;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamResult;

import java.util.Map;

public class AgentStreamFrameMapperTest {

    @Test
    public void mapsRuntimeNamesToExistingWireFields() {
        AgentStreamResult result = AgentStreamResult.builder()
                .status("success")
                .contentDelta("delta")
                .content("all")
                .complete(true)
                .elapsedMillis(125L)
                .tokenCount(8L)
                .eventData(Map.of("eventData", Map.of("messageType", "result")))
                .contentType("text")
                .traceId("trace-1")
                .requestId("request-1")
                .frameType("result")
                .errorMessage("none")
                .sequence(17L)
                .retryAfterMs(800L)
                .build();

        AgentSessionStreamFrame frame = new AgentStreamFrameMapper().toFrame(result);

        Assert.assertEquals("delta", frame.getResponse());
        Assert.assertEquals("all", frame.getResponseAll());
        Assert.assertTrue(frame.isFinished());
        Assert.assertEquals(125L, frame.getUseTimes());
        Assert.assertEquals(8L, frame.getUseTokens());
        Assert.assertEquals("request-1", frame.getReqId());
        Assert.assertEquals(17L, frame.getEventSeq());
        Assert.assertEquals(Long.valueOf(800L), frame.getRetryMs());
    }
}
