package org.wwz.ai.domain.agent.runtime.agent;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.enums.AgentState;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * React / PlanSolve 共用终答解析：仅接受无 tool_calls 的 assistant 文本。
 */
public final class ReactFinalAnswerResolver {

    private static final Logger log = LoggerFactory.getLogger(ReactFinalAnswerResolver.class);

    private static final Pattern FINISH_BRACKET = Pattern.compile(
            "(?is)^\\s*Finish\\s*\\[\\s*(.*?)\\s*]\\s*$");
    private static final Pattern FINISH_INLINE = Pattern.compile(
            "(?is)Finish\\s*\\[\\s*(.*?)\\s*]");

    public static final String MISSING_USER_FACING_ANSWER =
            "任务已执行完成，但未能生成面向用户的最终说明。请补充问题后重试，或查看过程中的工具结果。";

    private ReactFinalAnswerResolver() {
    }

    public static String resolve(ReActAgent executor, String runResult) {
        String fromMemory = findLastUserFacingAssistantText(executor);
        if (StringUtils.isNotBlank(fromMemory)) {
            return sanitize(fromMemory);
        }

        if (executor != null
                && executor.getState() == AgentState.FINISHED
                && isPlausibleUserFacingRunResult(runResult)) {
            return sanitize(runResult);
        }

        log.warn("final answer missing user-facing assistant text, request may have stopped mid-tools");
        return MISSING_USER_FACING_ANSWER;
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        Matcher whole = FINISH_BRACKET.matcher(text);
        if (whole.matches()) {
            return whole.group(1).trim();
        }
        Matcher inline = FINISH_INLINE.matcher(text);
        if (inline.find() && text.length() < 500) {
            String inner = inline.group(1).trim();
            if (StringUtils.isNotBlank(inner)) {
                return inner;
            }
        }
        return text;
    }

    private static String findLastUserFacingAssistantText(ReActAgent executor) {
        if (executor == null || executor.getMemory() == null) {
            return null;
        }
        List<Message> messages = executor.getMemory().getMessages();
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message == null || message.getRole() != RoleType.ASSISTANT) {
                continue;
            }
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                continue;
            }
            if (StringUtils.isNotBlank(message.getContent())) {
                return message.getContent().trim();
            }
        }
        return null;
    }

    private static boolean isPlausibleUserFacingRunResult(String runResult) {
        if (StringUtils.isBlank(runResult)) {
            return false;
        }
        String text = runResult.trim();
        if (text.startsWith("Terminated:")) {
            return false;
        }
        if ("No steps executed".equals(text) || "Thinking complete - no action needed".equals(text)) {
            return false;
        }
        return !text.contains("工具执行结果为:") && !text.contains("Tool execution");
    }
}
