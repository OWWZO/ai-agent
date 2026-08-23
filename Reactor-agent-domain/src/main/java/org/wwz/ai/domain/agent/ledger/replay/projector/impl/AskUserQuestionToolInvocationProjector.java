package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.reactor.model.multi.EventResult;
import org.wwz.ai.domain.agent.runtime.askuser.AskUserQuestionObservationSupport;
import org.wwz.ai.domain.agent.runtime.askuser.IUserQuestionRepository;
import org.wwz.ai.domain.agent.runtime.askuser.UserQuestionRecord;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.AskUserQuestionTool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AskUserQuestion 历史回放投影：保留题目参数和 observation 中的答案，供前端提问工具卡展示。
 */
public class AskUserQuestionToolInvocationProjector extends AbstractToolInvocationProjector {

    private final IUserQuestionRepository userQuestionRepository;

    public AskUserQuestionToolInvocationProjector() {
        this(null);
    }

    public AskUserQuestionToolInvocationProjector(IUserQuestionRepository userQuestionRepository) {
        this.userQuestionRepository = userQuestionRepository;
    }

    @Override
    public boolean supports(String toolName) {
        return StringUtils.equalsIgnoreCase(AskUserQuestionTool.NAME, toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              EventResult state) {
        Map<String, Object> input = readMap(invocation == null ? null : invocation.getInputJson());
        Map<String, Object> observation = readMap(invocation == null ? null : invocation.getLlmObservation());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageType", "ask_user_question");

        String questionId = stringValue(observation.get("questionId"));
        UserQuestionRecord persisted = findPersistedQuestion(questionId, invocation);
        String resolvedQuestionId = StringUtils.defaultIfBlank(
                questionId,
                persisted == null ? null : persisted.getQuestionId()
        );

        Object questions = persisted == null ? null : persisted.getQuestions();
        if (questions == null) {
            questions = observation.get("questions");
        }
        if (questions == null) {
            questions = input.get("questions");
        }
        if (questions != null) {
            payload.put("questions", questions);
        }
        payload.put("input", input);

        Object answers = persisted == null ? null : persisted.getAnswers();
        if (answers == null) {
            answers = observation.get("answers");
        }
        boolean answered = answers instanceof Map<?, ?>;
        if (answered) {
            payload.put("answers", answers);
        }
        String status = persisted == null
                ? (answered ? "answered" : "pending")
                : AskUserQuestionObservationSupport.toClientStatus(persisted.getStatus());
        payload.put("status", status);
        if (persisted != null && StringUtils.isNotBlank(persisted.getStatus())) {
            payload.put("persistenceStatus", persisted.getStatus());
        }
        if (StringUtils.isNotBlank(resolvedQuestionId)) {
            payload.put("questionId", resolvedQuestionId);
        }

        return List.of(buildTaskEvent(
                state,
                invocation,
                "ask_user_question",
                buildStructuredToolResponse(invocation, "ask_user_question", payload),
                buildArtifactRefs(artifacts)
        ));
    }

    private UserQuestionRecord findPersistedQuestion(String questionId, ToolInvocationView invocation) {
        if (userQuestionRepository == null) {
            return null;
        }
        try {
            if (StringUtils.isNotBlank(questionId)) {
                UserQuestionRecord byQuestionId = userQuestionRepository.findByQuestionId(questionId).orElse(null);
                if (byQuestionId != null) {
                    return byQuestionId;
                }
            }
            if (invocation == null || StringUtils.isBlank(invocation.getSessionId())) {
                return null;
            }
            for (UserQuestionRecord record : userQuestionRepository.listOpenBySessionId(invocation.getSessionId())) {
                if (record == null) {
                    continue;
                }
                if (invocation.getId() != null && invocation.getId().equals(record.getToolInvocationId())) {
                    return record;
                }
                if (StringUtils.isNotBlank(invocation.getToolCallId())
                        && invocation.getToolCallId().equals(record.getToolCallId())) {
                    return record;
                }
            }
            return null;
        } catch (RuntimeException ignored) {
            // 历史回放不能因问询附属状态读取失败而中断。
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
