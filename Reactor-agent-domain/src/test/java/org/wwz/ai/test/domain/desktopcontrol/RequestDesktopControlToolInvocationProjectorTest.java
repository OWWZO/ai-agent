package org.wwz.ai.test.domain.desktopcontrol;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.RequestDesktopControlToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlRecord;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlStatuses;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.IDesktopControlRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RequestDesktopControlToolInvocationProjectorTest {

    @Test
    public void replayOmitsLiveStreamUrl() {
        ToolInvocationView invocation = ToolInvocationView.builder()
                .id(3L)
                .toolCallId("tool-call-desk-001")
                .toolName("RequestDesktopControl")
                .inputJson("{\"reason\":\"请登录\"}")
                .llmObservation("{\"message\":\"桌面控制已交给用户。reason=请登录 controlId=dc_1\",\"status\":\"waiting_user_input\",\"reason\":\"请登录\",\"controlId\":\"dc_1\"}")
                .build();

        List<ProjectedReplayEvent> events = new RequestDesktopControlToolInvocationProjector()
                .project(invocation, List.of(), new EventResult());

        Assert.assertEquals(1, events.size());
        Map<String, Object> response = castMap(events.get(0).getResultMap());
        Map<String, Object> payload = castMap(response.get("resultMap"));
        Assert.assertEquals("desktop_control", payload.get("messageType"));
        Assert.assertEquals("dc_1", payload.get("controlId"));
        Assert.assertEquals("请登录", payload.get("reason"));
        Assert.assertFalse(payload.containsKey("streamUrl"));
    }

    @Test
    public void usesPersistedCompletedStateWhenObservationIsWaiting() {
        ToolInvocationView invocation = ToolInvocationView.builder()
                .toolCallId("tool-call-desk-002")
                .sessionId("s1")
                .toolName("RequestDesktopControl")
                .inputJson("{\"reason\":\"请登录\"}")
                .llmObservation("{\"status\":\"waiting_user_input\",\"reason\":\"请登录\",\"controlId\":\"dc_2\"}")
                .build();
        IDesktopControlRepository repository = new IDesktopControlRepository() {
            @Override
            public void insert(DesktopControlRecord record) {
            }

            @Override
            public Optional<DesktopControlRecord> findByControlId(String controlId) {
                return Optional.of(DesktopControlRecord.builder()
                        .controlId("dc_2")
                        .reason("请登录")
                        .streamUrl("https://stale.example/vnc.html")
                        .status(DesktopControlStatuses.COMPLETED)
                        .build());
            }

            @Override
            public Optional<DesktopControlRecord> findByResumeRequestId(String resumeRequestId) {
                return Optional.empty();
            }

            @Override
            public List<DesktopControlRecord> listOpenBySessionId(String sessionId) {
                return List.of();
            }

            @Override
            public boolean hasOpenBySessionId(String sessionId) {
                return false;
            }

            @Override
            public boolean casCompletePending(String controlId, String userId, String resumeRequestId) {
                return false;
            }

            @Override
            public boolean casClaimResume(String resumeRequestId, String userId) {
                return false;
            }

            @Override
            public boolean markCompleted(String controlId) {
                return false;
            }

            @Override
            public boolean markStatus(String controlId, String status) {
                return false;
            }

            @Override
            public boolean casCancel(String controlId, String userId) {
                return false;
            }
        };

        List<ProjectedReplayEvent> events = new RequestDesktopControlToolInvocationProjector(repository)
                .project(invocation, List.of(), new EventResult());
        Map<String, Object> payload = castMap(castMap(events.get(0).getResultMap()).get("resultMap"));
        Assert.assertEquals("completed", payload.get("status"));
        Assert.assertEquals("COMPLETED", payload.get("persistenceStatus"));
        Assert.assertFalse(payload.containsKey("streamUrl"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
