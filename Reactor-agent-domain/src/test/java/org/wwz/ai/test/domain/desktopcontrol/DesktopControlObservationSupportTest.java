package org.wwz.ai.test.domain.desktopcontrol;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlObservationSupport;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlRecord;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlStatuses;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.RequestDesktopControlTool;

import java.util.List;
import java.util.Map;

public class DesktopControlObservationSupportTest {

    @Test
    public void waitingObservationOmitsStreamUrl() {
        String obs = DesktopControlObservationSupport.buildWaitingObservation("请登录", "dc_1");
        Assert.assertTrue(obs.contains("请登录"));
        Assert.assertTrue(obs.contains("dc_1"));
        Assert.assertTrue(obs.contains("waiting_user_input"));
        Assert.assertFalse(obs.contains("http"));
        Assert.assertFalse(obs.contains("streamUrl"));
        Assert.assertFalse(obs.contains("vnc.html"));
    }

    @Test
    public void completedObservationOmitsStreamUrl() {
        String obs = DesktopControlObservationSupport.buildCompletedObservation("请登录", "dc_1");
        Assert.assertTrue(obs.contains("用户已完成桌面操作"));
        Assert.assertTrue(obs.contains("请登录"));
        Assert.assertFalse(obs.contains("http"));
        Assert.assertFalse(obs.contains("streamUrl"));
        Assert.assertFalse(obs.contains("vnc.html"));
    }

    @Test
    public void clientPayloadKeepsStreamUrlForLiveCard() {
        Map<String, Object> payload = DesktopControlObservationSupport.toClientPayload(
                DesktopControlRecord.builder()
                        .controlId("dc_1")
                        .sessionId("s1")
                        .sourceRequestId("r1")
                        .reason("请登录")
                        .streamUrl("https://6080-box.e2b.app/vnc.html")
                        .holdUntil(700.0)
                        .status(DesktopControlStatuses.PENDING)
                        .build()
        );
        Assert.assertEquals("desktop_control", payload.get("messageType"));
        Assert.assertEquals("pending", payload.get("status"));
        Assert.assertEquals("dc_1", payload.get("controlId"));
        Assert.assertEquals("https://6080-box.e2b.app/vnc.html", payload.get("streamUrl"));
        Assert.assertEquals(700.0, payload.get("holdUntil"));
    }

    @Test
    public void replayPayloadDropsLiveUrl() {
        Map<String, Object> payload = DesktopControlObservationSupport.toReplayPayload(
                DesktopControlRecord.builder()
                        .controlId("dc_1")
                        .streamUrl("https://6080-box.e2b.app/vnc.html")
                        .status(DesktopControlStatuses.COMPLETED)
                        .build()
        );
        Assert.assertEquals("completed", payload.get("status"));
        Assert.assertFalse(payload.containsKey("streamUrl"));
    }

    @Test
    public void resolveDesktopToolCallIdPrefersPreferredThenUnpaired() {
        Assert.assertEquals("fc_pref", DesktopControlObservationSupport.resolveDesktopToolCallId(
                List.of(), "fc_pref"));

        Message assistant = Message.fromToolCalls("desktop", List.of(
                ToolCall.builder()
                        .id("fc_desk")
                        .type("function")
                        .function(ToolCall.Function.builder()
                                .name(RequestDesktopControlTool.NAME)
                                .arguments("{\"reason\":\"login\"}")
                                .build())
                        .build()
        ));
        Assert.assertEquals("fc_desk", DesktopControlObservationSupport.resolveDesktopToolCallId(
                List.of(assistant), null));
    }
}
