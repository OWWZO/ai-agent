package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.agent.enums.AgentType;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.reactor.model.req.AgentRequest;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.reactor.service.impl.MultiAgentServiceImpl;

import java.util.List;

/**
 * MultiAgentServiceImpl 请求桥接回归。
 */
public class MultiAgentServiceImplTest {

    @Test
    public void shouldCarrySessionFilesIntoAgentRequestForReactMode() {
        MultiAgentServiceImpl service = new MultiAgentServiceImpl();
        ReflectionTestUtils.setField(service, "reactorConfig", buildReactorConfig());

        List<FileInformation> sessionFiles = List.of(FileInformation.builder()
                .fileName("source-image.png")
                .domainUrl("https://file.example.com/preview/source-image.png")
                .ossUrl("https://file.example.com/download/source-image.png")
                .mimeType("image/png")
                .resourceKey("session-1:source-image.png:hash")
                .originFileName("原图.png")
                .build());
        GptQueryReq request = GptQueryReq.builder()
                .traceId("trace-session-1:req-1")
                .sessionId("session-1")
                .requestId("req-1")
                .query("基于上传图片改成赛博朋克风")
                .deepThink(0)
                .outputStyle("html")
                .user("reactor")
                .sessionFiles(sessionFiles)
                .build();

        AgentRequest agentRequest = ReflectionTestUtils.invokeMethod(service, "buildAgentRequest", request);

        Assert.assertNotNull(agentRequest);
        Assert.assertEquals("trace-session-1:req-1", agentRequest.getRequestId());
        Assert.assertEquals(AgentType.REACT.getValue(), agentRequest.getAgentType());
        Assert.assertEquals(sessionFiles, agentRequest.getSessionFiles());
        Assert.assertEquals("react-base-prompt", agentRequest.getBasePrompt());
    }

    private ReactorConfig buildReactorConfig() {
        ReactorConfig reactorConfig = new ReactorConfig();
        ReflectionTestUtils.setField(reactorConfig, "reactorBasePrompt", "react-base-prompt");
        ReflectionTestUtils.setField(reactorConfig, "reactorSopPrompt", "plan-sop-prompt");
        return reactorConfig;
    }
}
