package org.wwz.ai.domain.agent.memory.ltm;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.ReactorRuntimeDependencies;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.agent.BaseAgent;
import org.wwz.ai.domain.agent.runtime.dto.Message;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 成功 turn 结束后的长期记忆同步与后台整理。
 * <p>子 Agent（skipMemory=true）完全跳过 LTM 生命周期；其工作记忆仍由
 * {@code SubAgentRunner} 单独投影到 {@code sub:<agentId>}。
 */
public final class LtmTurnSyncSupport {

    private LtmTurnSyncSupport() {
    }

    public static void syncSuccessfulTurn(AgentContext agentContext, BaseAgent executor) {
        // 子 Agent 是临时执行上下文，不能向共享用户 LTM sync 或调度 review。
        if (agentContext == null
                || LtmMemoryGuard.isSkipMemory(agentContext)
                || LtmMemoryGuard.isSideEffectsDisabled(agentContext)) {
            return;
        }
        ReactorRuntimeDependencies deps = agentContext.getRuntimeDependencies();
        if (deps == null) {
            return;
        }
        String user = agentContext.getQuery();
        if (StringUtils.isBlank(user)) {
            return;
        }
        String assistant = "";
        List<Map<String, Object>> messages = List.of();
        if (executor != null && executor.getMemory() != null) {
            List<Message> all = executor.getMemory().getMessages();
            if (all != null && !all.isEmpty()) {
                Message last = all.get(all.size() - 1);
                if (last != null && last.getRole() == RoleType.ASSISTANT && last.getContent() != null) {
                    assistant = last.getContent();
                }
                messages = toMaps(all);
            }
        }

        LtmManager ltmManager = deps.getOptionalLtmManager();
        if (ltmManager != null) {
            ltmManager.syncAll(user, assistant, agentContext.getSessionId(), messages);
            ltmManager.queuePrefetchAll(user, agentContext.getSessionId());
        }

        BackgroundReviewService review = LtmServices.backgroundReview();
        if (review == null) {
            review = deps.getOptionalBackgroundReviewService();
        }
        if (review == null) {
            return;
        }
        LtmOwner owner = agentContext.getLtmOwner();
        if (owner == null) {
            owner = LtmOwnerResolver.resolve(null, null);
        }
        List<Message> snapshot = List.of();
        if (executor != null && executor.getMemory() != null && executor.getMemory().getMessages() != null) {
            snapshot = new ArrayList<>(executor.getMemory().getMessages());
        }
        String parentSystem = null;
        ToolCollection parentTools = null;
        try {
            if (executor != null) {
                parentSystem = executor.getSystemPrompt();
                parentTools = executor.getAvailableTools();
            }
        } catch (Exception ignored) {
            // ignore
        }
        if (parentTools == null) {
            parentTools = agentContext.getToolCollection();
        }
        review.maybeScheduleAfterSuccessTurn(
                agentContext.getSessionId(),
                agentContext.getRequestId(),
                owner,
                user,
                assistant,
                snapshot,
                parentSystem,
                parentTools);
    }

    private static List<Map<String, Object>> toMaps(List<Message> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("role", message.getRole() == null ? null : message.getRole().name());
            row.put("content", message.getContent());
            out.add(row);
        }
        return out;
    }
}
