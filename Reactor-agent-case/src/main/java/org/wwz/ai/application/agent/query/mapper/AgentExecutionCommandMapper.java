package org.wwz.ai.application.agent.query.mapper;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.application.agent.query.GptQueryCommand;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.command.AgentExecutionCommand;
import org.wwz.ai.domain.agent.runtime.enums.AgentType;

import javax.annotation.Resource;

/**
 * Case command 到 Domain runtime command 的显式映射。
 */
@Component
public class AgentExecutionCommandMapper {

    @Resource
    private ReactorConfig reactorConfig;

    public AgentExecutionCommand toExecutionCommand(GptQueryCommand command, String userId) {
        if (command == null) {
            return null;
        }
        int deepThink = command.getDeepThink() == null ? 0 : command.getDeepThink();
        String user = command.getUser() == null ? "reactor" : command.getUser();
        String traceId = StringUtils.isBlank(command.getTraceId())
                ? buildTraceId(user, command.getSessionId(), command.getRequestId())
                : command.getTraceId();
        String requestId = StringUtils.isNotBlank(command.getRequestId())
                ? command.getRequestId().trim()
                : traceId;
        boolean planSolve = deepThink != 0;

        return AgentExecutionCommand.builder()
                .requestId(requestId)
                .sessionId(command.getSessionId())
                .userId(userId)
                .query(command.getQuery())
                .agentType(planSolve ? AgentType.PLAN_SOLVE.getValue() : AgentType.REACT.getValue())
                .outputStyle("dataAgent".equals(command.getOutputStyle()) ? "dataAgent" : null)
                .sessionFiles(command.getSessionFiles())
                .model(StringUtils.trimToNull(command.getModel()))
                .thinking(command.getThinking())
                .thinkingEffort(StringUtils.trimToNull(command.getThinkingEffort()))
                .forcePlanMode(Boolean.TRUE.equals(command.getForcePlanMode()))
                .erp(null)
                .sopPrompt("")
                .basePrompt(planSolve ? "" : reactorConfig.getReactorBasePrompt())
                .isStream(true)
                .build();
    }

    private String buildTraceId(String user, String sessionId, String requestId) {
        String normalized = StringUtils.isNotEmpty(user) ? user.toLowerCase() : user;
        if (normalized != null && normalized.chars().anyMatch(c -> c >= 0x4e00 && c <= 0x9fff)) {
            return sessionId + ":" + requestId;
        }
        return normalized + sessionId + ":" + requestId;
    }
}
