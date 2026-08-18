package org.wwz.ai.test.domain.askuser;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.askuser.AskUserQuestionObservationSupport;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionRecord;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionStatuses;

import java.util.List;
import java.util.Map;

public class AskUserQuestionObservationSupportTest {

    @Test
    public void buildAnswerObservationContainsAnswers() {
        String obs = AskUserQuestionObservationSupport.buildAnswerObservation(
                List.of(Map.of("question", "选哪个？", "header", "方向")),
                Map.of("选哪个？", "A"),
                "uq_1"
        );
        Assert.assertTrue(obs.contains("选哪个？"));
        Assert.assertTrue(obs.contains("uq_1"));
        Assert.assertTrue(obs.contains("A") || obs.contains("answers"));
    }

    @Test
    public void clientPayloadMapsPendingStatus() {
        Map<String, Object> payload = AskUserQuestionObservationSupport.toClientPayload(
                UserQuestionRecord.builder()
                        .questionId("uq_1")
                        .sessionId("s1")
                        .sourceRequestId("r1")
                        .status(UserQuestionStatuses.PENDING)
                        .questions(List.of())
                        .build()
        );
        Assert.assertEquals("ask_user_question", payload.get("messageType"));
        Assert.assertEquals("pending", payload.get("status"));
        Assert.assertEquals("uq_1", payload.get("questionId"));
    }
}
