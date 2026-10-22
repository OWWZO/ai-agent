package org.wwz.ai.test.trigger;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.application.agent.stream.AgentSessionStreamFrame;
import org.wwz.ai.trigger.http.agent.mapper.AgentStreamResponseMapper;
import org.wwz.ai.trigger.http.agent.mapper.GptQueryRequestMapper;
import org.wwz.ai.trigger.http.agent.vo.AgentStreamResponseVO;
import org.wwz.ai.trigger.http.agent.vo.GptQueryRequestVO;

import java.util.List;

public class AgentStreamContractTest {

    @Test
    public void mapsHttpQueryWithoutLeakingHttpVoIntoCaseCommand() {
        GptQueryRequestVO request = GptQueryRequestVO.builder()
                .requestId("req-1")
                .sessionId("session-1")
                .query("hello")
                .sessionFiles(List.of(GptQueryRequestVO.FileReferenceVO.builder()
                        .fileName("input.txt")
                        .resourceKey("resource-1")
                        .build()))
                .build();

        var command = new GptQueryRequestMapper().toCommand(request);

        Assert.assertEquals("req-1", command.getRequestId());
        Assert.assertEquals("hello", command.getQuery());
        Assert.assertEquals("resource-1", command.getSessionFiles().get(0).getResourceKey());
        Assert.assertEquals("org.wwz.ai.application.agent.query.GptQueryCommand",
                command.getClass().getName());
    }

    @Test
    public void mapsCaseFrameToTriggerResponseWithoutChangingWireFields() {
        AgentSessionStreamFrame frame = AgentSessionStreamFrame.builder()
                .status("success")
                .response("delta")
                .responseAll("all")
                .finished(true)
                .packageType("result")
                .eventSeq(4L)
                .build();

        AgentStreamResponseVO response = new AgentStreamResponseMapper().toResponse(frame);

        Assert.assertEquals("delta", response.getResponse());
        Assert.assertEquals("all", response.getResponseAll());
        Assert.assertTrue(response.isFinished());
        Assert.assertEquals("result", response.getPackageType());
        Assert.assertEquals(4L, response.getEventSeq());
    }
}
