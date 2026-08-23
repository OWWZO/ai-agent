package org.wwz.ai.test.domain.askuser;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.ledger.replay.projector.impl.AskUserQuestionToolInvocationProjector;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.runtime.askuser.IUserQuestionRepository;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AskUserQuestionToolInvocationProjectorTest {

    @Test
    public void projectsQuestionInputAndAnswerAsAskUserEvent() {
        ToolInvocationView invocation = ToolInvocationView.builder()
                .id(3L)
                .toolCallId("tool-call-ask-001")
                .toolName("AskUserQuestion")
                .inputJson("{\"questions\":[{\"question\":\"选框架？\",\"options\":[{\"label\":\"React\"}]}]}")
                .llmObservation("{\"questions\":[{\"question\":\"选框架？\",\"options\":[{\"label\":\"React\"}]}],\"answers\":{\"选框架？\":\"React\"},\"questionId\":\"uq_1\"}")
                .build();

        List<ProjectedReplayEvent> events = new AskUserQuestionToolInvocationProjector()
                .project(invocation, List.of(), new EventResult());

        Assert.assertEquals(1, events.size());
        Map<String, Object> response = castMap(events.get(0).getResultMap());
        Map<String, Object> payload = castMap(response.get("resultMap"));
        Assert.assertEquals("ask_user_question", payload.get("messageType"));
        Assert.assertEquals("answered", payload.get("status"));
        Assert.assertEquals("uq_1", payload.get("questionId"));
        Assert.assertTrue(payload.get("input") instanceof Map);
        Assert.assertEquals("React", castMap(payload.get("answers")).get("选框架？"));
    }

    @Test
    public void usesPersistedAnsweredStateWhenOriginalObservationIsWaiting() {
        ToolInvocationView invocation = ToolInvocationView.builder()
                .toolCallId("tool-call-ask-002")
                .toolName("AskUserQuestion")
                .inputJson("{\"questions\":[{\"question\":\"选框架？\"}]}")
                .llmObservation("{\"questions\":[{\"question\":\"选框架？\"}],\"status\":\"waiting_user_input\",\"questionId\":\"uq_2\"}")
                .build();
        IUserQuestionRepository repository = new IUserQuestionRepository() {
            @Override
            public Optional<UserQuestionRecord> findByQuestionId(String questionId) {
                return Optional.of(UserQuestionRecord.builder()
                        .questionId(questionId)
                        .status("ANSWERED")
                        .answers(Map.of("选框架？", "React"))
                        .questions(List.of(Map.of("question", "选框架？")))
                        .build());
            }

            @Override public void insert(UserQuestionRecord record) { }
            @Override public Optional<UserQuestionRecord> findByResumeRequestId(String resumeRequestId) { return Optional.empty(); }
            @Override public List<UserQuestionRecord> listOpenBySessionId(String sessionId) { return List.of(); }
            @Override public boolean hasOpenBySessionId(String sessionId) { return false; }
            @Override public boolean casAnswerPending(String questionId, String visitorId, Map<String, String> answers, String resumeRequestId) { return false; }
            @Override public boolean casClaimResume(String resumeRequestId, String visitorId) { return false; }
            @Override public boolean markAnswered(String questionId) { return false; }
            @Override public boolean markStatus(String questionId, String status) { return false; }
            @Override public boolean casCancel(String questionId, String visitorId) { return false; }
        };

        List<ProjectedReplayEvent> events = new AskUserQuestionToolInvocationProjector(repository)
                .project(invocation, List.of(), new EventResult());

        Map<String, Object> response = castMap(events.get(0).getResultMap());
        Map<String, Object> payload = castMap(response.get("resultMap"));
        Assert.assertEquals("answered", payload.get("status"));
        Assert.assertEquals("React", castMap(payload.get("answers")).get("选框架？"));
    }

    @Test
    public void findsPersistedStateByToolInvocationWhenWaitingObservationHasNoQuestionId() {
        ToolInvocationView invocation = ToolInvocationView.builder()
                .id(7L)
                .sessionId("s1")
                .toolCallId("tool-call-ask-003")
                .toolName("AskUserQuestion")
                .inputJson("{\"questions\":[{\"question\":\"选框架？\"}]}")
                .llmObservation("{\"status\":\"waiting_user_input\"}")
                .build();
        UserQuestionRecord persisted = UserQuestionRecord.builder()
                .questionId("uq_3")
                .toolInvocationId(7L)
                .status("ANSWERED")
                .answers(Map.of("选框架？", "React"))
                .questions(List.of(Map.of("question", "选框架？")))
                .build();
        IUserQuestionRepository repository = new IUserQuestionRepository() {
            @Override public Optional<UserQuestionRecord> findByQuestionId(String questionId) { return Optional.empty(); }
            @Override public void insert(UserQuestionRecord record) { }
            @Override public Optional<UserQuestionRecord> findByResumeRequestId(String resumeRequestId) { return Optional.empty(); }
            @Override public List<UserQuestionRecord> listOpenBySessionId(String sessionId) { return List.of(persisted); }
            @Override public boolean hasOpenBySessionId(String sessionId) { return false; }
            @Override public boolean casAnswerPending(String questionId, String visitorId, Map<String, String> answers, String resumeRequestId) { return false; }
            @Override public boolean casClaimResume(String resumeRequestId, String visitorId) { return false; }
            @Override public boolean markAnswered(String questionId) { return false; }
            @Override public boolean markStatus(String questionId, String status) { return false; }
            @Override public boolean casCancel(String questionId, String visitorId) { return false; }
        };

        List<ProjectedReplayEvent> events = new AskUserQuestionToolInvocationProjector(repository)
                .project(invocation, List.of(), new EventResult());

        Map<String, Object> response = castMap(events.get(0).getResultMap());
        Map<String, Object> payload = castMap(response.get("resultMap"));
        Assert.assertEquals("answered", payload.get("status"));
        Assert.assertEquals("uq_3", payload.get("questionId"));
        Assert.assertEquals("React", castMap(payload.get("answers")).get("选框架？"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
