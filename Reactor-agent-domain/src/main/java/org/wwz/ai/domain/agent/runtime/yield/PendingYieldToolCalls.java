package org.wwz.ai.domain.agent.runtime.yield;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.dto.tool.ToolCall;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.util.StringUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 让出类工具（AskUserQuestion / RequestDesktopControl / ExitPlanMode）的 tool_call 配对查询。
 * <p>
 * 三条链路都要在 yield 前确认「哪个 tool_call 还没有 tool result」，判定逻辑完全一致，
 * 只有工具名不同。这里只收这一块，不接管各链路的收口流程。
 */
public final class PendingYieldToolCalls {

    private PendingYieldToolCalls() {
    }

    /**
     * 从消息历史里找尚未配对的 tool_call id。
     *
     * @param preferredToolCallId 异常携带的 toolCallId，非空时直接采用
     * @param toolName            目标工具名
     * @param assignMissingId     为 true 时给缺失 id 的 tool_call 补一个并回写到 message；
     *                            为 false 时跳过该条（AskUserQuestion / RequestDesktopControl 的行为）
     * @return 配对的 toolCallId，找不到返回 null
     */
    public static String resolve(List<Message> messages,
                                 String preferredToolCallId,
                                 String toolName,
                                 boolean assignMissingId) {
        if (StringUtils.isNotBlank(preferredToolCallId)) {
            return preferredToolCallId.trim();
        }
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        Set<String> answeredIds = new HashSet<>();
        for (Message message : messages) {
            if (message == null || message.getRole() != RoleType.TOOL) {
                continue;
            }
            if (StringUtils.isBlank(message.getToolCallId())) {
                continue;
            }
            if (StringUtils.defaultString(message.getContent()).contains("waiting_user_input")) {
                continue;
            }
            answeredIds.add(message.getToolCallId());
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message == null || message.getRole() != RoleType.ASSISTANT
                    || message.getToolCalls() == null || message.getToolCalls().isEmpty()) {
                continue;
            }
            for (ToolCall toolCall : message.getToolCalls()) {
                if (toolCall == null || toolCall.getFunction() == null) {
                    continue;
                }
                if (!toolName.equals(toolCall.getFunction().getName())) {
                    continue;
                }
                if (StringUtils.isNotBlank(toolCall.getId()) && answeredIds.contains(toolCall.getId())) {
                    continue;
                }
                if (StringUtils.isBlank(toolCall.getId())) {
                    if (!assignMissingId) {
                        continue;
                    }
                    // 兼容少数 OpenAI-compatible 网关丢失 tool_call_id 的响应；先修复
                    // assistant tool call，再用同一 ID 写入 waiting/decision observation。
                    toolCall.setId(StringUtil.getUUID());
                }
                return toolCall.getId();
            }
        }
        return null;
    }

    /** 该 toolCallId 是否已有 tool result。 */
    public static boolean hasToolResult(List<Message> messages, String toolCallId) {
        if (messages == null || StringUtils.isBlank(toolCallId)) {
            return false;
        }
        for (Message message : messages) {
            if (message != null
                    && message.getRole() == RoleType.TOOL
                    && toolCallId.equals(message.getToolCallId())) {
                return true;
            }
        }
        return false;
    }
}
